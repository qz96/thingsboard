package org.thingsboard.server.transport.sl651;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遥测键构建（三种键模式 + 帧内冲突）测试。
 *
 * 夹具：
 *  - 现网真实帧（站 0051300053）：要素 引导符0xFF/属性0x08=0（原始键 FF08）、
 *    引导符0xFF/属性0x40=非法BCD(NaN，须跳过)；
 *  - 合成帧（站 1111111111）：同引导符 0x27 两个要素（标识符 2708=12、2710=1234），
 *    用于验证"多测点映射到同一业务码"时的后缀去重。
 */
class Sl651KvBuilderTest {

    private static final String REAL_FRAME_HEX =
            "7e7e100051300053123432002602037d261030000320f1f1005130005360f0f" +
            "02610300003ff970800ff9840a3c1ff5338827b08038c60";

    private static final String MULTI_POINT_FRAME_HEX =
            "7e7e101111111111123432001e020a05261030120000f1f1111111111160f0f" +
            "0261030120027081227101234038a14";

    private SL651Parser parser;

    @BeforeEach
    void setUp() {
        parser = new SL651Parser(false, true, false, true, "big");
    }

    @Test
    void dualMode_emitsRawAndMappedKeys() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        // FF97 映射成某业务码；FF98 数据非法(NaN) 不产生任何键
        List<TransportProtos.KeyValueProto> kv =
                Sl651KvBuilder.build(frame, guide -> guide == 0xFF ? "e10101" : null, null,
                        Sl651KvBuilder.KeyMode.DUAL);

        Map<String, Double> map = toMap(kv);
        assertEquals(2, kv.size(), "FF97 要素应产出 raw+mapped 两个键");
        assertEquals(0.0, map.get("FF08"), 1e-9);
        assertEquals(0.0, map.get("e10101"), 1e-9);
    }

    @Test
    void rawMode_emitsOnlyRawKeys() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        List<TransportProtos.KeyValueProto> kv =
                Sl651KvBuilder.build(frame, guide -> "e10101", null, Sl651KvBuilder.KeyMode.RAW);

        assertEquals(List.of("FF08"), keys(kv), "raw 模式不得出现业务键");
    }

    @Test
    void mappedMode_emitsOnlyBusinessKeys() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        List<TransportProtos.KeyValueProto> kv =
                Sl651KvBuilder.build(frame, guide -> "e10101", null, Sl651KvBuilder.KeyMode.MAPPED);

        assertEquals(List.of("e10101"), keys(kv), "mapped 模式不得出现原始键");
    }

    @Test
    void mappedMode_withoutProvider_fallsBackToRaw() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        List<TransportProtos.KeyValueProto> kv =
                Sl651KvBuilder.build(frame, null, null, Sl651KvBuilder.KeyMode.MAPPED);

        assertEquals(List.of("FF08"), keys(kv), "没有映射提供者时 mapped 模式退化为 raw，不能丢数据");
    }

    @Test
    void multiPointSameBusinessCode_secondGetsGuideSuffix() {
        SL651Frame frame = parse(MULTI_POINT_FRAME_HEX);
        assertEquals(2, frame.getDataElements().size(), "合成帧应解析出 2 个要素");

        List<TransportProtos.KeyValueProto> kv =
                Sl651KvBuilder.build(frame, guide -> guide == 0x27 ? "e01232" : null, null,
                        Sl651KvBuilder.KeyMode.DUAL);

        Map<String, Double> map = toMap(kv);
        // 原始键各自独立
        assertEquals(12.0, map.get("2708"), 1e-9);
        assertEquals(1234.0, map.get("2710"), 1e-9);
        // 业务键冲突：首个用纯码，第二个追加引导符，避免同帧互相覆盖
        assertEquals(12.0, map.get("e01232"), 1e-9);
        assertEquals(1234.0, map.get("e01232_27"), 1e-9);
        assertEquals(4, kv.size());
    }

    @Test
    void keyPrefix_appliedToBothKeyTypes() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        List<TransportProtos.KeyValueProto> kv =
                Sl651KvBuilder.build(frame, guide -> "e10101", "site1", Sl651KvBuilder.KeyMode.DUAL);

        assertEquals(List.of("site1_FF08", "site1_e10101"), keys(kv));
    }

    @Test
    void keyModeParsing_unknownFallsBackToDual() {
        assertEquals(Sl651KvBuilder.KeyMode.DUAL, Sl651KvBuilder.KeyMode.fromConfig(null));
        assertEquals(Sl651KvBuilder.KeyMode.DUAL, Sl651KvBuilder.KeyMode.fromConfig("  "));
        assertEquals(Sl651KvBuilder.KeyMode.DUAL, Sl651KvBuilder.KeyMode.fromConfig("whatever"));
        assertEquals(Sl651KvBuilder.KeyMode.RAW, Sl651KvBuilder.KeyMode.fromConfig("RAW"));
        assertEquals(Sl651KvBuilder.KeyMode.MAPPED, Sl651KvBuilder.KeyMode.fromConfig("mapped"));
    }

    private SL651Frame parse(String hex) {
        SL651Frame frame = parser.parse(hex);
        assertTrue(frame.isValid(), "夹具帧必须解析成功: " + frame.getMsg());
        return frame;
    }

    private static List<String> keys(List<TransportProtos.KeyValueProto> kv) {
        return kv.stream().map(TransportProtos.KeyValueProto::getKey).toList();
    }

    private static Map<String, Double> toMap(List<TransportProtos.KeyValueProto> kv) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (TransportProtos.KeyValueProto e : kv) {
            map.put(e.getKey(), e.getDoubleV());
        }
        return map;
    }
}
