/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio.ham;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The call recording is the samples in order, including a call longer than a few seconds. */
public class HeldPathRingTest {
    @Test public void twelve_seconds_come_back_contiguous() {
        double[] taps = new double[HeldPath.TAPS];
        taps[20] = 1;
        HeldPathReceiver rx = new HeldPathReceiver(taps, new HeldPathReceiver.Listener() {
            public void started(M17.Lsf lsf) { }
            public void link(M17.Lsf lsf) { }
            public void frame(M17.Stream s, float quality) { }
            public void ended(int frames, int good, String why) { }
        }, false);
        rx.beginCallForTest();
        int n = 12 * 8000;
        byte[] buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            short s = (short) i;
            buf[2 * i] = (byte) s;
            buf[2 * i + 1] = (byte) (s >> 8);
        }
        rx.feed(buf, buf.length);
        short[] back = rx.takeAudio();
        assertEquals(n, back.length);
        for (int i = 0; i < n; i++) assertEquals((short) i, back[i]);
    }
}
