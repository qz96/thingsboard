package org.thingsboard.server.transport.sl651;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SL651 要素 → 遥测键值对 构建器。
 *
 * 设计目标：把「要素怎么变成遥测键」这件纯逻辑从 SessionHandler 里抽出来，做到：
 *  1. 可在不依赖 TransportService/Netty 的前提下单元测试；
 *  2. 支持三种键模式，一份代码覆盖不同架构取舍（见 {@link KeyMode}）；
 *  3. 帧内键冲突有确定性处理，避免同帧多要素映射到同一业务码时互相覆盖。
 *
 * 键模式说明：
 *  - {@code RAW}    只发原始标识符键（引导符<<8|属性字节，如 FF08）——最贴近 TB 原生：传输层只管协议，
 *                   业务词汇完全交给规则链按租户/目标自由翻译；
 *  - {@code MAPPED} 只发表映射业务键（如 w21003）——最省事：规则链无需翻译节点；
 *  - {@code DUAL}   两者都发（默认）——业务键即插即用，原始键保留可回溯/可重放，
 *                   多租户场景下规则链仍可只消费原始键自行翻译。
 */
@Slf4j
public final class Sl651KvBuilder {

    private Sl651KvBuilder() {
    }

    public enum KeyMode {
        RAW, MAPPED, DUAL;

        /**
         * 解析配置值；未知/空值回落 DUAL（保证配置写错时不至于丢键）。
         */
        public static KeyMode fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return DUAL;
            }
            switch (value.trim().toLowerCase()) {
                case "raw":
                    return RAW;
                case "mapped":
                    return MAPPED;
                case "dual":
                    return DUAL;
                default:
                    log.warn("未知的 SL651 键模式配置值 [{}]，按 dual 处理", value);
                    return DUAL;
            }
        }
    }

    /**
     * 按模式构建遥测键值对。
     *
     * @param frame     已解析的 SL651 帧
     * @param mapping   要素映射（可为 null：无映射时 MAPPED 模式退化为 RAW）
     * @param keyPrefix 设备 profile 的遥测键前缀（为空或 "sl651" 时不加前缀）
     * @param mode      键模式
     */
    public static List<TransportProtos.KeyValueProto> build(SL651Frame frame,
                                                            Sl651ElementMappingProvider mapping,
                                                            String keyPrefix,
                                                            KeyMode mode) {
        List<TransportProtos.KeyValueProto> out = new ArrayList<>();
        if (frame == null || frame.getDataElements() == null) {
            return out;
        }
        KeyMode effective = (mode == KeyMode.MAPPED && mapping == null) ? KeyMode.RAW : mode;
        Set<String> usedKeys = new HashSet<>();

        for (DataElement element : frame.getDataElements()) {
            double value = element.getValue();
            if (Double.isNaN(value)) {
                continue; // 非法 BCD 数据，跳过（不产生任何键）
            }

            if (effective != KeyMode.MAPPED) {
                String rawKey = applyPrefix(String.format("%04X", element.getFullIdentifier()), keyPrefix);
                out.add(kv(uniqueKey(rawKey, usedKeys), value));
            }

            if (effective != KeyMode.RAW) {
                String code = mapping.getPollutantCode(element.getGuideByte());
                if (code != null && !code.isBlank()) {
                    String bizKey = applyPrefix(code, keyPrefix);
                    out.add(kv(uniqueBusinessKey(bizKey, element.getGuideByte(), usedKeys), value));
                }
            }
        }
        return out;
    }

    private static TransportProtos.KeyValueProto kv(String key, double value) {
        return TransportProtos.KeyValueProto.newBuilder()
                .setKey(key)
                .setType(TransportProtos.KeyValueType.DOUBLE_V)
                .setDoubleV(value)
                .build();
    }

    private static String applyPrefix(String key, String prefix) {
        if (prefix != null && !prefix.isBlank() && !"sl651".equals(prefix)) {
            return prefix + "_" + key;
        }
        return key;
    }

    /**
     * 原始标识符键去重：同帧出现完全相同的标识符时追加序号（正常报文不应发生）。
     */
    private static String uniqueKey(String base, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int i = 2;
        String candidate = base + "_" + i;
        while (!used.add(candidate)) {
            candidate = base + "_" + (++i);
        }
        return candidate;
    }

    /**
     * 业务键去重：映射表是引导符粒度，同帧多个测点（不同引导符/属性）可能映射到同一业务码
     * （例如 26 个水位引导符都映射 e01232）。此时追加引导符后缀以保证不互相覆盖：
     * 首次用纯业务码，冲突用 code_XX（XX=引导符），极端重复再加序号。
     */
    private static String uniqueBusinessKey(String base, int guideByte, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        String suffix = String.format("%02X", guideByte & 0xFF);
        String candidate = base + "_" + suffix;
        if (used.add(candidate)) {
            return candidate;
        }
        int i = 2;
        String withIndex = candidate + "_" + i;
        while (!used.add(withIndex)) {
            withIndex = candidate + "_" + (++i);
        }
        return withIndex;
    }
}
