/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class BandScopeTest {
    @Test public void stepsRangesAndUnits() {
        assertEquals(161, BandScope.steps(144_000_000, 148_000_000, 25_000));
        assertEquals(0, BandScope.steps(148_000_000, 144_000_000, 25_000));
        assertTrue(BandScope.tunable(146_520_000)); assertFalse(BandScope.tunable(220_000_000)); assertTrue(BandScope.tunable(446_000_000));
        assertEquals(-79.0f, BandScope.dbm(82), 0.01f); assertTrue(Float.isNaN(BandScope.dbm(0))); assertTrue("raw 226 (\"-7 dBm\") is no reading", Float.isNaN(BandScope.dbm(226)));
        for (BandScope.Preset p : BandScope.PRESETS) {                         // every preset is a sweep the radio can do
            assertTrue(p.name, BandScope.tunable(p.from) && BandScope.tunable(p.to));
            int n = BandScope.steps(p.from, p.to, p.step); assertTrue(p.name + " " + n, n >= 2 && n <= BandScope.MAX_STEPS);
        }
    }
    @Test public void peaksStandOutOfTheNoise() {
        float[] s = { -105, -104.5f, -105, -79, -96, -105, Float.NaN, -104, -84, -105 };   // the bench sweep: a carrier, its skirt, one more signal
        List<int[]> p = BandScope.peaks(s, 147_500_000, 25_000, 8);
        assertEquals(2, p.size());
        assertEquals(147_575_000, p.get(0)[0]); assertEquals(-79, p.get(0)[1]);     // the skirt (-96) is not a separate peak
        assertEquals(147_700_000, p.get(1)[0]);
        assertTrue(BandScope.peaks(new float[] { -100, -100.5f, -96.5f, -101 }, 162_400_000, 25_000, 8).isEmpty());
    }
}
