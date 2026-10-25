/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Random;

/**
 * A whole M17 voice transmission as the sender builds it (preamble, link setup frame, one stream frame per LICH
 * index, the end-bit frame, the end-of-transmission marker), taken apart again frame by frame with the project's
 * own decoder: every field round-trips, the six LICH chunks rebuild the link setup frame, and the symbol packing
 * is byte-exact. The "fuller encoder test" owed from the item 2 review.
 */
public class M17TransmissionTest {
    private static float[] f(int[] a) { float[] o = new float[a.length]; for (int i = 0; i < a.length; i++) o[i] = a[i]; return o; }

    @Test public void aWholeTransmissionRoundTripsFrameByFrame() {
        byte[] lsf = M17.lsf("N0CALL", "@ALL", 0x0005, "BENCH".getBytes());
        assertEquals(30, lsf.length);
        assertEquals("CRC over the first 28 bytes", M17.crc16(lsf, 28), ((lsf[28] & 0xFF) << 8) | (lsf[29] & 0xFF));
        // the sender's order: 192 preamble symbols, 192 LSF symbols, then 192 per stream frame, then the EOT marker
        int[] pre = M17.preamble(); assertEquals(192, pre.length);
        for (int i = 0; i < pre.length; i++) assertEquals(i % 2 == 0 ? 3 : -3, pre[i]);
        int[] lsfSym = M17.lsfSymbols(lsf); assertEquals(192, lsfSym.length);
        M17.Lsf l = M17.decodeLsf(f(lsfSym), 8);
        assertTrue(l.crcOk); assertEquals("N0CALL", l.src()); assertEquals("@ALL", l.dst()); assertEquals(0x0005, l.type());
        assertTrue(l.stream()); assertTrue(l.voice()); assertEquals(0, l.encryption()); assertEquals(0, l.can()); assertEquals("BENCH", l.metaText());
        assertArrayEquals(lsf, l.raw);

        Random r = new Random(7);
        byte[] rebuilt = new byte[30]; boolean[] chunk = new boolean[6];
        int frames = 13;                                                    // two full LICH cycles plus the end frame
        for (int fn = 0; fn < frames; fn++) {
            byte[] pay = new byte[16]; r.nextBytes(pay);
            boolean last = fn == frames - 1;
            int frameNo = fn | (last ? 0x8000 : 0);
            int[] sym = M17.streamSymbols(lsf, frameNo, pay, fn % 6);
            assertEquals(192, sym.length);
            for (int k = 0; k < 8; k++) assertEquals("sync symbol " + k, syncSymbols(M17.SYNC_STR)[k], sym[k]);
            M17.Stream s = M17.decodeStream(f(sym), 8);
            assertEquals(frameNo, s.fn); assertEquals(last, s.last()); assertArrayEquals(pay, s.payload);
            assertNotNull(s.lich); assertEquals(0, s.lichErrors); assertEquals(fn % 6, s.lichCount());
            System.arraycopy(s.lich, 0, rebuilt, 5 * s.lichCount(), 5); chunk[s.lichCount()] = true;
            // what a receiver re-encodes from the decoded chunk is the same frame
            assertArrayEquals(sym, M17.streamSymbolsFromLich(s.lich, s.fn, s.payload));
        }
        for (boolean c : chunk) assertTrue(c);
        assertArrayEquals("the six LICH chunks rebuild the link setup frame", lsf, rebuilt);

        int[] eot = M17.eotTail();
        assertEquals("one end-of-transmission marker, 40 ms", 192, eot.length);
        for (int i = 0; i < eot.length; i++) assertEquals(M17.EOT_SYMBOLS[i % 8], eot[i]);
        assertEquals("EOT marker word", 0x555D, M17.EOT);
        byte[] packedEot = M17.packSymbols(eot);
        assertEquals(48, packedEot.length);
        for (int i = 0; i < packedEot.length; i += 2) { assertEquals((byte) 0x55, packedEot[i]); assertEquals((byte) 0x5D, packedEot[i + 1]); }
    }

    @Test public void symbolPackingIsByteExactAndRoundTrips() {
        int[] s = { 3, 1, -1, -3, -3, -1, 1, 3 };
        byte[] p = M17.packSymbols(s);
        assertArrayEquals(new byte[] { 0x4B, (byte) 0xE1 }, p);   // dibits: +3 = 01, +1 = 00, -1 = 10, -3 = 11
        int[] all = new int[192 * 4]; Random r = new Random(3);
        for (int i = 0; i < all.length; i++) all[i] = new int[] { 3, 1, -1, -3 }[r.nextInt(4)];
        byte[] packed = M17.packSymbols(all);
        assertEquals(all.length / 4, packed.length);
        for (int i = 0; i < all.length; i++) {
            int two = (packed[i / 4] >> (6 - 2 * (i % 4))) & 3;
            int sym = two == 1 ? 3 : two == 0 ? 1 : two == 2 ? -1 : -3;
            assertEquals("symbol " + i, all[i], sym);
        }
    }

    @Test public void streamFramesSurviveNoiseAndReportLichErrors() {
        byte[] lsf = M17.lsf("W9XYZ", "N0CALL", 0x0005, null);
        Random r = new Random(11);
        byte[] pay = new byte[16]; r.nextBytes(pay);
        int[] clean = M17.streamSymbols(lsf, 42, pay, 4);
        float[] noisy = f(clean);
        for (int k = 8; k < noisy.length; k++) noisy[k] += (float) (0.4 * r.nextGaussian());   // the same noise the loopback test uses
        M17.Stream s = M17.decodeStream(noisy, 8);
        assertEquals(42, s.fn); assertArrayEquals(pay, s.payload); assertEquals(4, s.lichCount());
        // a wrecked LICH (its 96 bits = 48 symbols after the sync, every one inverted) is reported as absent, the payload still decodes
        float[] badLich = f(clean);
        for (int k = 8; k < 8 + 48; k++) badLich[k] = -badLich[k];
        M17.Stream b = M17.decodeStream(badLich, 8);
        assertEquals(42, b.fn); assertArrayEquals(pay, b.payload); assertNull(b.lich); assertTrue(b.lichErrors > 8);
    }

    /** The eight sync symbols of a 16-bit sync word, most significant pair first. */
    static int[] syncSymbols(int word) {
        int[] o = new int[8];
        for (int i = 0; i < 8; i++) { int two = (word >> (14 - 2 * i)) & 3; o[i] = two == 1 ? 3 : two == 0 ? 1 : two == 2 ? -1 : -3; }
        return o;
    }
}
