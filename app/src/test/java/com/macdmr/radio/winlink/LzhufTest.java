/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.winlink;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * LZHUF against reference files in the B2 form (from the MIT-licensed wl2k-go test data, which other Winlink
 * programs produce byte for byte): our compressor must give identical bytes and our decompressor the original.
 */
public class LzhufTest {
    static byte[] res(String name) throws IOException {
        try (InputStream in = LzhufTest.class.getResourceAsStream("/winlink/" + name)) { assertNotNull(name, in); return in.readAllBytes(); }
    }

    @Test public void matchesReferenceFiles() throws IOException {
        for (String f : new String[] { "gettysburg.txt", "Mark.Twain-Tom.Sawyer.txt" }) {
            byte[] plain = res(f), ref = res(f + ".lzh");
            assertArrayEquals(f + " decompress", plain, Lzhuf.decompressB2(ref));
            byte[] ours = Lzhuf.compressB2(plain);
            assertEquals(f + " compressed size", ref.length, ours.length);
            assertArrayEquals(f + " compress", ref, ours);
        }
    }

    @Test public void roundTrips() throws IOException {
        Random r = new Random(7);
        for (int n : new int[] { 0, 1, 2, 3, 59, 60, 61, 2047, 2048, 5000, 70000 }) {
            byte[] b = new byte[n];
            if (n % 2 == 0) r.nextBytes(b); else for (int i = 0; i < n; i++) b[i] = (byte) "ab c\r\n".charAt(r.nextInt(6));
            assertArrayEquals("n=" + n, b, Lzhuf.decompressB2(Lzhuf.compressB2(b)));
        }
    }

    @Test public void rejectsCorruption() {
        byte[] c = Lzhuf.compressB2("Hello Winlink, hello Winlink, hello.".getBytes(StandardCharsets.US_ASCII));
        c[c.length - 1] ^= 0x10;
        try { Lzhuf.decompressB2(c); fail("corrupt data accepted"); } catch (IOException expected) { }
    }
}
