/*
 * Copyright © 2016-2025 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.transport.sl651;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.device.profile.Sl651DeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.rpc.RpcStatus;
import org.thingsboard.server.common.transport.SessionMsgListener;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.auth.SessionInfoCreator;
import org.thingsboard.server.common.transport.auth.ValidateDeviceCredentialsResponse;
import org.thingsboard.server.common.transport.service.TransportActivityManager;
import org.thingsboard.server.gen.transport.TransportProtos;

import static org.thingsboard.server.common.transport.service.DefaultTransportService.SESSION_EVENT_MSG_OPEN;

import java.net.InetSocketAddress;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SL651 会话处理器，实现 M2 上行链路：
 *
 * 1. 首帧到达后以「遥测站地址」作为设备 Access Token 调用
 *    {@link TransportService#process(DeviceTransportType, TransportProtos.ValidateDeviceTokenRequestMsg, TransportServiceCallback)}
 *    完成凭据校验；
 * 2. 校验通过后经 {@link SessionInfoCreator} 构建 SessionInfoProto，上报 SESSION_EVENT_MSG_OPEN
 *    并调用 {@link TransportService#registerAsyncSession(SessionInfoProto, SessionMsgListener)} 注册会话；
 * 3. 将 SL651 帧数据要素转换为 {@link TransportProtos.PostTelemetryMsg}，
 *    通过 {@link TransportService#process(TransportProtos.SessionInfoProto, TransportProtos.PostTelemetryMsg, TransportServiceCallback)} 上行遥测。
 *
 * 说明：设备凭据需为 ACCESS_TOKEN，且 token 值等于遥测站地址（8 位十六进制站码）。
 * 本处理器同时实现 {@link SessionMsgListener}，下行 RPC/属性（M4）将在此基础上扩展。
 */
@Slf4j
public class Sl651SessionHandler extends SimpleChannelInboundHandler<SL651Frame> implements SessionMsgListener {

    private final Sl651TransportContext context;
    private final TransportService transportService;

    private final String address;

    private volatile TransportProtos.SessionInfoProto sessionInfo;
    private final AtomicBoolean authPending = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Deque<SL651Frame> pendingFrames = new ConcurrentLinkedDeque<>();

    private final Sl651CommandEncoder commandEncoder = new Sl651CommandEncoder();
    private Sl651CommandEncoder.Header lastHeader;
    private final Map<Integer, PendingRpc> pendingRpcByFunction = new ConcurrentHashMap<>();

    private volatile Sl651DeviceProfileTransportConfiguration profileConfig = new Sl651DeviceProfileTransportConfiguration();
    private InetSocketAddress inetAddress;

    public Sl651SessionHandler(Sl651TransportContext context, String address) {
        this.context = context;
        this.transportService = context.getTransportService();
        this.address = address;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        doDisconnect();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SL651Frame frame) {
        // 记录最近一次上行帧头，作为下行命令的回程寻址信息
        lastHeader = new Sl651CommandEncoder.Header(frame.getCenterStationAddress(),
                frame.getTelemetryStationAddress(), frame.getPassword());
        String station = frame.getTelemetryStationAddressString();
        if (sessionInfo == null) {
            // 首帧：校验凭据并注册会话；期间到达的帧暂存待接入后统一上行
            if (authPending.compareAndSet(false, true)) {
                pendingFrames.addLast(frame);
                validateAndConnect(station);
            } else {
                pendingFrames.addLast(frame);
            }
            return;
        }
        processFrame(frame);
        // M4：尝试把该帧对应到待回复的下行 RPC
        completeRpcIfMatched(frame);
    }

    /**
     * 以「token 前缀 + 遥测站地址」为 token 校验设备凭据。
     * token 前缀（默认 sl651）用于避免不同协议的站码在全局凭据命名空间撞码，与网关侧约定一致。
     */
    private void validateAndConnect(String station) {
        String token = context.getTokenPrefix() + station;
        transportService.process(DeviceTransportType.SL651,
                TransportProtos.ValidateDeviceTokenRequestMsg.newBuilder().setToken(token).build(),
                new TransportServiceCallback<>() {
                    @Override
                    public void onSuccess(ValidateDeviceCredentialsResponse msg) {
                        if (!msg.hasDeviceInfo()) {
                            log.info("[{}] 站{}凭据校验失败(无匹配设备), 关闭连接", address, station);
                            if (inetAddress != null) {
                                context.getRateLimitService().onAuthFailure(inetAddress);
                            }
                            authPending.set(false);
                            pendingFrames.clear();
                            close();
                            return;
                        }
                        openSession(msg);
                    }

                    @Override
                    public void onError(Throwable e) {
                        log.error("[{}] 站{}凭据校验异常", address, station, e);
                        authPending.set(false);
                        pendingFrames.clear();
                        close();
                    }
                });
    }

