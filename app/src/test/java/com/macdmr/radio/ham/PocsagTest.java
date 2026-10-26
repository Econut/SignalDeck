/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PocsagTest {
    @Test public void one_page_round_trips() {
        Pocsag.Page p = new Pocsag.Page(1234567, 3, "HI");
        int[] bits = Pocsag.bits(Arrays.asList(p));
        List<Pocsag.Page> back = Pocsag.decode(bits);
        assertEquals(1, back.size());
        assertEquals(1234567, back.get(0).ric);
        assertEquals(3, back.get(0).func);
        assertEquals("HI", back.get(0).text);
        assertTrue(Pocsag.check(Pocsag.addressWord(1234567, 3)));
    }

    @Test public void three_rics_in_one_transmission() {
        List<Pocsag.Page> pages = Arrays.asList(
                new Pocsag.Page(1234567, 3, "ONE"),
                new Pocsag.Page(200001, 3, "TWO"),
                new Pocsag.Page(777777, 3, "THREE"));
        List<Pocsag.Page> back = Pocsag.decode(Pocsag.bits(pages));
        assertEquals(3, back.size());
        assertEquals("ONE", back.get(0).text);
        assertEquals(200001, back.get(1).ric);
        assertEquals("TWO", back.get(1).text);
        assertEquals(777777, back.get(2).ric);
        assertEquals("THREE", back.get(2).text);
    }

    @Test public void address_sits_in_its_frame() {
        List<Integer> words = Pocsag.words(Arrays.asList(new Pocsag.Page(8, 0, "X")));   // frame 0
        assertEquals(Pocsag.addressWord(8, 0), (int) words.get(0));
        words = Pocsag.words(Arrays.asList(new Pocsag.Page(9, 0, "X")));                // frame 1
        assertEquals(Pocsag.IDLE, (int) words.get(0));
        assertEquals(Pocsag.IDLE, (int) words.get(1));
        assertEquals(Pocsag.addressWord(9, 0), (int) words.get(2));
    }

    @Test public void sender_tag_and_waveform() {
        assertEquals("N0CALL: hello", Pocsag.sign("n0call-7", "hello"));
        assertEquals("N0CALL", Pocsag.sender("N0CALL: hello"));
        assertEquals("hello", Pocsag.body("N0CALL: hello"));
        assertNull(Pocsag.sender("hello"));
        assertEquals(11, Pocsag.waveform(1200).length);
        assertEquals(8, Pocsag.waveform(1200)[0]);
        assertEquals(3, Pocsag.waveform(1200)[3] & 0xFF);          // 3/20
        assertEquals(20, Pocsag.waveform(1200)[5] & 0xFF);
        assertEquals(8, Pocsag.waveform(512)[3] & 0xFF);           // 8/125
        assertEquals(125, Pocsag.waveform(512)[5] & 0xFF);
        assertEquals(3, Pocsag.waveform(2400)[3] & 0xFF);          // 3/10
        assertEquals(10, Pocsag.waveform(2400)[5] & 0xFF);
        byte[] packed = Pocsag.pack(new int[] { 1, 0, 1, 1 });
        assertEquals(1, packed.length);
        assertEquals((byte) 0x45, packed[0]);                      // 1,0,1,1 in bits 7:6, 5:4, 3:2, 1:0 
    }
}
