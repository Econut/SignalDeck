/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;
import java.util.Random;

public class Ft8Test {
    private static final String[] MESSAGES = {
        "CQ K1ABC FN42", "K1ABC W9XYZ DM79", "W9XYZ K1ABC -11", "K1ABC W9XYZ R-09", "W9XYZ K1ABC RRR",
        "K1ABC W9XYZ RR73", "K1ABC W9XYZ 73", "CQ DX K1ABC FN42", "CQ 145 K1ABC FN42", "K1ABC/R W9XYZ DM79",
        "G4ABC/P PA9XYZ JO22", "TNX BOB 73 GL", "CQ PJ4/K1ABC", "W9XYZ PJ4/K1ABC RR73", "QRZ K1ABC FN42",
        "CQ N0CALL",   // N0CALL has a four-letter suffix: a non-standard call, type 4, which cannot carry a grid (see theBenchCqLosesItsGridAsWsjtxWould)
    };

    /** The bench's "CQ N0CALL FN20" (FW21 run) came back as "CQ N0CALL": N0CALL's four-letter suffix makes it a non-standard
     *  call, so the message is type 4 and has no grid field, as WSJT-X packs it. A standard call keeps its grid; the app can tell. */
    @Test public void theBenchCqLosesItsGridAsWsjtxWould() {
        byte[] p = Ft8Message.pack("CQ N0CALL FN20", null);
        assertNotNull(p);
        assertEquals("type 4, non-standard call", 4, (p[9] >> 3) & 7);
        assertEquals("CQ N0CALL", Ft8Message.unpack(p, null));
        assertTrue(Ft8Message.dropsGrid("CQ N0CALL FN20"));
        assertFalse(Ft8Message.dropsGrid("CQ N0CALL"));
        byte[] q = Ft8Message.pack("CQ K1ABC FN42", null);
        assertEquals("type 1, standard call", 1, (q[9] >> 3) & 7);
        int g = ((q[7] & 0x3F) << 10) | ((q[8] & 0xFF) << 2) | ((q[9] & 0xFF) >> 6);
        assertEquals("FN42 as the 15-bit grid index", ((('F' - 'A') * 18 + ('N' - 'A')) * 10 + 4) * 10 + 2, g);
        assertEquals("CQ K1ABC FN42", Ft8Message.unpack(q, null));
        assertFalse(Ft8Message.dropsGrid("CQ K1ABC FN42"));
        assertFalse(Ft8Message.dropsGrid("K1ABC W9XYZ -11"));
    }

    @Test public void crcIsCarriedInBits77To90() {
        byte[] p = Ft8Message.pack("CQ K1ABC FN42", null), a = Ft8.addCrc(p);
        int crc = ((a[9] & 0x07) << 11) | ((a[10] & 0xFF) << 3) | ((a[11] & 0xFF) >> 5);
        a[9] &= (byte) 0xF8; a[10] = 0; a[11] = 0;
        assertEquals(Ft8.crc14(a, 82), crc);
        assertNotEquals(0, crc);
    }

    @Test public void everyMessageTypePacksAndUnpacks() {
        for (String m : MESSAGES) {
            Ft8Message.Hashes h = new Ft8Message.Hashes();
            byte[] p = Ft8Message.pack(m, h);
            assertNotNull(m, p);
            String want = m.equals("W9XYZ PJ4/K1ABC RR73") ? "<W9XYZ> PJ4/K1ABC RR73" : m;   // type 4: the standard call goes as a hash, shown in brackets
            assertEquals(m, want, Ft8Message.unpack(p, h));
        }
    }

    @Test public void hashedCallsAreShownWhenHeardBefore() {
        Ft8Message.Hashes tx = new Ft8Message.Hashes(), rx = new Ft8Message.Hashes();
        byte[] p = Ft8Message.pack("PJ4/K1ABC W9XYZ RR73", tx);          // standard message, the "to" call hashed
        assertEquals("<...> W9XYZ RR73", Ft8Message.unpack(p, rx));
        Ft8Message.unpack(Ft8Message.pack("CQ PJ4/K1ABC", tx), rx);     // its CQ carried the call in full
        assertEquals("<PJ4/K1ABC> W9XYZ RR73", Ft8Message.unpack(p, rx));
    }

    @Test public void rejectsWhatDoesNotFit() {
        assertNull(Ft8Message.pack("THIS FREE TEXT IS FAR TOO LONG", null));
        assertNull(Ft8Message.pack("HELLO@WORLD", null));
    }