    private void openSession(ValidateDeviceCredentialsResponse msg) {
        UUID sessionId = UUID.randomUUID();
        TransportProtos.SessionInfoProto info = SessionInfoCreator.create(msg, context, sessionId);
        applyProfileConfig(msg);
        transportService.process(info, SESSION_EVENT_MSG_OPEN,
                new TransportServiceCallback<>() {
                    @Override
                    public void onSuccess(Void unused) {
                        transportService.registerAsyncSession(info, Sl651SessionHandler.this);
                        sessionInfo = info;
                        connected.set(true);
                        authPending.set(false);
                        if (inetAddress != null) {
                            context.getRateLimitService().onAuthSuccess(inetAddress);
                        }
                        log.info("[{}][{}] SL651 设备会话已建立", address, sessionId);
                        drainPendingFrames();
                    }

                    @Override
                    public void onError(Throwable e) {
                        log.error("[{}] 打开 SL651 会话失败", address, e);
                        authPending.set(false);
                        pendingFrames.clear();
                        close();
                    }
                });
    }

    private void drainPendingFrames() {
        SL651Frame frame;
        while ((frame = pendingFrames.poll()) != null) {
            processFrame(frame);
        }
    }

    /**
     * 从凭据校验结果中读取 SL651 Device Profile 传输配置；缺失时回退默认。
     */
    private void applyProfileConfig(ValidateDeviceCredentialsResponse msg) {
        if (msg.getDeviceProfile() != null && msg.getDeviceProfile().getProfileData() != null
                && msg.getDeviceProfile().getProfileData().getTransportConfiguration() instanceof Sl651DeviceProfileTransportConfiguration) {
            profileConfig = (Sl651DeviceProfileTransportConfiguration) msg.getDeviceProfile().getProfileData().getTransportConfiguration();
            log.info("[{}] 应用 SL651 设备配置: statusAsAttr={}, 键前缀={}, 上报类型={}",
                    address, profileConfig.isStatusReportAsAttribute(), profileConfig.getTelemetryKeyPrefix(), profileConfig.getReportTypes());
        } else {
            profileConfig = new Sl651DeviceProfileTransportConfiguration();
        }
    }

    /**
     * SL651 上报分流：状态/事件报 → 设备属性；遥测报 → 遥测（M3）。
     * 每帧同时把原始二进制帧以 hex 形式留存为设备 client 属性 rawFrame，便于排查/重放。
     */
    private void processFrame(SL651Frame frame) {
        storeRawFrame(frame);
        java.util.List<TransportProtos.KeyValueProto> kvList = buildKvList(frame);
        if (kvList.isEmpty()) {
            return;
        }
        if (profileConfig.isStatusReportAsAttribute() && isStatusReport(frame)) {
            TransportProtos.PostAttributeMsg attrMsg = TransportProtos.PostAttributeMsg.newBuilder()
                    .addAllKv(kvList).build();
            transportService.process(sessionInfo, attrMsg, TransportServiceCallback.EMPTY);
        } else {
            TransportProtos.TsKvListProto tsKvList = TransportProtos.TsKvListProto.newBuilder()
                    .setTs(System.currentTimeMillis())
                    .addAllKv(kvList)
                    .build();
            TransportProtos.PostTelemetryMsg telemetryMsg = TransportProtos.PostTelemetryMsg.newBuilder()
                    .addTsKvList(tsKvList).build();
            transportService.process(sessionInfo, telemetryMsg, TransportServiceCallback.EMPTY);
        }
    }

    /**
     * 将整帧原始字节以 hex 形式作为 client 属性留存（覆盖写，仅保留最新一帧）。
     */
    private void storeRawFrame(SL651Frame frame) {
        byte[] raw = frame.getRawFrame();
        if (raw == null || raw.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(String.format("%02X", b));
        }
        TransportProtos.PostAttributeMsg rawAttr = TransportProtos.PostAttributeMsg.newBuilder()
                .addKv(TransportProtos.KeyValueProto.newBuilder()
                        .setKey("rawFrame")
                        .setType(TransportProtos.KeyValueType.STRING_V)
                        .setStringV(sb.toString())
                        .build())
                .build();
        transportService.process(sessionInfo, rawAttr, TransportServiceCallback.EMPTY);
    }

