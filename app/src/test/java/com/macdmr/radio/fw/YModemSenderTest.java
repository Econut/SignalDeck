/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio.fw;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YModemSenderTest {
    @Test public void crcMatchesTheCcittValue() {
        byte[] check = "123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(0x31C3, YModemSender.crc16(check, 0, check.length));
    }

    @Test public void nameBlockIsSohWithTheSize() {
        byte[] b = YModemSender.nameBlock("img.bin", 378620);
        assertEquals(133, b.length);
        assertEquals(YModemSender.SOH, b[0] & 0xFF);
        assertEquals(0, b[1] & 0xFF);
        assertEquals(0xFF, b[2] & 0xFF);
        String text = new String(b, 3, 20, java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("img.bin\0" + 378620));
        int crc = YModemSender.crc16(b, 3, 128);
        assertEquals(crc >> 8, b[131] & 0xFF);
        assertEquals(crc & 0xFF, b[132] & 0xFF);
    }

    @Test public void aShortImageCompletes() throws Exception {
        byte[] image = new byte[100];
        image[0] = 9;
        Script wire = new Script();
        wire.reply(YModemSender.CRC_REQ);
        wire.reply(YModemSender.ACK, YModemSender.CRC_REQ);
        wire.reply(YModemSender.ACK);
        wire.reply(YModemSender.ACK);
        wire.reply(YModemSender.ACK);
        Pins pins = new Pins();
        YModemSender.Result r = new YModemSender(ms -> { }, s -> { }).send(
                image, "img.bin", "pwd", "ptt", wire, pins, false, () -> "DMR003.FW22.V022");
        assertEquals("ok", r.code);
        assertEquals("DMR003.FW22.V022", r.detail);
        assertEquals(0x31, wire.tx.get(0)[0] & 0xFF);
        assertEquals(YModemSender.STX, wire.tx.get(2)[0] & 0xFF);
        assertEquals(1, wire.tx.get(2)[1] & 0xFF);
        assertEquals(9, wire.tx.get(2)[3] & 0xFF);
        assertEquals(YModemSender.EOT, wire.tx.get(3)[0] & 0xFF);
        assertTrue(pins.high.get(pins.high.size() - 1));
    }

    @Test public void silenceIsNoPrompt() {
        Script wire = new Script();
        YModemSender.Result r = new YModemSender(ms -> { }, s -> { }).send(
                new byte[10], "a.bin", "pwd", "ptt", wire, new Pins(), false, () -> "");
        assertEquals("no-prompt", r.code);
        assertEquals(3, count(wire, 0x31));
    }

    @Test public void sixNaksStop() {
        Script wire = new Script();
        wire.reply(YModemSender.CRC_REQ);
        for (int i = 0; i < 8; i++) wire.reply(YModemSender.NAK);
        YModemSender.Result r = new YModemSender(ms -> { }, s -> { }).send(
                new byte[10], "a.bin", "pwd", "ptt", wire, new Pins(), false, () -> "");
        assertEquals("nak-limit", r.code);
    }

    @Test public void canCancels() {
        Script wire = new Script();
        wire.reply(YModemSender.CRC_REQ);
        wire.reply(YModemSender.CAN);
        YModemSender.Result r = new YModemSender(ms -> { }, s -> { }).send(
                new byte[10], "a.bin", "pwd", "ptt", wire, new Pins(), false, () -> "");
        assertEquals("cancelled", r.code);
    }

    @Test public void abortClosesAfterHalf() throws Exception {
        byte[] image = new byte[4096];
        Script wire = new Script();
        wire.reply(YModemSender.CRC_REQ);
        wire.reply(YModemSender.ACK, YModemSender.CRC_REQ);
        wire.reply(YModemSender.ACK);
        wire.reply(YModemSender.ACK);
        YModemSender.Result r = new YModemSender(ms -> { }, s -> { }).send(
                image, "img.bin", "pwd", "ptt", wire, new Pins(), true, () -> "nope");
        assertEquals("aborted", r.code);
        assertTrue(wire.closed);
        assertEquals(4, wire.tx.size()); // 0x31, block 0, data 1, data 2
    }

    private static int count(Script wire, int b) {
        int n = 0;
        for (byte[] t : wire.tx) for (byte v : t) if ((v & 0xFF) == b) n++;
        return n;
    }

    private static final class Script implements YModemSender.Wire {
        final List<int[]> bursts = new ArrayList<>();
        final List<byte[]> tx = new ArrayList<>();
        int burst = -1, at;
        boolean closed;
        void reply(int... bytes) { bursts.add(bytes); }
        public void write(byte[] b) { tx.add(Arrays.copyOf(b, b.length)); burst++; at = 0; }
        public int read(int timeoutMs) {
            if (burst < 0 || burst >= bursts.size()) return -1;
            int[] cur = bursts.get(burst);
            return at >= cur.length ? -1 : cur[at++];
        }
        public void close() { closed = true; }
    }

    private static final class Pins implements YModemSender.Pins {
        final List<Boolean> high = new ArrayList<>();
        public void set(String node, boolean on) { high.add(on); }
    }
}