    @Test public void encodedToneSequenceHasTheCostasSyncAndValidParity() {
        int[] t = Ft8.tones(Ft8Message.pack("CQ K1ABC FN42", null));
        assertEquals(79, t.length);
        for (int k = 0; k < 7; k++) { assertEquals(Ft8.COSTAS[k], t[k]); assertEquals(Ft8.COSTAS[k], t[36 + k]); assertEquals(Ft8.COSTAS[k], t[72 + k]); }
        byte[] cw = Ft8.encode174(Ft8.addCrc(Ft8Message.pack("CQ K1ABC FN42", null)));
        byte[] bits = new byte[174];
        for (int i = 0; i < 174; i++) bits[i] = (byte) ((cw[i / 8] >> (7 - i % 8)) & 1);
        assertEquals(0, Ft8.ldpcCheck(bits));
    }

    private static float[] slot(double snrDb, long seed, Object[][] signals) {
        float[] x = new float[Ft8.RATE * 15];
        Random r = new Random(seed);
        // noise power in 2500 Hz: white noise of variance s2 over 4000 Hz -> s2 * 2500/4000 in 2500 Hz
        double sigPow = 0.5 * 0.1 * 0.1, noise2500 = sigPow / Math.pow(10, snrDb / 10), s2 = noise2500 * 4000 / 2500;
        for (int i = 0; i < x.length; i++) x[i] = (float) (Math.sqrt(s2) * r.nextGaussian());
        for (Object[] s : signals) {
            float[] w = Ft8.synth(Ft8.tones(Ft8Message.pack((String) s[0], null)), (Double) s[1], Ft8.RATE);
            int start = (int) (Ft8.RATE * (0.5 + (Double) s[2]));
            for (int i = 0; i < w.length && start + i < x.length; i++) if (start + i >= 0) x[start + i] += 0.1f * w[i];
        }
        return x;
    }

    @Test public void decodesSeveralSignalsInOneSlot() {
        float[] x = slot(-5, 1, new Object[][] { { "CQ K1ABC FN42", 600.0, 0.0 }, { "K1ABC W9XYZ DM79", 1200.0, 0.3 }, { "W9XYZ K1ABC -11", 1850.0, -0.4 } });
        List<Ft8.Decode> d = Ft8.decode(x, x.length, new Ft8Message.Hashes());
        assertEquals(d.toString(), 3, d.size());
        boolean a = false, b = false, c = false;
        for (Ft8.Decode e : d) {
            if (e.text.equals("CQ K1ABC FN42")) { a = true; assertEquals(600, e.freqHz, 3.2); assertEquals(0.0, e.dtS, 0.09); }
            if (e.text.equals("K1ABC W9XYZ DM79")) { b = true; assertEquals(1200, e.freqHz, 3.2); assertEquals(0.3, e.dtS, 0.09); }
            if (e.text.equals("W9XYZ K1ABC -11")) { c = true; assertEquals(1850, e.freqHz, 3.2); assertEquals(-0.4, e.dtS, 0.09); }
        }
        assertTrue(a && b && c);
    }

    @Test public void decodesBelowTheNoise() {
        int ok = 0;
        for (int seed = 0; seed < 5; seed++) {
            float[] x = slot(-16, 100 + seed, new Object[][] { { "K1ABC W9XYZ RR73", 1000.0 + 37 * seed, 0.1 } });
            for (Ft8.Decode e : Ft8.decode(x, x.length, new Ft8Message.Hashes())) if (e.text.equals("K1ABC W9XYZ RR73")) ok++;
        }
        assertTrue("decoded " + ok + " of 5 at -16 dB", ok >= 4);
    }

    @Test public void reportsAnSnrNearTheTruth() {
        float[] x = slot(-8, 9, new Object[][] { { "CQ K1ABC FN42", 1500.0, 0.0 } });
        List<Ft8.Decode> d = Ft8.decode(x, x.length, new Ft8Message.Hashes());
        assertEquals(1, d.size());
        assertEquals(-8, d.get(0).snrDb, 4);
    }

    @Test public void noiseAloneDecodesNothing() {
        for (int seed = 0; seed < 4; seed++) {
            float[] x = slot(0, 500 + seed, new Object[0][]);
            assertTrue(Ft8.decode(x, x.length, new Ft8Message.Hashes()).isEmpty());
        }
    }

    @Test public void fftMatchesADirectDft() {
        int n = 2560; Ft8.MixedFft f = new Ft8.MixedFft(n);
        double[] re = new double[n], im = new double[n]; Random r = new Random(3);
        for (int i = 0; i < n; i++) re[i] = r.nextGaussian();
        double[] x = re.clone();
        f.transform(re, im);
        for (int k : new int[] { 0, 1, 7, 333, 1280, 2559 }) {
            double sr = 0, si = 0;
            for (int i = 0; i < n; i++) { sr += x[i] * Math.cos(-2 * Math.PI * k * i / n); si += x[i] * Math.sin(-2 * Math.PI * k * i / n); }
            assertEquals(sr, re[k], 1e-6); assertEquals(si, im[k], 1e-6);
        }
    }
}
