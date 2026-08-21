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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.transport.TransportContext;

/**
 * SL651 传输上下文。
 *
 * 继承 {@link TransportContext} 获取 TB 传输层基础设施（TransportService、执行器、缓存、限流等）。
 * 后续 M2 将基于 {@link #getTransportService()} 完成设备凭据校验、session 注册与遥测/属性上行。
 */
@Component
public class Sl651TransportContext extends TransportContext {

    /** 设备凭据 token 前缀：token = tokenPrefix + 遥测站 BCD10 位站码，避免不同协议站码在全局命名空间撞码 */
    @Value("${transport.sl651.token_prefix:sl651}")
    private String tokenPrefix;

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    /**
     * 创建 SL651 解析器（默认配置，可由上层覆盖）
     */
    public SL651Parser createParser() {
        return new SL651Parser();
    }

}