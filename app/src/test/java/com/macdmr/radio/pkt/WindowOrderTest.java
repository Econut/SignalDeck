/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Overlapping decode windows: frames come out once each and in air order (the bench case: 5 found before 4). */
public class WindowOrderTest {
    static byte[] f(int n) { return new byte[] { (byte) n, 1, 2, 3 }; }
    static Object[] at(double bits, int n) { return new Object[] { bits, f(n) }; }
    static List<Object[]> list(Object[]... a) { return new ArrayList<>(Arrays.asList(a)); }
    static List<Integer> ids(List<byte[]> l) { List<Integer> r = new ArrayList<>(); for (byte[] b : l) r.add((int) b[0]); return r; }

    @Test public void frameMissedThenCaughtInTheOverlapKeepsItsPlace() {
        WindowOrder o = new WindowOrder();
        // window 1 (3600 bits; the next window starts at 2400): frames 1..3 early, 5 in the overlap; 4 (also in the overlap) missed
        List<byte[]> out = o.add(list(at(100, 1), at(900, 2), at(1700, 3), at(3000, 5)), 2400, true);
        assertEquals(Arrays.asList(1, 2, 3), ids(out));
        // window 2 starts at the old 2400: it catches 4 (at 300) and 5 again (at 600), and a new 6
        out = o.add(list(at(300, 4), at(600, 5), at(1500, 6)), 2400, true);
        assertEquals(Arrays.asList(4, 5, 6), ids(out));
        assertFalse(o.holding());
    }

    @Test public void heldFramesAreReleasedWhenTheAudioStops() {
        WindowOrder o = new WindowOrder();
        assertEquals(Arrays.asList(1), ids(o.add(list(at(100, 1), at(2500, 2)), 2400, true)));
        assertTrue(o.holding());
        assertEquals(Arrays.asList(2), ids(o.flush()));
        // a window without the previous tail (after a gap): what was held is final first
        o.add(list(at(2600, 7)), 2400, true);
        assertEquals(Arrays.asList(7, 8), ids(o.add(list(at(50, 8)), 2400, false)));
    }
}
