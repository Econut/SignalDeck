/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

public class ConditionsTest {
    @Test public void refractivityOfStandardAir() {
        double n = Conditions.refractivity(15, 50, 1013.25);
        assertTrue("N " + n, n > 305 && n < 318);
        assertTrue(Conditions.refractivity(25, 90, 1013) > Conditions.refractivity(25, 20, 1013));     // humid air bends more
        assertEquals(Conditions.NORMAL, Conditions.classify(-40));
        assertEquals(Conditions.SUPER, Conditions.classify(-100));
        assertEquals(Conditions.DUCT, Conditions.classify(-200));
    }
    private static JSONObject hour(double t2, double rh2, double ps, double[][] levels) throws Exception {   // levels: {p, t, rh, h}
        JSONObject h = new JSONObject().put("time", new JSONArray().put("2026-10-22T03:00")).put("temperature_2m", new JSONArray().put(t2)).put("relative_humidity_2m", new JSONArray().put(rh2)).put("surface_pressure", new JSONArray().put(ps));
        for (double[] l : levels) { int p = (int) l[0]; h.put("temperature_" + p + "hPa", new JSONArray().put(l[1])).put("relative_humidity_" + p + "hPa", new JSONArray().put(l[2])).put("geopotential_height_" + p + "hPa", new JSONArray().put(l[3])); }
        return h;
    }
    @Test public void anInversionWithDryAirAboveIsADuct() throws Exception {
        // cool humid air at the ground under warm dry air 300 m up: the classic radiation-inversion duct
        List<Conditions.Slot> s = Conditions.tropo(hour(12, 95, 985, new double[][] { { 1000, 0, 0, 0 }, { 950, 18, 25, 600 }, { 925, 16, 25, 830 } }), 300);
        assertEquals(1, s.size()); assertEquals(Conditions.DUCT, s.get(0).cls);
        // a well-mixed afternoon: about −40 per km
        List<Conditions.Slot> m = Conditions.tropo(hour(20, 50, 985, new double[][] { { 950, 16.5, 48, 610 }, { 925, 14.5, 46, 840 } }), 300);
        assertEquals(Conditions.NORMAL, m.get(0).cls);
    }
    @Test public void showersAndKp() {
        long oct20 = 1792454400000L;                                                   // 2026-10-20 00:00 UTC
        List<Conditions.Shower> l = Conditions.showers(oct20, 45, TimeZone.getTimeZone("UTC"));
        assertEquals("Orionids", l.get(0).name); assertEquals("Leonids", l.get(1).name);
        assertEquals("quiet", Conditions.kpWords(2)); assertEquals("storm", Conditions.kpWords(5.3));
        JSONArray grid = new JSONArray().put(new JSONArray().put(357).put(52).put(7)).put(new JSONArray().put(358).put(52).put(9));
        assertEquals(7, Conditions.ovationAt(grid, 51.8, -2.6));                        // -2.6° E = 357.4°
    }
}
