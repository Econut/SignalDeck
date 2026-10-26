/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The 0x2B caller-info body: the stock nine bytes, and the two the promiscuous firmware appends. */
public class CallerInfoTest {
    @Test public void stockNineBytes() {
        byte[] b = { 1, (byte) 0x15, (byte) 0xCD, (byte) 0x5B, 0, 9, 0, 0, 0 };     // group call from 6016277 to TG 9
        int[] ci = RadioEngine.parseCallerInfo(b);
        assertNotNull(ci);
        assertEquals(1, ci[0]); assertEquals(6016277, ci[1]); assertEquals(9, ci[2]);
        assertEquals(-1, ci[3]); assertEquals(-1, ci[4]);
    }

    @Test public void promiscuousElevenBytes() {
        byte[] b = { 0, (byte) 0x15, (byte) 0xCD, (byte) 0x5B, 0, (byte) 0x2A, (byte) 0x31, (byte) 0x2E, 0, 7, 1 };   // private call, CC 7, slot 2
        int[] ci = RadioEngine.parseCallerInfo(b);
        assertNotNull(ci);
        assertEquals(0, ci[0]); assertEquals(6016277, ci[1]); assertEquals(3027242, ci[2]);
        assertEquals(7, ci[3]); assertEquals(1, ci[4]);
    }

    @Test public void shortBodyIsNull() {
        assertNull(RadioEngine.parseCallerInfo(new byte[8]));
        assertNull(RadioEngine.parseCallerInfo(null));
    }

    @Test public void onlyLowBitsOfTheAppendedBytes() {
        byte[] b = new byte[11]; b[1] = 1; b[9] = (byte) 0xF3; b[10] = (byte) 0x02;
        int[] ci = RadioEngine.parseCallerInfo(b);
        assertEquals(3, ci[3]); assertEquals(0, ci[4]);
    }
}
