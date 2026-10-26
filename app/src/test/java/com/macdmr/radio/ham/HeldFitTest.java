/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio.ham;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A made-up 41-tap channel plus noise. The fit has to bring the payloads back. */
public class HeldFitTest {
    @Test public void fit_recovers_a_synthetic_channel() {
        byte[] lsf = M17.lsf("N0CALL", "N0CALL", M17.streamVoice(0), new byte[14]);
        List<int[]> frames = new ArrayList<int[]>();
        List<byte[]> payloads = new ArrayList<byte[]>();
        for (int i = 0; i < 30; i++) {
            byte[] p = new byte[16];
            for (int k = 0; k < 16; k++) p[k] = (byte) (i * 16 + k);
            frames.add(M17.streamSymbols(lsf, i, p, i % 6));
            payloads.add(p);
        }
        int n = frames.size() * 192;
        double[] sent = new double[n];
        for (int i = 0; i < frames.size(); i++) for (int k = 0; k < 192; k++) sent[i * 192 + k] = frames.get(i)[k];
        Random rng = new Random(1);
        double[] rx = new double[n + 40];
        for (int t = 0; t < n; t++) {
            double echo = t > 0 ? 0.25 * sent[t - 1] : 0;
            rx[20 + t] = sent[t] + echo + 0.02 * rng.nextGaussian();
        }
        List<HeldFit.Frame> fitFrames = new ArrayList<HeldFit.Frame>();
        for (int i = 0; i < frames.size(); i++) {
            double[] w = Arrays.copyOfRange(rx, i * 192, i * 192 + 232);
            double[] sliced = Arrays.copyOfRange(w, 20, 20 + 192);
            assertTrue(HeldFit.targetOk(sliced, frames.get(i)));
            fitFrames.add(new HeldFit.Frame(w, frames.get(i)));
        }
        assertNull(HeldFit.fit(fitFrames.subList(0, 19)));
        double[] taps = HeldFit.fit(fitFrames);
        assertEquals(41, taps.length);
        assertEquals(30, HeldFit.exactStreams(fitFrames, taps, payloads));
    }

    @Test public void guard_and_second_call() {
        int[] known = new int[192];
        double[] sliced = new double[192];
        for (int i = 0; i < 192; i++) { known[i] = (i & 1) == 0 ? 3 : -3; sliced[i] = known[i]; }
        assertTrue(HeldFit.targetOk(sliced, known));
        for (int i = 0; i < 10; i++) sliced[i] = -sliced[i];
        assertTrue(!HeldFit.targetOk(sliced, known));
        assertTrue(HeldFit.keep(28, 28, true, -80, -97, "N0CALL", "DMRTST-7"));
        assertTrue(!HeldFit.keep(28, 28, false, -80, -97, "N0CALL", "DMRTST-7"));
        assertTrue(!HeldFit.keep(20, 28, true, -80, -97, "N0CALL", "N0CALL"));
        assertTrue(HeldFit.keep(28, 28, true, -80, -86, "N0CALL", "DMRTST-7"));
        assertTrue(!HeldFit.keep(28, 28, true, -80, -85, "N0CALL", "N0CALL"));
    }
}
