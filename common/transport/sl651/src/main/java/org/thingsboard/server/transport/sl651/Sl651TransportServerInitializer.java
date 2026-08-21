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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * SL651 TCP 服务端 Pipeline 装配。
 */
public class Sl651TransportServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Sl651TransportContext context;
    private final SL651Parser parser;
    private final long inactivityTimeoutMs;

    public Sl651TransportServerInitializer(Sl651TransportContext context, SL651Parser parser, long inactivityTimeoutMs) {
        this.context = context;
        this.parser = parser;
        this.inactivityTimeoutMs = inactivityTimeoutMs;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("decoder", new Sl651FrameDecoder(parser));
        pipeline.addLast("idleStateHandler", new IdleStateHandler(inactivityTimeoutMs, 0, 0, TimeUnit.MILLISECONDS));
        pipeline.addLast("handler", new Sl651SessionHandler(context, ch.remoteAddress().toString()));
    }
}