    private java.util.List<TransportProtos.KeyValueProto> buildKvList(SL651Frame frame) {
        java.util.List<TransportProtos.KeyValueProto> kvList = new java.util.ArrayList<>();
        for (DataElement element : frame.getDataElements()) {
            double value = element.getValue();
            if (Double.isNaN(value)) {
                continue; // 非法 BCD 数据，跳过
            }
            // 落库键使用稳定、无语言的要素编码（完整 2 字节数据标识符），由规则链 JS 再翻译为 HJ212 污染因子编码
            String key = applyKeyPrefix(String.format("%04X", element.getFullIdentifier()));
            kvList.add(TransportProtos.KeyValueProto.newBuilder()
                    .setKey(key)
                    .setType(TransportProtos.KeyValueType.DOUBLE_V)
                    .setDoubleV(value)
                    .build());
        }
        return kvList;
    }

    private String applyKeyPrefix(String key) {
        String prefix = profileConfig.getTelemetryKeyPrefix();
        if (prefix != null && !prefix.isBlank() && !"sl651".equals(prefix)) {
            return prefix + "_" + key;
        }
        return key;
    }

    private boolean isStatusReport(SL651Frame frame) {
        // 仅按报文类型（功能码）判定是否为状态/事件报，避免"帧内含一个 0x45 状态要素"就把整帧误判为状态报、
        // 导致正常遥测数据被整体路由到属性。SL651 中人工置数报(0x35)属于典型的非遥测上报。
        return frame.getFunctionCode() == 0x35;
    }

    private void doDisconnect() {
        if (sessionInfo == null) {
            return;
        }
        connected.set(false);
        // 会话关闭：所有未完成的 RPC 以错误回执结束
        pendingRpcByFunction.values().forEach(p -> sendErrorRpc(p.requestId, "SL651 会话已关闭"));
        pendingRpcByFunction.clear();
        try {
            transportService.process(sessionInfo, TransportActivityManager.SESSION_EVENT_MSG_CLOSED, TransportServiceCallback.EMPTY);
        } catch (Throwable e) {
            log.warn("[{}] 关闭会话事件发送异常", address, e);
        }
        try {
            transportService.deregisterSession(sessionInfo);
        } catch (Throwable e) {
            log.warn("[{}] 注销会话异常", address, e);
        }
        log.info("[{}] SL651 会话已关闭", address);
    }

    // ============ M4 下行 RPC ============

    @Override
    public void onToDeviceRpcRequest(UUID sessionId, TransportProtos.ToDeviceRpcRequestMsg rpcRequest) {
        String methodName = rpcRequest.getMethodName();
        Sl651RpcMethod rpcMethod = Sl651RpcMethod.find(methodName).orElse(null);
        if (rpcMethod == null) {
            log.info("[{}] 不支持的 SL651 RPC 方法: {}", address, methodName);
            sendErrorRpc(rpcRequest.getRequestId(), "Unsupported SL651 RPC method: " + methodName);
            return;
        }
        if (lastHeader == null || sessionInfo == null) {
            log.info("[{}] 尚无有效上行帧头，无法下发 RPC 命令: {}", address, methodName);
            sendErrorRpc(rpcRequest.getRequestId(), "No uplink frame received yet");
            return;
        }

        int functionCode = rpcMethod.getFunctionCode();
        byte[] params = rpcRequest.getParams() != null ? rpcRequest.getParams().getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        byte[] command = commandEncoder.build(lastHeader, functionCode, params);
        ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(command));
        log.info("[{}] 下发 SL651 命令: method={}, 功能码=0x{:02X}", address, methodName, functionCode);

