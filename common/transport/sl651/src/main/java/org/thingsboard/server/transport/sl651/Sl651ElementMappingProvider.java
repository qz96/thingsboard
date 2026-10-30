package org.thingsboard.server.transport.sl651;

/**
 * SL651 要素 → HJ212 污染因子编码 的映射提供者。
 *
 * 背景：传输层默认把遥测键落成「完整 2 字节数据标识符」的十六进制形式（如 FF08），
 * 语义对业务侧不友好。网关侧的实践是用数据库表
 * {@code sl_element_mapping(guide_byte, pollutant_code, enabled, ...)}
 * 做表驱动翻译（guide_byte → 如 w21003/e01203）。
 *
 * 本接口把"翻译能力"从传输层抽象出来：
 *  - 传输层只依赖本接口（可选注入，缺省 null = 原样使用标识符键）；
 *  - 单体内嵌模式下由 application 模块提供基于 JDBC 的实现（直查网关同一张表，
 *    与网关的要素映射管理页共享数据，网关下线后表与数据仍然保留）；
 *  - 微服务模式下可实现为 REST/缓存版本。
 *
 * 键粒度说明：当前表只到引导符（1 字节）粒度，与网关行为保持一致；
 * 同引导符多测点/扩展要素（FFXX）的区分是后续独立优化项。
 */
public interface Sl651ElementMappingProvider {

    /**
     * 查询引导符对应的 HJ212 污染因子编码。
     *
     * @param guideByte SL651 引导符（0x00~0xFF）
     * @return 如 "w21003"；未映射返回 null（调用方应回退为原始标识符键）
     */
    String getPollutantCode(int guideByte);
}
