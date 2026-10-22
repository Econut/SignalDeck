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

import java.util.Arrays;
import java.util.List;

public class PotaTest {
    @Test public void references() {
        assertEquals(Pota.POTA, Pota.programOf("GB-0001")); assertEquals(Pota.POTA, Pota.programOf("us-10234"));
        assertEquals(Pota.SOTA, Pota.programOf("G/SE-001")); assertEquals(Pota.SOTA, Pota.programOf("W0C/FR-001"));
        assertNull(Pota.programOf("park")); assertNull(Pota.programOf("GB-01"));
        assertEquals(10, Pota.needed(Pota.POTA)); assertEquals(4, Pota.needed(Pota.SOTA));
        assertEquals("20261022", Pota.utcDay(1792700000000L));
    }
    @Test public void spotsInBothFormats() throws Exception {
        JSONArray pota = new JSONArray()
            .put(new JSONObject().put("activator", "N0CALL").put("frequency", "146520").put("mode", "FM").put("reference", "GB-0001").put("name", "Test Park").put("spotTime", "2026-10-22T18:00:00"))
            .put(new JSONObject().put("activator", "N1CALL").put("frequency", "14074").put("mode", "FT8").put("reference", "GB-0002").put("spotTime", "2026-10-22T18:05:00"))
            .put(new JSONObject().put("activator", "N2CALL").put("frequency", "446100").put("mode", "SSB").put("reference", "GB-0003").put("spotTime", "2026-10-22T18:06:00"));
        List<Pota.Spot> p = Pota.parsePota(pota);
        assertEquals(3, p.size()); assertEquals(146_520_000L, p.get(0).freqHz);
        assertTrue(p.get(0).workable()); assertFalse(p.get(1).workable()); assertFalse("SSB on 70 cm: not with this FM radio", p.get(2).workable());
        JSONArray sota = new JSONArray().put(new JSONObject().put("activatorCallsign", "N3CALL").put("frequency", "145.500").put("mode", "fm").put("associationCode", "G").put("summitCode", "SE-001").put("summitDetails", "Test Hill").put("timeStamp", "2026-10-22T18:10:00.000"));
        List<Pota.Spot> s = Pota.parseSota(sota);
        assertEquals(145_500_000L, s.get(0).freqHz); assertEquals("G/SE-001", s.get(0).ref); assertTrue(s.get(0).workable());
    }
    @Test public void adifCarriesTheReferences() {
        Logbook.Entry e = new Logbook.Entry(); e.ts = 1792700000000L; e.call = "N0CALL"; e.freqHz = 146_520_000; e.qso = true; e.mySig = "POTA"; e.mySigInfo = "GB-0001"; e.sig = "SOTA"; e.sigInfo = "G/SE-001";
        String a = Logbook.adif(Arrays.asList(e), "test", "N9TEST");
        for (String f : new String[] { "<MY_SIG:4>POTA", "<MY_SIG_INFO:7>GB-0001", "<MY_POTA_REF:7>GB-0001", "<SIG:4>SOTA", "<SOTA_REF:8>G/SE-001", "<STATION_CALLSIGN:6>N9TEST", "<BAND:2>2m" }) assertTrue(f, a.contains(f));
    }
}
