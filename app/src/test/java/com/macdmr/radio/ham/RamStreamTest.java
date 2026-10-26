/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

/** The FW27 receive-RAM stream: frame parsing, the miss count, and the sample order into the receiver. */
public class RamStreamTest {
    private static byte[] body(int seq, int id, short[] samples) {
        byte[] b = new byte[RamStream.BODY];
        b[0] = RamStream.OP; b[1] = (byte) seq; b[2] = (byte) (seq >> 8); b[3] = (byte) id;
        for (int i = 0; i < RamStream.SAMPLES; i++) { b[4 + 2 * i] = (byte) (samples[i] >> 8); b[5 + 2 * i] = (byte) samples[i]; }
        return b;
    }

    private static short[] ramp(int from) { short[] s = new short[RamStream.SAMPLES]; for (int i = 0; i < s.length; i++) s[i] = (short) (from + i); return s; }

    @Test public void parsesBigEndianSamplesAndHeader() {
        short[] s = ramp(-300);
        RamStream.Half h = RamStream.parse(body(0x1234, 1, s));
        assertNotNull(h);
        assertEquals(0x1234, h.seq); assertEquals(1, h.id);
        assertArrayEquals(s, h.samples);
    }

    @Test public void otherBodiesAreNotHalves() {
        assertNull(RamStream.parse(new byte[] { 17, 1 }));                 // the op 17 ack
        assertNull(RamStream.parse(new byte[] { 15, 1 }));
        byte[] b = body(1, 0, ramp(0)); b[0] = 16;
        assertNull(RamStream.parse(b));
        assertNull(RamStream.parse(null));
    }

    @Test public void countsSkippedSequencesAndRepeatedHalfIds() {
        RamStream.Receiver r = new RamStream.Receiver(null);
        r.half(RamStream.parse(body(0, 1, ramp(0))));
        r.half(RamStream.parse(body(1, 0, ramp(0))));
        r.half(RamStream.parse(body(3, 0, ramp(0))));     // 2 skipped: one miss
        r.half(RamStream.parse(body(4, 0, ramp(0))));     // same half id twice in a row: one miss
        r.half(RamStream.parse(body(5, 1, ramp(0))));
        assertEquals(5, r.halves()); assertEquals(2, r.missed());
        assertEquals(5 * 256 / 8000.0, r.seconds(), 1e-9);
    }

    @Test public void wrapsAtSixteenBits() {
        RamStream.Receiver r = new RamStream.Receiver(null);
        r.half(RamStream.parse(body(0xFFFF, 1, ramp(0))));
        r.half(RamStream.parse(body(0, 0, ramp(0))));
        assertEquals(0, r.missed());
    }

    @Test public void identityTapsLeaveTheStreamAlone() {
        double[] w = RamStream.identityTaps();
        assertEquals(HeldPath.TAPS, w.length);
        double[] y = new double[400]; for (int i = 0; i < y.length; i++) y[i] = Math.sin(i * 0.1);
        double[] e = HeldPath.equalise(y, w);
        for (int i = 0; i < y.length; i++) assertEquals(y[i], e[i], 1e-12);
    }

    @Test public void feedsTheReceiverLittleEndian() {
        final byte[][] got = new byte[1][];
        RamStream.Sink sink = (mono, n) -> got[0] = java.util.Arrays.copyOf(mono, n);
        RamStream.Receiver r = new RamStream.Receiver(sink);
        short[] s = ramp(1000); s[0] = (short) 0xBEEF;
        r.half(RamStream.parse(body(0, 0, s)));
        assertEquals(512, got[0].length);
        assertEquals((byte) 0xEF, got[0][0]); assertEquals((byte) 0xBE, got[0][1]);
        assertEquals((byte) (1001 & 0xFF), got[0][2]); assertEquals((byte) (1001 >> 8), got[0][3]);
    }
}
