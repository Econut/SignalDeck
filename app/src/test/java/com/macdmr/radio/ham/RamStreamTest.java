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
    /** Op 5's reply as FW27 streams it: the samples, then the sequence byte and the half id. */
    private static byte[] body(int seq, int id, short[] samples) {
        byte[] b = new byte[RamStream.BODY];
        for (int i = 0; i < RamStream.SAMPLES; i++) { b[2 * i] = (byte) (samples[i] >> 8); b[2 * i + 1] = (byte) samples[i]; }
        b[512] = (byte) seq; b[513] = (byte) id;
        return b;
    }

    /** The first design's frame: opcode 17, a 16-bit sequence, the half id, then the samples. */
    private static byte[] body17(int seq, int id, short[] samples) {
        byte[] b = new byte[RamStream.BODY_OP17];
        b[0] = RamStream.OP; b[1] = (byte) seq; b[2] = (byte) (seq >> 8); b[3] = (byte) id;
        for (int i = 0; i < RamStream.SAMPLES; i++) { b[4 + 2 * i] = (byte) (samples[i] >> 8); b[5 + 2 * i] = (byte) samples[i]; }
        return b;
    }

    private static short[] ramp(int from) { short[] s = new short[RamStream.SAMPLES]; for (int i = 0; i < s.length; i++) s[i] = (short) (from + i); return s; }

    @Test public void parsesBigEndianSamplesAndHeader() {
        short[] s = ramp(-300);
        RamStream.Half h = RamStream.parse(body(5, 1, s));
        assertNotNull(h);
        assertEquals(5, h.seq); assertEquals(1, h.id);
        assertArrayEquals(s, h.samples);
        RamStream.Half o = RamStream.parse(body17(0x1234, 1, s));
        assertNotNull(o);
        assertEquals(0x1234, o.seq); assertEquals(1, o.id);
        assertArrayEquals(s, o.samples);
    }

    @Test public void otherBodiesAreNotHalves() {
        assertNull(RamStream.parse(new byte[] { 17, 1 }));                 // the op 17 ack
        assertNull(RamStream.parse(new byte[] { 15, 1 }));
        byte[] b = body17(1, 0, ramp(0)); b[0] = 16;
        assertNull(RamStream.parse(b));
        assertNull(RamStream.parse(new byte[513]));
        assertNull(RamStream.parse(null));
    }

    @Test public void countsSkippedSequencesOnly() {
        RamStream.Receiver r = new RamStream.Receiver(null, 8000);
        r.half(RamStream.parse(body(0, 0, ramp(0))));
        r.half(RamStream.parse(body(1, 0, ramp(0))));
        r.half(RamStream.parse(body(3, 0, ramp(0))));     // 2 skipped: one miss
        r.half(RamStream.parse(body(4, 0, ramp(0))));     // the same half id again is not a miss (FW27 reports 0 for every half)
        r.half(RamStream.parse(body(5, 0, ramp(0))));
        assertEquals(5, r.halves()); assertEquals(1, r.missed());
        assertEquals(5 * 256 / 8000.0, r.seconds(), 1e-9);
    }

    /** A real 1 kHz tone sampled at the module's 8103 Hz clock (987.25 Hz if played at 8 kHz) reads 1000 Hz after resampling, with 8000/8103 of the samples. */
    @Test public void resamplesTheModuleClockToEightKilohertz() {
        final java.io.ByteArrayOutputStream got = new java.io.ByteArrayOutputStream();
        RamStream.Receiver r = new RamStream.Receiver((mono, n) -> got.write(mono, 0, n));
        int halves = 400, total = halves * RamStream.SAMPLES;                      // 12.6 s at 8103 Hz
        double f = 1000.0;                                                         // a real 1 kHz tone, sampled on the module's clock (reads 987.25 Hz if played at 8 kHz)
        int seq = 0;
        for (int h = 0; h < halves; h++) {
            short[] s = new short[RamStream.SAMPLES];
            for (int i = 0; i < s.length; i++) s[i] = (short) Math.round(10000 * Math.sin(2 * Math.PI * f * (h * RamStream.SAMPLES + i) / RamStream.STREAM_HZ));
            r.half(RamStream.parse(body(seq, h & 1, s))); seq = (seq + 1) % 6;
        }
        byte[] b = got.toByteArray(); int n = b.length / 2;
        assertEquals(total * (double) RamStream.OUT_HZ / RamStream.STREAM_HZ, n, 3);
        double[] y = new double[n]; for (int i = 0; i < n; i++) y[i] = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8));
        int zc = 0; for (int i = 1; i < n; i++) if ((y[i - 1] < 0) != (y[i] < 0)) zc++;   // the tone from its zero crossings, at 8 kHz
        double hz = zc / 2.0 * RamStream.OUT_HZ / n;
        assertEquals(1000.0, hz, 1.0);
        double maxStep = 10000 * 2 * Math.PI * 1000 / RamStream.OUT_HZ * 1.05 + 2;       // a clean sine across the half seams
        for (int i = 1; i < n; i++) assertTrue("jump at " + i, Math.abs(y[i] - y[i - 1]) <= maxStep);
        assertEquals(total / (double) RamStream.STREAM_HZ, r.seconds(), 1e-6);
    }

    @Test public void opFiveSequenceWrapsAtSix() {
        RamStream.Receiver r = new RamStream.Receiver(null, 8000);
        int id = 1;
        for (int seq = 0; seq < 6; seq++) { r.half(RamStream.parse(body(seq, id, ramp(0)))); id ^= 1; }
        r.half(RamStream.parse(body(0, id, ramp(0))));          // 5 then 0: no miss
        assertEquals(0, r.missed());
        id ^= 1;
        r.half(RamStream.parse(body(2, id, ramp(0))));          // 0 then 2: one missed
        assertEquals(1, r.missed());
    }

    @Test public void widerSequenceWrapsAtItsWidth() {
        RamStream.Receiver r = new RamStream.Receiver(null, 8000);
        r.half(RamStream.parse(body(254, 1, ramp(0))));
        r.half(RamStream.parse(body(255, 0, ramp(0))));
        r.half(RamStream.parse(body(0, 1, ramp(0))));
        assertEquals(0, r.missed());
        RamStream.Receiver w = new RamStream.Receiver(null, 8000);
        w.half(RamStream.parse(body17(0xFFFF, 1, ramp(0))));
        w.half(RamStream.parse(body17(0, 0, ramp(0))));
        assertEquals(0, w.missed());
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
        RamStream.Receiver r = new RamStream.Receiver(sink, 8000);
        short[] s = ramp(1000); s[0] = (short) 0xBEEF;
        r.half(RamStream.parse(body(0, 0, s)));
        assertEquals(512, got[0].length);
        assertEquals((byte) 0xEF, got[0][0]); assertEquals((byte) 0xBE, got[0][1]);
        assertEquals((byte) (1001 & 0xFF), got[0][2]); assertEquals((byte) (1001 >> 8), got[0][3]);
    }
}
