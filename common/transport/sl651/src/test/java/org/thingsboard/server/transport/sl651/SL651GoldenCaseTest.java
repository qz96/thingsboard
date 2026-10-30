package org.thingsboard.server.transport.sl651;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SL651 解析与帧解码 golden-case 测试。
 *
 * 夹具来源：2026-10-30 现网真实上行帧（遥测站 0051300053，0x32 定时报）。
 * 该帧已在网关侧长期运行验证，作为 TB 原生 transport 的行为基线：
 *  - 任何改动导致本测试失败，必须先解释差异是否符合 SL651-2016，再决定是否更新期望值。
 *
 * 帧结构（55 字节）：
 *   7E7E | 10 | 0051300053 | 1234 | 32 | 0026 | 02 | 正文38B | 03 | 8C60
 *   正文 = 流水号037D + 发报时间261030000320 + 地址标识符F1F1 + 站址0051300053
 *        + 分类码60 + 观测时间标识符F0F0 + 观测时间2610300003
 *        + 要素1: FF 97 08 00（扩展0x97，1字节，0位小数，值0）
 *        + 要素2: FF 98 40 A3C1FF5338827B08（扩展0x98，8字节，非BCD → NaN）
 */
class SL651GoldenCaseTest {

    /** 现网真实帧：0x32 定时报，CRC=8C60（大端，高字节在前） */
    private static final String REAL_FRAME_HEX =
            "7e7e100051300053123432002602037d261030000320f1f1005130005360f0f" +
            "02610300003ff970800ff9840a3c1ff5338827b08038c60";

    /** 同帧但流水号改为 037E（粘包用例需要两帧不同序列，重算 CRC） */
    private static final String SECOND_FRAME_HEX =
            "7e7e100051300053123432002602037e261030000320f1f1005130005360f0f" +
            "02610300003ff970800ff9840a3c1ff5338827b080396d0";

    private SL651Parser parser;

    @BeforeEach
    void setUp() {
        // 与生产网关一致的解析口径：宽松结构校验、CRC 开启、大端（SL651-2016 表20：高字节在前）
        parser = new SL651Parser(false, true, false, true, "big");
    }

    @Test
    void realFrame_headFields() {
        SL651Frame frame = parser.parse(hexToBytes(REAL_FRAME_HEX));
        assertTrue(frame.isValid(), "现网真实帧必须解析成功: " + frame.getMsg());
        assertEquals("0051300053", frame.getTelemetryStationAddressString());
        assertEquals(0x32, frame.getFunctionCode());
        assertEquals(0x037D, frame.getSerialNumber());
        assertEquals(0x10, frame.getCenterStationAddress());
        assertEquals(0x1234, frame.getPassword());
        assertEquals("2610300003", frame.getObservationTimeString());
        assertEquals(2, frame.getDataElements().size());
    }

    @Test
    void realFrame_elementValues() {
        SL651Frame frame = parser.parse(hexToBytes(REAL_FRAME_HEX));
        assertTrue(frame.isValid());

        DataElement first = frame.getDataElements().get(0);
        assertEquals(0xFF, first.getGuideByte(), "扩展要素引导符");
        assertTrue(first.hasExtension());
        assertEquals(0x97, first.getExtensionByte());
        assertEquals(1, first.getDataLength());
        assertEquals(0, first.getDecimalPlaces());
        assertEquals(0.0, first.getValue(), 1e-9);

        DataElement second = frame.getDataElements().get(1);
        assertEquals(0x98, second.getExtensionByte());
        assertEquals(8, second.getDataLength());
        // 数据 A3C1FF5338827B08 含 >9 的半字节，非合法 BCD → 必须返回 NaN（无效值），
        // 上层（SessionHandler.buildKvList）据此跳过该要素
        assertTrue(Double.isNaN(second.getValue()),
                "非 BCD 数据必须返回 NaN，而不是错值");
    }

    @Test
    void badCrc_frameRejected() {
        byte[] bytes = hexToBytes(REAL_FRAME_HEX);
        bytes[bytes.length - 2] = 0x00;
        bytes[bytes.length - 1] = 0x00;
        SL651Frame frame = parser.parse(bytes);
        // 宽松模式（strict-mode=false）下解析器仍返回 valid=true（尽量解出要素），
        // 但必须把 crcValid 置 false——上层据此决定"坏帧不上报/不应答"（与网关侧同口径）。
        // 断言语义：crcValid=false 是坏帧判定的事实来源，SessionHandler/Decoder 不得依赖 isValid 区分好坏帧。
        assertTrue(frame.isValid(), "宽松模式下帧仍可解析");
        assertFalse(frame.isCrcValid(), "CRC 校验失败必须反映在 crcValid 上");
    }

    @Test
    void decoder_stickyPackets_emitsTwoFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new Sl651FrameDecoder(parser, 2048));
        try {
            // 两帧一次到达（TCP 粘包）→ 必须输出两个独立帧
            channel.writeInbound(Unpooled.wrappedBuffer(hexToBytes(REAL_FRAME_HEX)));
            channel.writeInbound(Unpooled.wrappedBuffer(hexToBytes(SECOND_FRAME_HEX)));

            SL651Frame first = channel.readInbound();
            SL651Frame second = channel.readInbound();
            assertNotNull(first, "粘包场景下第一帧必须被切出");
            assertNotNull(second, "粘包场景下第二帧必须被切出");
            assertTrue(first.isValid());
            assertTrue(second.isValid());
            assertEquals(0x037D, first.getSerialNumber());
            assertEquals(0x037E, second.getSerialNumber());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoder_splitPacket_assemblesWhenComplete() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new Sl651FrameDecoder(parser, 2048));
        try {
            byte[] frame = hexToBytes(REAL_FRAME_HEX);
            // 半包：前 20 字节先到，不应产出
            channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(frame, 0, 20)));
            assertNull(channel.readInbound(), "半包状态不应产出帧");

            // 剩余字节到达 → 组装成功
            channel.writeInbound(Unpooled.wrappedBuffer(
                    Arrays.copyOfRange(frame, 20, frame.length)));
            SL651Frame out = channel.readInbound();
            assertNotNull(out, "字节到齐后必须产出完整帧");
            assertTrue(out.isValid());
            assertEquals(0x037D, out.getSerialNumber());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoder_garbagePrefix_skippedAndResynced() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new Sl651FrameDecoder(parser, 2048));
        try {
            byte[] noise = {0x11, 0x22, 0x33};
            byte[] frame = hexToBytes(REAL_FRAME_HEX);
            byte[] input = new byte[noise.length + frame.length];
            System.arraycopy(noise, 0, input, 0, noise.length);
            System.arraycopy(frame, 0, input, noise.length, frame.length);

            channel.writeInbound(Unpooled.wrappedBuffer(input));
            SL651Frame out = channel.readInbound();
            assertNotNull(out, "乱码前缀必须被跳过并重新同步");
            assertTrue(out.isValid());
            assertEquals("0051300053", out.getTelemetryStationAddressString());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
