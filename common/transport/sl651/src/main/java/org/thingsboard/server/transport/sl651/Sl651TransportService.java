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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SL651 TCP 传输服务。
 *
 * M1 阶段负责：启动 Netty TCP 服务端监听 SL651 终端连接，经 {@link Sl651FrameDecoder}
 * 解包后由 {@link Sl651SessionHandler} 记录帧内容。后续 M2/M4 将在此接入
 * TB 认证、session 与上下行数据流。
 */
@Slf4j
@Component
public class Sl651TransportService {

    @Value("${transport.sl651.enabled:true}")
    private boolean enabled;

    @Value("${transport.sl651.bind_address:0.0.0.0}")
    private String bindAddress;

    @Value("${transport.sl651.bind_port:8501}")
    private int bindPort;

    @Value("${transport.sl651.netty.boss_group_thread_count:1}")
    private int bossGroupThreadCount;

    @Value("${transport.sl651.netty.worker_group_thread_count:4}")
    private int workerGroupThreadCount;

    @Value("${transport.sl651.timeout:600000}")
    private long inactivityTimeoutMs;

    @Autowired
    private Sl651TransportContext context;

    private SL651Parser parser;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void init() throws InterruptedException {
        if (!enabled) {
            log.info("SL651 传输服务已禁用 (transport.sl651.enabled=false)，跳过启动");
            return;
        }
        parser = context.createParser();
        log.info("SL651 传输服务初始化, 监听 {}:{}", bindAddress, bindPort);

        bossGroup = new NioEventLoopGroup(bossGroupThreadCount);
        workerGroup = new NioEventLoopGroup(workerGroupThreadCount);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new Sl651TransportServerInitializer(context, parser, inactivityTimeoutMs))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);
        serverChannel = bootstrap.bind(bindAddress, bindPort).sync().channel();
        log.info("SL651 传输服务已启动, 监听地址: {}:{}", bindAddress, bindPort);
    }

    @PreDestroy
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("SL651 传输服务已停止");
    }
}