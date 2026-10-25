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

/** The tone-queue bodies against the FW21 stub's layout, and the Morse, RTTY and FT8 pair lists against their timings. */
public class ToneQueueTest {
    @Test public void bodyLayoutMatchesTheStub() {
        List<ToneQueue.Pair> p = java.util.Arrays.asList(ToneQueue.Pair.hz(1000, 400), ToneQueue.Pair.silence(200));
        byte[] b = ToneQueue.body(true, 4000, p);
        assertEquals(3 + 4 * 2, b.length);                                     // opcode, int16 amplitude, 4 bytes per pair: (len - 3) % 4 == 0 as the stub checks
        assertEquals(11, b[0]);
        assertEquals(4000, (b[1] & 0xFF) | ((b[2] & 0xFF) << 8));
        assertEquals(8000, (b[3] & 0xFF) | ((b[4] & 0xFF) << 8));                // 1000 Hz in eighths
        assertEquals(400, (b[5] & 0xFF) | ((b[6] & 0xFF) << 8));
        assertEquals(0, (b[7] & 0xFF) | ((b[8] & 0xFF) << 8)); assertEquals(200, (b[9] & 0xFF) | ((b[10] & 0xFF) << 8));
        assertEquals(10, ToneQueue.body(false, 4000, p)[0]);
        byte[] clamped = ToneQueue.body(true, 30000, p);
        assertEquals(10000, (short) ((clamped[1] & 0xFF) | ((clamped[2] & 0xFF) << 8)));
    }

    @Test public void bodiesAreCutAtThirtyPairsWithOpElevenLast() {
        List<ToneQueue.Pair> p = new java.util.ArrayList<>();
        for (int i = 0; i < 79; i++) p.add(ToneQueue.Pair.hz(1000 + 6.25 * (i % 8), 160));
        List<byte[]> bodies = ToneQueue.bodies(4000, p);
        assertEquals(3, bodies.size());
        assertEquals(10, bodies.get(0)[0]); assertEquals(10, bodies.get(1)[0]); assertEquals(11, bodies.get(2)[0]);
        assertEquals(3 + 4 * 30, bodies.get(0).length); assertEquals(3 + 4 * 19, bodies.get(2).length);
    }

    @Test public void morseTimingIsTheStandardOne() {
        List<ToneQueue.Pair> p = ToneQueue.morse("HI", 20, 700);             // dit 60 ms: H = 4 dits with 3 gaps, letter gap, I = 2 dits with 1 gap
        assertEquals(ToneQueue.LEAD_MS, p.get(0).ms); assertEquals(0, p.get(0).eighths);
        int tone = 0, gaps = 0;
        for (ToneQueue.Pair q : p) { if (q.eighths == 5600) tone += q.ms; else gaps += q.ms; }
        assertEquals(6 * 60, tone);                                           // six dits of tone
        assertEquals(ToneQueue.LEAD_MS + 3 * 60 + 3 * 60 + 60 + 60, gaps);    // lead, three gaps in H, the letter gap, one gap in I, the closing dit
        assertEquals(com.macdmr.radio.coverage.Morse.units("HI") * 60 + ToneQueue.LEAD_MS + 60, ToneQueue.durationMs(p));
        List<ToneQueue.Pair> dah = ToneQueue.morse("T", 20, 700);
        assertEquals(180, dah.get(1).ms);
    }

    @Test public void rttyFramesEachCharacterWithStartFiveBitsAndStop() {
        List<ToneQueue.Pair> p = ToneQueue.rtty("HI", 1000, 800);
        assertEquals(0, p.get(0).eighths);                                     // lead silence
        assertEquals(8000, p.get(1).eighths);                                  // the mark idle, merged with nothing before it
        assertTrue(p.get(1).ms >= 800);
        // LTRS, H, I, LTRS: four characters of 22 × 6 + 33 ms, plus 800 ms idle, 300 ms tail, the lead
        int total = ToneQueue.durationMs(p);
        assertEquals(ToneQueue.LEAD_MS + 800 + 4 * (6 * 22 + 33) + 300, total);
        for (ToneQueue.Pair q : p) assertTrue(q.toString(), q.eighths == 0 || q.eighths == 8000 || q.eighths == 9360);   // mark 1000, space 1170
        // the first start bit after the idle is space
        assertEquals(9360, p.get(2).eighths); assertEquals(22, p.get(2).ms);
    }

    @Test public void ft8IsSeventyNineTonesAtSixAndAQuarterHertz() {
        int[] tones = new int[79]; for (int i = 0; i < 79; i++) tones[i] = i % 8;
        List<ToneQueue.Pair> p = ToneQueue.ft8(tones, 1000, 500);
        assertEquals(500, p.get(0).ms); assertEquals(0, p.get(0).eighths);
        assertEquals(80, p.size());                                            // lead + 79 tones (no two neighbours equal in this pattern)
        for (int i = 0; i < 79; i++) { assertEquals(160, p.get(i + 1).ms); assertEquals(8000 + 50 * (i % 8), p.get(i + 1).eighths); }
        assertEquals(500 + 79 * 160, ToneQueue.durationMs(p));
        List<ToneQueue.Pair> real = ToneQueue.ft8(Ft8.tones(Ft8Message.pack("CQ K1ABC FN42", null)), 1000, 500);
        assertEquals(79 * 160 + 500, ToneQueue.durationMs(real));
        assertEquals(8000 + 50 * 3, real.get(1).eighths);                      // the Costas array starts with tone 3
    }

    @Test public void neighbouringEqualTonesMerge() {
        List<ToneQueue.Pair> m = ToneQueue.merged(java.util.Arrays.asList(ToneQueue.Pair.hz(1000, 22), ToneQueue.Pair.hz(1000, 22), ToneQueue.Pair.hz(1170, 22), ToneQueue.Pair.hz(1000, 33)));
        assertEquals(3, m.size()); assertEquals(44, m.get(0).ms); assertEquals(33, m.get(2).ms);
    }
}
