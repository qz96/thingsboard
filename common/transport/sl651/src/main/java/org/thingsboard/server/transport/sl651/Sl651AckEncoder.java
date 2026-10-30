package org.thingsboard.server.transport.sl651;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * SL651 确认帧（应答帧）编码器。
 *
 * 依据 SL651-2016：
 *  - 表21「HEX/BCD 编码 M2/M4 模式下行帧结构」：
 *      帧起始符(2) 7E7E | <b>遥测站地址(5)</b> | 中心站地址(1) | 密码(2) | 功能码(1)
 *      | 报文上下行标识及长度(2，高4位1000=下行) | STX(1) | 正文 | 结束符(1) | 校验码(2)
 *    注意：下行帧的站址/中心站顺序与上行帧相反（上行是中心站在前）。
 *  - 表25「下行报文正文基本结构」：流水号(2，取自上行帧) + 发报时间(6，BCD YYMMDDHHmmSS)
 *    → 正文 8 字节，故长度字段为 0x8008。
 *  - 结束符：EOT(0x04) 正常退出；ESC(0x1B) 要求终端保持在线。
 *  - 校验码：CRC-16/IBM，高位字节在前。
 *
 * 该布局与网关 sl-gateway 的 Sl651ResponseEncoder 完全一致（同一现网报文已逐字节对照）。
 */
public final class Sl651AckEncoder {

    /** 确认帧正文长度：流水号(2) + 发报时间(6) */
    public static final int ACK_BODY_LENGTH = 8;

    /** 默认结束符：EOT（传输结束，退出通信） */
    public static final byte DEFAULT_TERMINATOR = ControlChar.EOT;

    private static final byte[] START_MARKER = {0x7E, 0x7E};

    /** 下游时区：规约使用北京时间的 BCD 编码 */
    private static final ZoneOffset ZONE = ZoneOffset.ofHours(8);

    private Sl651AckEncoder() {
    }

    /**
     * 以当前时间构造确认帧（结束符 EOT）。
     */
    public static byte[] buildAck(SL651Frame frame) {
        return buildAck(frame, ZonedDateTime.now(ZONE), DEFAULT_TERMINATOR);
    }

    /**
     * 构造确认帧。
     *
     * @param frame     上行帧（提供遥测站地址、中心站地址、密码、功能码、流水号）
     * @param now       应答时刻（用于正文中的发报时间）
     * @param terminator 结束符（EOT=0x04 / ESC=0x1B 保持在线）
     * @return 完整确认帧字节；frame 为空时返回 null
     */
    public static byte[] buildAck(SL651Frame frame, ZonedDateTime now, byte terminator) {
        if (frame == null) {
            return null;
        }
        byte[] station = frame.getTelemetryStationAddress();
        byte[] reportTime = toBcdTime(now);

        ByteBuf buf = Unpooled.buffer(32);
        try {
            buf.writeBytes(START_MARKER);
            buf.writeBytes(station, 0, 5);
            buf.writeByte(frame.getCenterStationAddress() & 0xFF);
            buf.writeShort(frame.getPassword() & 0xFFFF);
            buf.writeByte(frame.getFunctionCode() & 0xFF);
            // 高 4 位 1000 = 下行；其余 12 位 = 正文长度
            buf.writeShort(0x8000 | (ACK_BODY_LENGTH & 0x0FFF));
            buf.writeByte(ControlChar.STX);
            buf.writeShort(frame.getSerialNumber() & 0xFFFF); // 流水号取自上行帧
            buf.writeBytes(reportTime);
            buf.writeByte(terminator);

            byte[] head = new byte[buf.readableBytes()];
            buf.readBytes(head);
            int crc = CRC16.calculate(head, 0, head.length);

            ByteBuf out = Unpooled.buffer(head.length + 2);
            try {
                out.writeBytes(head);
                out.writeByte((crc >> 8) & 0xFF); // 高位字节在前（表20/21 校验码说明）
                out.writeByte(crc & 0xFF);
                byte[] result = new byte[out.readableBytes()];
                out.readBytes(result);
                return result;
            } finally {
                out.release();
            }
        } finally {
            buf.release();
        }
    }

    /**
     * 时间 → 6 字节 BCD（YY MM DD HH mm SS）。
     */
    static byte[] toBcdTime(ZonedDateTime time) {
        return new byte[]{
                toBcd(time.getYear() % 100),
                toBcd(time.getMonthValue()),
                toBcd(time.getDayOfMonth()),
                toBcd(time.getHour()),
                toBcd(time.getMinute()),
                toBcd(time.getSecond())
        };
    }

    private static byte toBcd(int value) {
        return (byte) (((value / 10) << 4) | (value % 10));
    }
}
