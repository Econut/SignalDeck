/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Agold USB bridge envelope around the Armor command set: {@code 7E 96 69 | route | cmd | rw | len(2, BE) | body |
 * checksum(2, BE) | 81}. Route 01 is the module. The result of a set command is the body's one byte, not a header
 * field, and only on a reply ({@code rw} 00). A module push keeps that byte in the body. A command that is
 * not in either table is still delivered: one reply byte is the result, anything else is data. Each such
 * command is logged once. Route 00 is the bridge itself and never becomes a module frame.
 */
public final class AgoldEnvelope implements FrameCodec {
    /** Module replies whose body is the data (version, RSSI, caller, text, read-back, the symbol engine). */
    private static final int[] DATA = { 0x24, 0x25, 0x2B, 0x2D, 0x32, 0x34, 0x40 };
    /** Module replies whose one body byte is the result. */
    private static final int[] SET = { 0x22, 0x23, 0x2E, 0x30, 0x31, 0x36 };

    private BridgeListener bridge;
    private Consumer<String> log;
    private final Set<Integer> logged = new HashSet<>();

    @Override public void setBridge(BridgeListener listener) { bridge = listener; }
    @Override public void setLog(Consumer<String> log) { this.log = log; }

    @Override public byte[] encode(Frame frame) {
        int[] data = new int[frame.body.length];
        for (int i = 0; i < data.length; i++) data[i] = frame.body[i] & 0xFF;
        return AgoldFrames.request(0x01, frame.cmd, data);
    }

    @Override public int tryDecode(byte[] b, int off, int avail, Frame[] out) {
        out[0] = null;
        if (avail < 1) return 0;
        if ((b[off] & 0xFF) != 0x7E) return -1;
        if (avail < 3) return 0;
        if ((b[off + 1] & 0xFF) != 0x96 || (b[off + 2] & 0xFF) != 0x69) return -1;
        if (avail < 8) return 0;
        int len = ((b[off + 6] & 0xFF) << 8) | (b[off + 7] & 0xFF);
        if (len > 1024) return -1;
        int total = 11 + len;
        if (avail < total) return 0;
        if ((b[off + total - 1] & 0xFF) != 0x81) return -1;
        int[] u = new int[total];
        for (int i = 0; i < total; i++) u[i] = b[off + i] & 0xFF;
        int end = 8 + len;
        if (AgoldFrames.checksum(u, 3, end) != ((u[end] << 8) | u[end + 1])) return -1;
        int route = u[3], cmd = u[4], rw = u[5];
        byte[] body = Arrays.copyOfRange(b, off + 8, off + end);
        if (route == 0) {
            if (bridge != null) bridge.onBridge(cmd, body);
            return total;
        }
        if (route != 1) return total;
        out[0] = moduleFrame(cmd, rw, body);
        return total;
    }

    private Frame moduleFrame(int cmd, int rw, byte[] body) {
        if (listed(DATA, cmd)) return new Frame(cmd, rw, 0, body);
        boolean reply = rw == 0;
        if (listed(SET, cmd)) {
            if (reply && body.length >= 1) return new Frame(cmd, rw, body[0] & 0xFF, new byte[0]);
            return new Frame(cmd, rw, 0, body);
        }
        if (logged.add(cmd) && log != null) log.accept(String.format(Locale.US, "envelope cmd=0x%02X unlisted", cmd));
        if (reply && body.length == 1) return new Frame(cmd, rw, body[0] & 0xFF, new byte[0]);
        return new Frame(cmd, rw, 0, body);
    }

    private static boolean listed(int[] cmds, int cmd) {
        for (int c : cmds) if (c == cmd) return true;
        return false;
    }
}
