/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Modified for UNIWA P8 compatibility, 2026-10-27. See COPYRIGHT.
 */
package com.macdmr.radio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** P8 wire-data adapter. Pure data only: no UART, GPIO, Android or root access.
 * Derived from the owner's WW_DMR APK (SHA-256 and source anchors in docs/uniwa-p8.md).
 * A preview is NOT permission to send it. In particular, PTT is GPIO-driven on this phone.
 */
public final class P8Protocol {
    private P8Protocol() { }
    public static final String CONTROL_PORT = "/dev/ttysWK0", PCM_PORT = "/dev/ttyS0";
    public static final int CONTROL_BAUD = 57600, PCM_BAUD = 230400;
    public static final String POWER_NODE = "/sys/bus/platform/drivers/dmr_gpio/dmr_pwr";
    public static final String AUDIO_CONTROL_NODE = "/sys/bus/platform/drivers/dmr_gpio/auctusctl";
    public static final String PTT_NODE = "/sys/devices/platform/dmr_gpio/dmrptt";

    /** Values are wire enums, not UI indices. Retain the stock channel's values until measured.
     * The last byte deliberately repeats monitor: y2.c and r2.i do this on the P8.
     * Do not substitute the generic engine's hard-coded relay=2.
     */
    public static Frame analog(int rxHz, int txHz, int bandwidth, int power, int squelch,
                               int rxToneType, int rxToneIndex, int txToneType, int txToneIndex,
                               int powerSave, int volume, int monitor) {
        frequency(rxHz); frequency(txHz);
        int[] bytes = {bandwidth, power, squelch, rxToneType, rxToneIndex, txToneType,
                txToneIndex, powerSave, volume, monitor};
        for (int v : bytes) if (v < 0 || v > 255) throw new IllegalArgumentException("wire field outside 0..255");
        ByteBuffer b = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(rxHz).putInt(txHz);
        for (int v : bytes) b.put((byte) v);
        b.put((byte) monitor);
        return Frame.request(0x23, b.array());
    }

    private static void frequency(int hz) {
        if (hz < 100000000 || hz > 1000000000) throw new IllegalArgumentException("frequency must be Hz in 100..1000 MHz; hardware limits unverified");
    }

    public static Frame versionQuery() { return Frame.request(0x34, (byte) 1); }
    public static Frame receiveVolume(int wireValue) {
        if (wireValue < 0 || wireValue > 255) throw new IllegalArgumentException("wire volume outside 0..255");
        return Frame.request(0x2e, (byte) wireValue);
    }

    /** Stock app decodes the whole version body as text, not a little-endian version int. */
    public static String versionText(Frame f) {
        if (f.cmd != 0x34 || f.rw != 0 || f.sr != 0) throw new IllegalArgumentException("not a successful version reply");
        int end = f.body.length;
        while (end > 0 && (f.body[end - 1] == 0 || f.body[end - 1] == '\r' || f.body[end - 1] == '\n')) end--;
        for (int i = 0; i < end; i++) if ((f.body[i] & 255) < 32 || (f.body[i] & 255) > 126)
            throw new IllegalArgumentException("version contains non-ASCII bytes; retain raw reply");
        return new String(f.body, 0, end, StandardCharsets.US_ASCII);
    }

    public static String describe(Frame f) {
        if (f.cmd == 0x34 && f.rw == 0 && f.sr == 0) return "Module version: " + versionText(f);
        if (f.cmd == 0x36 && f.body.length == 1) {
            String[] status = {"idle/unknown", "receive start", "receive end", "transmit start", "transmit end",
                    "SMS received", "repeater timeout", "busy / transmit denied", "SMS sent", "SMS failed",
                    "mixed-mode digital receive start", "mixed-mode digital receive end",
                    "mixed-mode analogue receive start", "mixed-mode analogue status 0D (stock label ambiguous)"};
            int code = f.body[0] & 255;
            return "Status: " + (code < status.length ? status[code] : "unknown " + code);
        }
        return f.toString();
    }

    /** One complete checksummed frame; rejects junk, truncated data and multiple frames. */
    public static Frame decodeHex(String text) {
        if (text == null) throw new IllegalArgumentException("missing hex");
        String hex = text.replaceAll("\\s", "");
        if (hex.length() < 18 || hex.length() > 2066 || (hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]+"))
            throw new IllegalArgumentException("enter one complete frame as hexadecimal bytes");
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        Frame[] out = new Frame[1];
        if (Frame.tryDecode(b, 0, b.length, out) != b.length) throw new IllegalArgumentException("frame length, checksum or markers invalid");
        return out[0];
    }
}
