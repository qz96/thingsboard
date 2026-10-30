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

import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 要素映射提供者（可选注入）。
     * 单体内嵌模式由 application 模块提供 DB 实现（直查 sl_element_mapping 表）；
     * 未提供时遥测键保持原始标识符（如 FF97），由规则链自行翻译。
     */
    @Autowired(required = false)
    private Sl651ElementMappingProvider elementMappingProvider;

    /**
     * 遥测键模式：dual（默认，原始标识符键 + 表映射业务键）/ mapped（仅业务键）/ raw（仅原始键）。
     * 见 {@link Sl651KvBuilder.KeyMode}——同一份代码可切换三种架构取舍，切换零成本。
     */
    @Value("${transport.sl651.keys.mode:dual}")
    private String keysMode;

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public Sl651ElementMappingProvider getElementMappingProvider() {
        return elementMappingProvider;
    }

    public Sl651KvBuilder.KeyMode getKeysMode() {
        return Sl651KvBuilder.KeyMode.fromConfig(keysMode);
    }

    /**
     * 创建 SL651 解析器。
     *
     * 配置与生产网关、golden-case 测试保持一致（SL651GoldenCaseTest）：
     *  - 宽松结构校验（部分终端长度字段不规范）；
     *  - CRC 校验开启，大端序（SL651-2016 表20：高字节在前——解析器默认 little 是错的，必须显式 big）；
     *  - 不做长度字段强校验、允许非标准起始/结束符。
     */
    public SL651Parser createParser() {
        return new SL651Parser(false, true, false, true, "big");
    }

}