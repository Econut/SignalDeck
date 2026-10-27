/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Collections;

/** Clean 8 kHz two-level audio of a POCSAG batch, at each standard rate. */
public class PocsagDecoderTest {
    @Test public void eachRateReturnsThePage() {
        Pocsag.Page page = new Pocsag.Page(123456, 3, "HELLO");
        for (int baud : PocsagDecoder.RATES) {
            short[] audio = audio(Pocsag.bits(Collections.singletonList(page)), baud);
            PocsagDecoder.Hit hit = PocsagDecoder.best(audio);
            assertEquals(baud, hit.baud);
            assertEquals(1, hit.pages.size());
            assertEquals(123456, hit.pages.get(0).ric);
            assertEquals(3, hit.pages.get(0).func);
            assertEquals("HELLO", hit.pages.get(0).text);
        }
    }

    @Test public void thirtyPartsPerMillionStillDecodes() {
        Pocsag.Page page = new Pocsag.Page(123456, 3, "HELLO");
        int[] bits = Pocsag.bits(java.util.Collections.singletonList(page));
        double spb = 8000.0 / 1200.0 * (1 + 30e-6);
        int n = (int) Math.ceil(bits.length * spb);
        short[] a = new short[n];
        for (int i = 0; i < n; i++) { int b = Math.min(bits.length - 1, (int) (i / spb)); a[i] = (short) (bits[b] == 1 ? -8000 : 8000); }
        PocsagDecoder.Hit hit = PocsagDecoder.best(a);
        assertEquals(1200, hit.baud);
        assertEquals("HELLO", hit.pages.get(0).text);
    }

    @Test public void oneAndTwoBitErrorsAreRepaired() {
        int cw = Pocsag.addressWord(123456, 3);
        assertEquals(cw, Pocsag.correct(cw ^ (1 << 4)));
        assertEquals(cw, Pocsag.correct(cw ^ (1 << 4) ^ (1 << 17)));
        assertFalse(Pocsag.check(cw ^ (1 << 3) ^ (1 << 8) ^ (1 << 19)));
    }

    /** Bit 1 is the negative level, matching the slicer. */
    static short[] audio(int[] bits, int baud) {
        double spb = 8000.0 / baud;
        int n = (int) Math.ceil(bits.length * spb);
        short[] a = new short[n];
        for (int i = 0; i < n; i++) {
            int b = Math.min(bits.length - 1, (int) (i / spb));
            a[i] = (short) (bits[b] == 1 ? -8000 : 8000);
        }
        return a;
    }
}
