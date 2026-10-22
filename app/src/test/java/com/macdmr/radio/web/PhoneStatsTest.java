/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.web;

import static org.junit.Assert.*;

import org.junit.Test;

public class PhoneStatsTest {
    @Test public void rateFromMinuteSamples() {
        long[] t = new long[31]; double[] v = new double[31]; long now = 10_000_000L;
        for (int i = 0; i < 31; i++) { t[i] = now - (30 - i) * 60_000L; v[i] = 80 - i * 0.2; }   // -0.2 % a minute = -12 %/h
        assertEquals(-12.0, PhoneStats.ratePerHour(t, v, 31, now), 0.01);
        assertEquals(400, PhoneStats.minutesLeft(80, -12, false));                              // 80 % at 12 %/h
        assertEquals(100, PhoneStats.minutesLeft(80, 12, true));
        assertEquals(-1, PhoneStats.minutesLeft(80, 12, false));
    }
    @Test public void tooShortOrOldIsUnknown() {
        long now = 10_000_000L;
        assertTrue(Double.isNaN(PhoneStats.ratePerHour(new long[] { now - 120_000, now }, new double[] { 50, 49 }, 2, now)));     // 2 min span
        assertTrue(Double.isNaN(PhoneStats.ratePerHour(new long[] { now - 3_600_000, now - 3_000_000 }, new double[] { 50, 49 }, 2, now)));  // older than 30 min
    }
    @Test public void currentUnitsAndSign() {
        assertEquals(1500, PhoneStats.currentMa(1_500_000, true));      // µA
        assertEquals(-420, PhoneStats.currentMa(420_000, false));
        assertEquals(-420, PhoneStats.currentMa(-420, false));          // already mA
        assertEquals(Integer.MIN_VALUE, PhoneStats.currentMa(0, true));
    }
}
