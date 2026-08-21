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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SL651 中心站命令(RPC method) → 功能码映射。
 *
 * 每个方法除命令功能码外，还声明「可被视为应答的上行帧功能码」，
 * 用于把遥测终端回传的数据帧对应回某个待回复的 RPC 请求（M4 应答关联）。
 */
public enum Sl651RpcMethod {

    /** 中心站查询遥测站实时数据 (0x37) */
    QUERY_REALTIME_DATA("queryRealtimeData", 0x37, new int[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35}),
    /** 中心站查询遥测站状态和报警信息 (0x46) */
    QUERY_STATUS_ALARM("queryStatusAndAlarm", 0x46, new int[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35}),
    /** 中心站设置时钟 (0x4A) */
    ADJUST_CLOCK("adjustClock", 0x4A, new int[]{0x30}),
    /** 中心站修改密码 (0x49) */
    CHANGE_PASSWORD("changePassword", 0x49, new int[]{0x30}),
    /** 中心站读取遥测站基本配置表 (0x41) */
    READ_BASIC_CONFIG("readBasicConfig", 0x41, new int[]{0x30}),
    /** 中心站读取遥测站运行参数配置表 (0x43) */
    READ_RUNNING_CONFIG("readRunningConfig", 0x43, new int[]{0x30});

    private static final Map<String, Sl651RpcMethod> BY_METHOD = new HashMap<>();

    static {
        for (Sl651RpcMethod m : values()) {
            BY_METHOD.put(m.methodName, m);
        }
    }

    private final String methodName;
    private final int functionCode;
    private final Set<Integer> responseFunctionCodes;

    Sl651RpcMethod(String methodName, int functionCode, int[] responseFunctionCodes) {
        this.methodName = methodName;
        this.functionCode = functionCode;
        this.responseFunctionCodes = Arrays.stream(responseFunctionCodes).boxed().collect(Collectors.toSet());
    }

    public String getMethodName() {
        return methodName;
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public Set<Integer> getResponseFunctionCodes() {
        return responseFunctionCodes;
    }

    /**
     * 按 methodName 查找映射；不存在则返回空
     */
    public static Optional<Sl651RpcMethod> find(String methodName) {
        return Optional.ofNullable(BY_METHOD.get(methodName));
    }
}