        // 记录投递状态给核心
        transportService.process(sessionInfo, rpcRequest, RpcStatus.DELIVERED, true, TransportServiceCallback.EMPTY);
        // 登记待回复 RPC（便于把终端应答对应回来）
        pendingRpcByFunction.put(functionCode, new PendingRpc(rpcRequest.getRequestId(), rpcMethod));
    }

    /**
     * 把上行帧匹配到待回复的 RPC；若该帧功能码属于某命令的应答集，则回执成功。
     */
    private void completeRpcIfMatched(SL651Frame frame) {
        if (pendingRpcByFunction.isEmpty()) {
            return;
        }
        int fc = frame.getFunctionCode();
        pendingRpcByFunction.entrySet().removeIf(entry -> entry.getValue().method.getResponseFunctionCodes().contains(fc)
                && doReplyRpc(entry.getKey(), frame));
    }

    private boolean doReplyRpc(int commandFunctionCode, SL651Frame frame) {
        PendingRpc pending = pendingRpcByFunction.get(commandFunctionCode);
        if (pending == null) {
            return false;
        }
        pendingRpcByFunction.remove(commandFunctionCode);
        String payload = buildRpcPayload(frame);
        TransportProtos.ToDeviceRpcResponseMsg response = TransportProtos.ToDeviceRpcResponseMsg.newBuilder()
                .setRequestId(pending.requestId)
                .setPayload(payload)
                .build();
        transportService.process(sessionInfo, response, TransportServiceCallback.EMPTY);
        log.info("[{}] RPC {} 收到终端应答并已回执, 功能码=0x{:02X}", address, pending.requestId, frame.getFunctionCode());
        return true;
    }

    private String buildRpcPayload(SL651Frame frame) {
        ObjectNode root = context.getMapper().createObjectNode();
        root.put("station", frame.getTelemetryStationAddressString());
        root.put("functionCode", frame.getFunctionCode());
        ObjectNode values = root.putObject("values");
        for (DataElement element : frame.getDataElements()) {
            double v = element.getValue();
            if (!Double.isNaN(v)) {
                values.put(element.getElementName(), v);
            }
        }
        try {
            return context.getMapper().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"functionCode\":" + frame.getFunctionCode() + "}";
        }
    }

    private void sendErrorRpc(int requestId, String errorMsg) {
        if (sessionInfo == null) {
            return;
        }
        TransportProtos.ToDeviceRpcResponseMsg response = TransportProtos.ToDeviceRpcResponseMsg.newBuilder()
                .setRequestId(requestId)
                .setError(errorMsg)
                .build();
        transportService.process(sessionInfo, response, TransportServiceCallback.EMPTY);
    }

    private static class PendingRpc {
        final int requestId;
        final Sl651RpcMethod method;

        PendingRpc(int requestId, Sl651RpcMethod method) {
            this.requestId = requestId;
            this.method = method;
        }
    }

    private void close() {
        // 主动关闭当前通道
        if (ctx != null && ctx.channel().isActive()) {
            ctx.close();
        }
    }

    private ChannelHandlerContext ctx;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress) {
            inetAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            if (!context.getRateLimitService().checkAddress(inetAddress)) {
                log.info("[{}] IP 连接限流触发, 关闭连接", address);
                ctx.close();
                return;
            }
        }
        log.info("[{}] SL651 终端连接建立", ctx.channel().remoteAddress());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent && IdleState.READER_IDLE == ((IdleStateEvent) evt).state()) {
            log.info("[{}] 读空闲超时, 关闭连接", address);
            doDisconnect();
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    // ============ SessionMsgListener（下行，M4 实现 RPC） ============

    public TransportProtos.SessionInfoProto getSessionInfo() {
        return sessionInfo;
    }

    @Override
    public void onGetAttributesResponse(TransportProtos.GetAttributeResponseMsg getAttributesResponse) {
        log.trace("[{}] SL651 属性读取响应暂未实现", address);
    }

    @Override
    public void onAttributeUpdate(UUID sessionId, TransportProtos.AttributeUpdateNotificationMsg attributeUpdateNotification) {
        log.trace("[{}] SL651 属性下发暂未实现", address);
    }

    @Override
    public void onRemoteSessionCloseCommand(UUID sessionId, TransportProtos.SessionCloseNotificationProto sessionCloseNotification) {
        log.trace("[{}] 收到远端会话关闭命令", address);
        close();
    }

    @Override
    public void onToServerRpcResponse(TransportProtos.ToServerRpcResponseMsg toServerResponse) {
        log.trace("[{}] 服务端 RPC 响应暂未实现", address);
    }

    @Override
    public void onDeviceDeleted(org.thingsboard.server.common.data.id.DeviceId deviceId) {
        log.info("[{}] 设备被删除: {}", address, deviceId);
        close();
    }
}