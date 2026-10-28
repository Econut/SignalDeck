/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The receive-audio probe's level measures. */
public class AudioSourceProbeTest {
    private static short[] sine(double hz, double peak, int n) {
        short[] s = new short[n]; for (int i = 0; i < n; i++) s[i] = (short) Math.round(peak * Math.sin(2 * Math.PI * hz * i / AudioSourceProbe.RATE)); return s;
    }

    @Test public void toneAndLevel() {
        short[] s = sine(1000, 16384, 16000);                          // a half-scale 1 kHz sine: RMS -9.0 dBFS
        assertEquals(-9.03, AudioSourceProbe.rmsDbfs(s, s.length), 0.05);
        assertEquals(-9.03, AudioSourceProbe.toneDbfs(s, s.length, 1000, AudioSourceProbe.RATE), 0.1);
        assertTrue(AudioSourceProbe.toneDbfs(s, s.length, 1500, AudioSourceProbe.RATE) < -60);   // another frequency: nothing
        assertEquals(-120, AudioSourceProbe.rmsDbfs(new short[100], 100), 0);
        assertEquals(-120, AudioSourceProbe.toneDbfs(new short[0], 0, 1000, AudioSourceProbe.RATE), 0);
    }
}
