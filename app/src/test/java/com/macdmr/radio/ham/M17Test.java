/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class M17Test {
    @Test public void specificationVectors() {
        assertEquals(0x772B, M17.crc16("123456789".getBytes(), 9));
        assertEquals(0x206E, M17.crc16("A".getBytes(), 1));
        assertEquals(0xFFFF, M17.crc16(new byte[0], 0));
        assertArrayEquals(new byte[] { 0x00, 0x00, 0x4B, 0x13, (byte) 0xD1, 0x06 }, M17.callsign("N0CALL"));
        assertEquals("N0CALL", M17.callsign(M17.callsign("N0CALL"), 0));
        assertEquals("@ALL", M17.callsign(M17.callsign("@ALL"), 0));
        assertEquals(0x0D7880F, M17.golay(0x0D78));
        assertEquals(137, M17.INTRL[1]);
    }

    @Test public void voiceSymbolsMatchTheResearchEncoder() {
        assertEquals("N0CALL", M17.voiceCall("n0call-7"));
        assertEquals("N0CALL", M17.voiceCall(""));
        assertArrayEquals(new byte[] { 0x4B }, M17.packSymbols(new int[] { 3, 1, -1, -3 }));
        int[] eot = M17.eotTail();
        assertEquals(192, eot.length);
        int[] pat = { 3, 3, 3, 3, 3, 3, -3, 3 };
        for (int i = 0; i < eot.length; i++) assertEquals("eot " + i, pat[i % 8], eot[i]);
        byte[] lsf = M17.lsf("N0CALL", "W9XYZ", 0x0005, null);
        int[] got = M17.lsfSymbols(lsf);
        int[] py = { 3,3,3,3,-3,-3,3,-3,-1,3,3,-1,1,-3,-3,3,-1,-1,-1,-1,-1,3,1,1 };
        for (int i = 0; i < py.length; i++) assertEquals("lsf " + i, py[i], got[i]);
        byte[] pay = new byte[16]; for (int i = 0; i < 16; i++) pay[i] = (byte) i;
        int[] st = M17.streamSymbols(lsf, 0x8000, pay, 5);
        int[] pySt = { -3,-3,-3,-3,3,3,-3,3,-3,-3,1,1,-1,-1,-1,3 };
        for (int i = 0; i < pySt.length; i++) assertEquals("str " + i, pySt[i], st[i]);
        assertEquals((byte) 0x55, M17.packSymbols(got)[0]);
        assertEquals((byte) 0xF7, M17.packSymbols(got)[1]);
    }

    @Test public void loopbackThroughTheCodes() {
        byte[] lsf = M17.lsf("W9XYZ", "@ALL", 0x0005, "HELLO M17".getBytes());
        M17.Lsf l = M17.decodeLsf(toFloat(M17.lsfSymbols(lsf)), 8);
        assertTrue(l.crcOk); assertEquals("W9XYZ", l.src()); assertEquals("@ALL", l.dst()); assertEquals("HELLO M17", l.metaText()); assertTrue(l.voice());
        Random r = new Random(1);
        for (int i = 0; i < 12; i++) {
            byte[] p = new byte[16]; r.nextBytes(p);
            float[] sym = toFloat(M17.streamSymbols(lsf, i, p, i % 6));
            for (int k = 8; k < 192; k++) sym[k] += (float) (0.4 * r.nextGaussian());   // noise
            M17.Stream s = M17.decodeStream(sym, 8);
            assertEquals(i, s.fn); assertArrayEquals(p, s.payload); assertEquals(i % 6, s.lichCount());
            assertArrayEquals(M17.streamSymbols(lsf, i, p, i % 6), M17.streamSymbolsFromLich(s.lich, s.fn, s.payload));
        }
    }

    static float[] toFloat(int[] a) { float[] f = new float[a.length]; for (int i = 0; i < a.length; i++) f[i] = a[i]; return f; }

    // ---- real radio audio: HackRF -> the owner's Armor 26 Ultra, four transmissions (seeds 1-4), 2026-10-22 ----

    static short[] wav(InputStream in) throws Exception {
        byte[] b = in.readAllBytes(); int data = 12;
        while (!(b[data] == 'd' && b[data + 1] == 'a' && b[data + 2] == 't' && b[data + 3] == 'a')) data += 8 + ((b[data + 4] & 0xFF) | (b[data + 5] & 0xFF) << 8);
        int n = ((b[data + 4] & 0xFF) | (b[data + 5] & 0xFF) << 8 | (b[data + 6] & 0xFF) << 16) / 2; short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[data + 8 + 2 * i] & 0xFF) | (b[data + 9 + 2 * i] << 8));
        return s;
    }
    static JSONObject meta() throws Exception { return new JSONObject(new String(M17Test.class.getResourceAsStream("/m17/m17_rx_armor26.json").readAllBytes(), "UTF-8")); }
    static int[] symbols(JSONObject part) throws Exception { String s = part.getString("symbols"); int[] o = new int[s.length()]; for (int i = 0; i < o.length; i++) o[i] = 2 * (s.charAt(i) - '0') - 3; return o; }

    @Test public void decodesRealRadioAudioAfterCalibration() throws Exception {
        short[] audio = wav(M17Test.class.getResourceAsStream("/m17/m17_rx_armor26.wav"));
        JSONObject m = meta(); JSONArray parts = m.getJSONArray("parts");
        JSONObject a = parts.getJSONObject(0);
        float[] cal = M17Receiver.calibrate(audio, a.getInt("start8k"), symbols(a));
        System.out.printf("calibration fit MSE %.3f%n", cal[M17Receiver.NCOEF]);
        assertTrue("calibration fits", cal[M17Receiver.NCOEF] < 0.1);
        String out = System.getenv("M17_CAL_OUT");
        if (out != null) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < M17Receiver.NCOEF; i++) sb.append(cal[i]).append('\n'); java.nio.file.Files.write(java.nio.file.Paths.get(out), sb.toString().getBytes()); }

        final List<String> events = new ArrayList<>(); final List<byte[]> payloads = new ArrayList<>(); final List<Integer> fns = new ArrayList<>();
        M17Receiver rx = new M17Receiver(java.util.Arrays.copyOf(cal, M17Receiver.NCOEF), new M17Receiver.Listener() {
            public void started(M17.Lsf l) { events.add("start " + l.src() + " " + l.dst() + " " + l.crcOk); }
            public void frame(M17.Stream s, float q) { payloads.add(s.payload); fns.add(s.fn); }
            public void lsfFromLich(M17.Lsf l) { events.add("lich-lsf " + l.src()); }
            public void ended(int f, int g, String why) { events.add("end " + f + " " + g + " " + why); }
        });
        if (System.getenv("M17_ADAPT") != null) rx.adaptRate = Float.parseFloat(System.getenv("M17_ADAPT"));
        for (int i = 0; i < audio.length; i += 512) rx.feed(java.util.Arrays.copyOfRange(audio, i, Math.min(audio.length, i + 512)), Math.min(512, audio.length - i));
        System.out.println(events);
        // count exact payloads of the three transmissions it was not calibrated on
        int exact = 0;
        for (int pi = 1; pi < 4; pi++) {
            JSONArray pays = parts.getJSONObject(pi).getJSONArray("payloads");
            java.util.Set<String> want = new java.util.HashSet<>(); for (int k = 0; k < pays.length(); k++) want.add(pays.getString(k));
            for (byte[] p : payloads) { StringBuilder h = new StringBuilder(); for (byte x : p) h.append(String.format("%02x", x)); if (want.remove(h.toString())) exact++; }
        }
        System.out.println("exact stream payloads in the three test transmissions: " + exact + " of 300");
        long starts = events.stream().filter(e -> e.startsWith("start N0CALL ALL true")).count();
        assertEquals("four transmissions found, LSF decoded", 4, starts);
        assertTrue("exact " + exact, exact >= 290);
    }
}
