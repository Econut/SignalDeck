/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.dsp;

import static org.junit.Assert.*;

import org.junit.Test;

public class SoftSqClipBenchTest {
    /** The replayed gate must match the real detector on the same audio, or the sweep would tune the wrong thing. */
    @Test public void replayedGateMatchesTheDetector() {
        java.util.Random r = new java.util.Random(7);
        int win = 2000, n = 40; short[] s = new short[win * n];
        for (int i = 0; i < s.length; i++) {
            boolean hiss = (i / win) % 10 < 4;                                   // 1 s of hiss, then 1.5 s of a voice-band tone
            s[i] = (short) (hiss ? r.nextGaussian() * 3000 : 6000 * Math.sin(2 * Math.PI * 700 * i / 8000.0));
        }
        double[] sm = new double[n]; boolean[] quiet = new boolean[n];
        for (int level = 1; level <= 9; level++) {
            NoiseSquelchDetector d = new NoiseSquelchDetector(8000); int open = 0;
            for (int w = 0; w < n; w++) {
                byte[] b = new byte[win * 2]; double e = 0;
                for (int i = 0; i < win; i++) { short x = s[w * win + i]; b[2 * i] = (byte) x; b[2 * i + 1] = (byte) (x >> 8); e += (double) x * x; }
                d.process(b, b.length, level);
                if (d.isSignal()) open++;
                sm[w] = d.smoothed(); quiet[w] = e < win * 2500.0;
            }
            double[] t = {0, 38, 35, 32, 29, 27, 24, 22, 19, 16};
            assertEquals("level " + level, 100 * open / n, SoftSqClipBench.gatePercent(sm, quiet, t[level]));
        }
    }
}
