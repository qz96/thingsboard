package org.thingsboard.server.transport.sl651;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确认帧编码与「是否应答」判定测试。
 *
 * 基准数据：现网真实上行帧（站 0051300053，0x32 定时报）对应的确认帧，
 * 该字节序列已在网关侧实测产出（站址在前、中心站在后、正文=流水号+发报时间、EOT、CRC 高位在前），
 * TB 原生实现必须与之逐字节一致，否则真机不认。
 */
class Sl651AckEncoderTest {

    private static final String REAL_FRAME_HEX =
            "7e7e100051300053123432002602037d261030000320f1f1005130005360f0f" +
            "02610300003ff970800ff9840a3c1ff5338827b08038c60";

    /** 应答时刻 2026-10-30 10:02:47 +08:00 时，网关实测的确认帧 */
    private static final String EXPECTED_ACK_HEX =
            "7E7E005130005310123432800802037D261030100247044DAA";

    private SL651Parser parser;

    @BeforeEach
    void setUp() {
        parser = new SL651Parser(false, true, false, true, "big");
    }

    @Test
    void ackFrame_matchesGatewayLayoutByteByByte() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        ZonedDateTime respondAt = ZonedDateTime.of(2026, 10, 30, 10, 2, 47, 0, ZoneOffset.ofHours(8));

        byte[] ack = Sl651AckEncoder.buildAck(frame, respondAt, ControlChar.EOT);

        assertEquals(EXPECTED_ACK_HEX, hex(ack), "确认帧必须与网关实测字节完全一致");
        assertEquals(25, ack.length);
    }

    @Test
    void ackFrame_stationComesBeforeCenterStation() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        byte[] ack = Sl651AckEncoder.buildAck(frame,
                ZonedDateTime.of(2026, 10, 30, 0, 3, 0, 0, ZoneOffset.ofHours(8)), ControlChar.EOT);

        // 表21：下行帧为 站址(5) → 中心站(1)，与上行帧相反
        assertEquals("0051300053", hex(ack, 2, 7), "遥测站地址应在中心站地址之前");
        assertEquals(0x10, ack[7] & 0xFF, "中心站地址");
        assertEquals(0x1234, ((ack[8] & 0xFF) << 8) | (ack[9] & 0xFF), "密码");
        assertEquals(0x32, ack[10] & 0xFF, "功能码回填");
        assertEquals(0x8008, ((ack[11] & 0xFF) << 8) | (ack[12] & 0xFF), "下行标识+正文长度 8");
        assertEquals(0x02, ack[13] & 0xFF, "STX");
        assertEquals(0x037D, ((ack[14] & 0xFF) << 8) | (ack[15] & 0xFF), "流水号回填");
        assertEquals(0x04, ack[22] & 0xFF, "结束符 EOT");
    }

    @Test
    void ackFrame_crcIsBigEndianAndOverAllPreviousBytes() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        byte[] ack = Sl651AckEncoder.buildAck(frame,
                ZonedDateTime.of(2026, 10, 30, 0, 3, 0, 0, ZoneOffset.ofHours(8)), ControlChar.EOT);

        int expected = CRC16.calculate(ack, 0, ack.length - 2);
        int actual = ((ack[23] & 0xFF) << 8) | (ack[24] & 0xFF);
        assertEquals(expected, actual, "CRC 必须覆盖结束符在内的全部前置字节，且高位在前");
    }

    @Test
    void ackFrame_escTerminator_keepsTerminalOnline() {
        SL651Frame frame = parse(REAL_FRAME_HEX);
        byte[] ack = Sl651AckEncoder.buildAck(frame,
                ZonedDateTime.of(2026, 10, 30, 0, 3, 0, 0, ZoneOffset.ofHours(8)), ControlChar.ESC);

        assertEquals(0x1B, ack[22] & 0xFF, "ESC(0x1B) 表示要求终端保持在线");
    }

    @Test
    void shouldAck_rules() {
        Sl651TransportContext context = new Sl651TransportContext();
        ReflectionTestUtils.setField(context, "ackEnabled", true);
        ReflectionTestUtils.setField(context, "ackExcludeFunctionCodes", "2F");
        ReflectionTestUtils.setField(context, "ackTerminator", "04");

        SL651Frame valid = parse(REAL_FRAME_HEX);
        assertTrue(context.shouldAck(valid), "有效帧且功能码未排除 → 应答");

        byte[] badCrc = hexToBytes(REAL_FRAME_HEX);
        badCrc[badCrc.length - 2] = 0x00;
        badCrc[badCrc.length - 1] = 0x00;
        assertFalse(context.shouldAck(parser.parse(badCrc)), "CRC 不符 → 不应答（规约 6.3.2.1）");

        // 把本帧功能码(0x32)配进排除列表 → 不应答
        ReflectionTestUtils.setField(context, "ackExcludeFunctionCodes", "2F,32");
        ReflectionTestUtils.setField(context, "parsedExcludedCodes", null);
        assertFalse(context.shouldAck(valid), "排除列表内的功能码 → 不应答");

        // 关闭总开关 → 不应答
        ReflectionTestUtils.setField(context, "ackExcludeFunctionCodes", "2F");
        ReflectionTestUtils.setField(context, "parsedExcludedCodes", null);
        ReflectionTestUtils.setField(context, "ackEnabled", false);
        assertFalse(context.shouldAck(valid), "ack 关闭 → 不应答");
    }

    private SL651Frame parse(String hex) {
        SL651Frame frame = parser.parse(hex);
        assertTrue(frame.isValid(), "夹具帧必须解析成功: " + frame.getMsg());
        return frame;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String hex(byte[] data) {
        return hex(data, 0, data.length);
    }

    private static String hex(byte[] data, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append(String.format("%02X", data[i]));
        }
        return sb.toString();
    }
}
