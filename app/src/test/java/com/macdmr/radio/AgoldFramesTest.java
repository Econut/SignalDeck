/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** Synthetic frames only (made-up IDs and keys); the layouts are the Agold stock app's. */
public class AgoldFramesTest {
    private static final int ID = 1234567, CALL = 7654321;

    /** A whole frame the way the stock app builds it (IComUtils.makePackage): one's-complement checksum over route..body. */
    static byte[] frame(int route, int cmd, int rw, byte[] body) {
        byte[] f = new byte[8 + body.length + 3];
        f[0] = 0x7E; f[1] = (byte) 0x96; f[2] = 0x69; f[3] = (byte) route; f[4] = (byte) cmd; f[5] = (byte) rw;
        f[6] = (byte) (body.length >> 8); f[7] = (byte) body.length;
        System.arraycopy(body, 0, f, 8, body.length);
        long sum = 0;
        for (int i = 3; i < 8 + body.length; i += 2) sum += ((f[i] & 0xFF) << 8) | (i + 1 < 8 + body.length ? f[i + 1] & 0xFF : 0);
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        int ck = (int) (~sum & 0xFFFF);
        f[8 + body.length] = (byte) (ck >> 8); f[9 + body.length] = (byte) ck; f[10 + body.length] = (byte) 0x81;
        return f;
    }
    static String hex(byte[] b, int n, boolean spaced) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) { if (spaced && i > 0) s.append(' '); s.append(String.format("%02X", b[i] & 0xFF)); }
        return s.toString();
    }
    static void le(byte[] b, int at, int v) { for (int i = 0; i < 4; i++) b[at + i] = (byte) (v >> (8 * i)); }
    static void be(byte[] b, int at, int v) { for (int i = 0; i < 4; i++) b[at + i] = (byte) (v >> (8 * (3 - i))); }
    static String bytesAt(String spacedHex, int from, int n) { String[] t = spacedHex.split(" "); StringBuilder s = new StringBuilder(); for (int i = from; i < from + n; i++) s.append(t[i]).append(' '); return s.toString().trim(); }

    @Test public void dmr09DigitalChannelLosesIdCallNumberAndKey() {
        byte[] body = new byte[163];
        le(body, 0, 441_000_000); le(body, 4, 441_000_000); le(body, 8, ID);
        for (int g = 0; g < 32; g++) le(body, 12 + 4 * g, g == 0 ? 91 : 0);       // talkgroups stay: they are not about a person
        le(body, 140, CALL);
        for (int k = 0; k < 8; k++) body[151 + k] = (byte) ('a' + k);
        String line = "I ExtModuleProtocol: setDmr09DigitalGroup packByte:" + hex(frame(1, 0x22, 1, body), 999, true);
        String r = AgoldFrames.redact(line);
        String f = r.substring(r.indexOf("7E 96 69"));
        assertEquals("## ## ## ##", bytesAt(f, 16, 4));                          // own ID
        assertEquals("## ## ## ##", bytesAt(f, 148, 4));                         // TX call number
        assertEquals("## ## ## ## ## ## ## ##", bytesAt(f, 159, 8));             // key
        assertEquals("## ##", bytesAt(f, 171, 2));                               // checksum
        assertEquals(hex(frame(1, 0x22, 1, body), 16, true), bytesAt(f, 0, 16)); // header and frequencies kept
        assertEquals("5B 00 00 00", bytesAt(f, 20, 4));                          // TG 91
        assertTrue(f.endsWith("81"));
    }

    @Test public void olderDigitalChannelCutShortInARunOfHex() {
        byte[] body = new byte[156];
        body[0] = 1; le(body, 1, 446_000_000); le(body, 5, 446_000_000); be(body, 9, ID); body[13] = 1; body[14] = 1; be(body, 15, CALL);
        for (int k = 0; k < 8; k++) body[20 + k] = (byte) ('a' + k);
        String full = hex(frame(1, 0x36, 1, body), 96, false);                   // the log cut it after 96 bytes
        String r = AgoldFrames.redact("I ExtModuleManager: createCmdInThread >> " + full);
        String f = r.substring(r.indexOf("7E9669"));
        assertEquals(full.substring(0, 34), f.substring(0, 34));                  // header + power + frequencies
        assertEquals("########", f.substring(34, 42));                           // own ID (body 9..12)
        assertEquals(full.substring(42, 46), f.substring(42, 46));                // colour code, contact type
        assertEquals("########", f.substring(46, 54));                           // call number
        assertEquals("################", f.substring(56, 72));                   // key
        assertFalse(r.contains(String.format("%08X", ID)));
    }

    @Test public void textAndCallerBodiesGoWhole() {
        byte[] sms = "Hello there".getBytes();
        String r = AgoldFrames.redact("parseCmd strCmd:" + hex(frame(1, 0x2D, 0, sms), 999, true));
        assertFalse(r.contains("48 65 6C"));
        assertTrue(r.contains("7E 96 69 01 2D 00 00 0B ##"));
        String v = "parseCmd strCmd:" + hex(frame(1, 0x34, 0, "DMR006.TEST.V001".getBytes()), 999, true);
        assertEquals(v, AgoldFrames.redact(v));                                  // a version reply is not private
        String mcu = "parseCmd strCmd:" + hex(frame(0, 0x2D, 0, sms), 999, true);
        assertEquals(mcu, AgoldFrames.redact(mcu));                              // route 00 is the phone-side MCU's own command
    }

    @Test public void reportMaskKeepsFrameHexButStillMasksText() {
        byte[] body = new byte[156]; be(body, 9, ID);
        String line = "caller 1234567 sent " + hex(frame(1, 0x36, 1, body), 999, false) + " then 7654321";
        String m = DeviceReport.mask(line);
        assertTrue(m, m.startsWith("caller ###### sent 7E966901360100"));
        assertTrue(m, m.endsWith(" then ######"));
        assertFalse(m, m.contains(String.format("%08X", ID)));
    }

    @Test public void answersComeFromRepliesOnly() {
        StringBuilder log = new StringBuilder();
        log.append("setDmr09DigitalGroup packByte:").append(hex(frame(1, 0x22, 1, new byte[163]), 60, true)).append('\n');
        log.append("parseCmd strCmd:").append(hex(frame(0, 0x01, 0, "20201016161541".getBytes()), 999, true)).append('\n');   // phone-side MCU: not the module
        log.append("parseCmd strCmd:").append(hex(frame(1, 0x34, 0, "DMR006.TEST.V001".getBytes()), 999, true)).append('\n');
        log.append("parseCmd strCmd:").append(hex(frame(1, 0x22, 0, new byte[] { 0 }), 999, true)).append('\n');
        log.append("handleCmdResponse strCmd:").append(hex(frame(1, 0x2E, 0, new byte[] { 0 }), 999, false)).append('\n');
        log.append("parseCmd strCmd:").append(hex(frame(1, 0x23, 0, new byte[] { 1 }), 999, true)).append('\n');
        String a = AgoldFrames.answers(log.toString());
        assertEquals("the module answered the stock app 4 times (Agold frames in its log; version DMR006.TEST.V001; 0x22 0x2E accepted; 0x23 refused)", a);
        assertNull(AgoldFrames.answers("no frames here\n"));
        assertNull(AgoldFrames.answers("parseCmd strCmd:" + hex(frame(0, 0x06, 0, new byte[] { 0 }), 999, true)));
    }

    @Test public void twoFramesInOneRun() {
        String two = hex(frame(1, 0x22, 0, new byte[] { 0 }), 999, false) + hex(frame(1, 0x2E, 0, new byte[] { 0 }), 999, false);
        assertEquals(2, AgoldFrames.find(two).size());
    }

    @Test public void watchLineIsReadBack() {
        assertEquals("the module answered the stock app 2 times (Agold frames in its log)",
                FirstRunCheck.stockAnswers("start x\n" + FirstRunCheck.STOCK_ANSWERS + "the module answered the stock app 2 times (Agold frames in its log)\nits log:\n"));
        assertNull(FirstRunCheck.stockAnswers(FirstRunCheck.STOCK_ANSWERS + FirstRunCheck.NONE_RECOGNISED + "\n"));
        assertNull(FirstRunCheck.stockAnswers("nothing"));
    }
}
