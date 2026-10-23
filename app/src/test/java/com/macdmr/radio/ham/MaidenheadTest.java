/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

public class MaidenheadTest {
    @Test public void gridOfKnownPlaces() {
        assertEquals("FN31pr", Maidenhead.grid(41.7146626, -72.7284655, 6));   // W1AW, as HamDB gives it
        assertEquals("JO01", Maidenhead.grid(51.48, 0.0, 4));                  // Greenwich
        assertEquals("DM79", Maidenhead.grid(39.74, -104.99, 4));
        assertEquals(8, Maidenhead.grid(39.74, -104.99, 8).length());
    }

    @Test public void centreAndBack() {
        double[] c = Maidenhead.centre("FN31pr");
        assertEquals("FN31pr", Maidenhead.grid(c[0], c[1], 6));
        assertEquals("DM79", Maidenhead.grid(Maidenhead.centre("dm79")[0], Maidenhead.centre("dm79")[1], 4));
        assertNull(Maidenhead.centre("ZZ99")); assertNull(Maidenhead.centre("FN"));
    }

    @Test public void distanceAndBearing() {
        double km = Maidenhead.km(41.7146626, -72.7284655, 39.74, -104.99);     // Newington CT to Denver
        assertEquals(2710, km, 40);
        double b = Maidenhead.bearing(41.7146626, -72.7284655, 39.74, -104.99);
        assertTrue("west, was " + b, b > 268 && b < 284);
        assertEquals("NE", Maidenhead.compass(44)); assertEquals("N", Maidenhead.compass(359));
    }

    @Test public void lookupParsing() throws Exception {
        org.json.JSONObject cs = new org.json.JSONObject("{\"call\":\"W1AW\",\"grid\":\"FN31pr\",\"lat\":\"41.71\",\"lon\":\"-72.72\",\"fname\":\"\",\"name\":\"ARRL HQ OPERATORS CLUB\",\"addr2\":\"NEWINGTON\",\"state\":\"CT\",\"country\":\"United States\"}");
        CallLookup.Info i = CallLookup.parse(cs);
        assertEquals("Arrl Hq Operators Club", i.name);
        assertEquals("Newington, CT", i.place());
        assertEquals("FN31pr", i.grid);
    }
}
