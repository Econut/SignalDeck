/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import static org.junit.Assert.*;

import org.junit.Test;

/** The SSTV encoder's timing against the mode standards, and what the transmit limit admits. */
public class SstvEncoderTest {
    private static int[] flat(int w, int h, int argb) { int[] px = new int[w * h]; java.util.Arrays.fill(px, argb); return px; }

    @Test public void durationsMatchTheModeStandards() {
        // image time per the specifications (lines × line period), the encoder adds ~1.1 s of leader, VIS and tail
        assertEquals(240 * 150.0, SSTVEncoder.lines(SSTVEncoder.ROBOT_36) * SSTVEncoder.lineMs(SSTVEncoder.ROBOT_36), 0.01);
        assertEquals(256 * 446.446, SSTVEncoder.lines(SSTVEncoder.MARTIN_M1) * SSTVEncoder.lineMs(SSTVEncoder.MARTIN_M1), 0.01);
        assertEquals(256 * 226.798, SSTVEncoder.lines(SSTVEncoder.MARTIN_M2) * SSTVEncoder.lineMs(SSTVEncoder.MARTIN_M2), 0.01);
        assertEquals(256 * 428.22, SSTVEncoder.lines(SSTVEncoder.SCOTTIE_S1) * SSTVEncoder.lineMs(SSTVEncoder.SCOTTIE_S1), 0.01);
        assertEquals(256 * 277.692, SSTVEncoder.lines(SSTVEncoder.SCOTTIE_S2) * SSTVEncoder.lineMs(SSTVEncoder.SCOTTIE_S2), 0.01);
        assertEquals(128 * 703.04, SSTVEncoder.lines(SSTVEncoder.PD_90) / 2 * SSTVEncoder.lineMs(SSTVEncoder.PD_90), 0.01);
        assertEquals(36000 + 1110, SSTVEncoder.durationMs(SSTVEncoder.ROBOT_36), 1);
        assertEquals(114290 + 1110, SSTVEncoder.durationMs(SSTVEncoder.MARTIN_M1), 2);
        assertEquals(58060 + 1110, SSTVEncoder.durationMs(SSTVEncoder.MARTIN_M2), 2);
        assertEquals(109624 + 1110, SSTVEncoder.durationMs(SSTVEncoder.SCOTTIE_S1), 2);
        assertEquals(71089 + 1110, SSTVEncoder.durationMs(SSTVEncoder.SCOTTIE_S2), 2);
        assertEquals(89989 + 1110, SSTVEncoder.durationMs(SSTVEncoder.PD_90), 2);
        assertEquals(120 * 197 + 1110, SSTVEncoder.durationMs(SSTVEncoder.ROBOT_24_COLOR), 1);   // the encoder's Robot 24 line is 197 ms (as it always was)
    }

    @Test public void everyModeFitsTheRadioWithItsLeadIn() {
        for (int m : SSTVEncoder.MODES) {
            assertTrue(SSTVEncoder.name(m), SSTVEncoder.isSupported(m));
            assertTrue(SSTVEncoder.name(m) + " with the 1.6 s lead-in under 118 s", SSTVEncoder.durationMs(m) + 1600 <= 118_000);
            assertEquals(SSTVEncoder.name(m), SSTVEncoder.durationMs(m) > 30_000, SSTVEncoder.isLong(m));
        }
        assertFalse(SSTVEncoder.isLong(SSTVEncoder.ROBOT_24_COLOR)); assertTrue(SSTVEncoder.isLong(SSTVEncoder.ROBOT_36));
        assertEquals("Martin M1 (≈115 s)", SSTVEncoder.label(SSTVEncoder.MARTIN_M1));
        assertEquals("Robot 8 BW (≈9 s)", SSTVEncoder.label(SSTVEncoder.ROBOT_8_BW));   // the audio: 8 s of image plus leader, VIS and tail
        assertFalse(SSTVEncoder.isSupported(SSTVMode.MODE_SCOTTIE_DX));   // 316 s: never
    }

    @Test public void theAudioIsAsLongAsTheModeSays() {
        for (int m : new int[] { SSTVEncoder.ROBOT_36, SSTVEncoder.MARTIN_M2, SSTVEncoder.SCOTTIE_S2, SSTVEncoder.PD_90, SSTVEncoder.MARTIN_M1, SSTVEncoder.SCOTTIE_S1 }) {
            int w = SSTVEncoder.width(m), h = SSTVEncoder.lines(m);
            short[] pcm = SSTVEncoder.encodePixels(flat(w, h, 0xFF80C040), w, h, m);
            int ms = pcm.length / 8;
            // Scottie carries one extra 9 ms sync before the first line
            int expect = SSTVEncoder.durationMs(m) + (m == SSTVEncoder.SCOTTIE_S1 || m == SSTVEncoder.SCOTTIE_S2 ? 9 : 0);
            assertEquals(SSTVEncoder.name(m), expect, ms, 2);
            int peak = 0; for (short v : pcm) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > 29000 && peak <= 30000);
        }
    }

    @Test public void visCarriesTheCodeLsbFirstWithEvenParity() {
        // the VIS bits sit after 100 ms extra leader + 300 + 10 + 300 ms, then a 30 ms start bit; each bit 30 ms at 1100 (1) / 1300 (0) Hz
        int w = 320, h = 256;
        short[] pcm = SSTVEncoder.encodePixels(flat(w, h, 0xFF000000), w, h, SSTVEncoder.MARTIN_M1);
        int at = (100 + 300 + 10 + 300 + 30) * 8;
        int code = 0, ones = 0;
        for (int bit = 0; bit < 8; bit++) {
            double f = frequency(pcm, at + bit * 240 + 20, 200);
            int v = f < 1200 ? 1 : 0;
            if (bit < 7) { code |= v << bit; ones += v; } else assertEquals("even parity", ones % 2, v);
        }
        assertEquals(SSTVMode.MODE_MARTIN_M1, code);
        short[] pd = SSTVEncoder.encodePixels(flat(w, h, 0xFF000000), w, h, SSTVEncoder.PD_90);
        int c2 = 0; for (int bit = 0; bit < 7; bit++) c2 |= (frequency(pd, at + bit * 240 + 20, 200) < 1200 ? 1 : 0) << bit;
        assertEquals(0x63, c2);
    }

    /** Zero-crossing frequency estimate over n samples at 8 kHz. */
    static double frequency(short[] s, int from, int n) {
        int cross = 0; for (int i = from + 1; i < from + n; i++) if ((s[i - 1] < 0) != (s[i] < 0)) cross++;
        return cross * 8000.0 / (2.0 * n);
    }
}
