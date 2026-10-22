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

public class FmSatsTest {
    private static JSONObject sat(int norad, String name, String names, String status) throws Exception { return new JSONObject().put("norad_cat_id", norad).put("name", name).put("names", names).put("status", status); }
    private static JSONObject tx(int norad, String type, String mode, long up, long down, String desc) throws Exception { return new JSONObject().put("norad_cat_id", norad).put("type", type).put("mode", mode).put("uplink_low", up).put("downlink_low", down).put("description", desc).put("alive", true); }

    @Test public void picksTheFmVoiceTransmitterThisRadioCanWork() throws Exception {
        JSONArray sats = new JSONArray().put(sat(1, "SAT ONE", "AO-1, ONESAT", "in orbit")).put(sat(2, "TWOSAT", "", "in orbit")).put(sat(3, "DEADSAT", "AO-3", "re-entered")).put(sat(4, "FOURSAT", "XO-4", "in orbit"));
        JSONArray txs = new JSONArray()
            .put(tx(1, "Transmitter", "FM", 0, 437_000_000, "FM beacon"))
            .put(tx(1, "Transceiver", "FM", 145_850_000, 435_400_000, "Mode V/U FM Voice (PL 88.5Hz)"))
            .put(tx(1, "Transponder", "USB", 145_900_000, 435_500_000, "Linear transponder"))
            .put(tx(2, "Transceiver", "FM", 145_970_000, 436_400_000, "Mode V/U - FM Transponder"))
            .put(tx(3, "Transceiver", "FM", 145_850_000, 436_795_000, "V/U FM"))
            .put(tx(4, "Transceiver", "FM", 1_268_000_000L, 2_400_000_000L, "L/S FM"));      // outside this radio's bands
        List<FmSats.Entry> e = FmSats.pick(Arrays.asList("AO-1", "TWOSAT", "AO-3", "XO-4", "NOPE"), sats, txs);
        assertEquals(2, e.size());
        assertEquals("AO-1", e.get(0).name); assertEquals(435_400_000, e.get(0).downHz); assertEquals(145_850_000, e.get(0).upHz); assertEquals(88.5, e.get(0).toneHz, 0.01);
        assertEquals("TWOSAT", e.get(1).name); assertEquals(0, e.get(1).toneHz, 0.01);
    }
    @Test public void tonesFromDescriptions() {
        assertEquals(67.0, FmSats.tone("Mode V/U FM Voice CTCSS 67.0 Hz"), 0.01);
        assertEquals(67.0, FmSats.tone("Mode V/U FM 67.0 PL"), 0.01);
        assertEquals(88.5, FmSats.tone("V/U FM Voice (PL 88.5Hz)"), 0.01);
        assertEquals(0, FmSats.tone("Mode U/V FM Voice (no CTCSS any longer)"), 0.01);
        assertEquals(0, FmSats.tone("FM VOICE"), 0.01);
    }
    @Test public void amsatListAndReports() throws Exception {
        String html = "<option value=\"AO-123_[FM]\">AO-123 [FM]</option><option value=\"AO-123_[SSDV]\">x</option><option value=\"TEVEL2-1_[FM]\">y</option><option value=\"QO-100_[NB]\">z</option>";
        assertEquals(Arrays.asList("AO-123", "TEVEL2-1"), FmSats.amsatFmNames(html));
        JSONArray r = new JSONArray()
            .put(new JSONObject().put("report", "Heard").put("reported_time", "2026-10-22T15:30:00Z"))
            .put(new JSONObject().put("report", "Not Heard").put("reported_time", "2026-10-22T16:30:00Z"))
            .put(new JSONObject().put("report", "Telemetry Only").put("reported_time", "2026-10-21T10:00:00Z"));
        FmSats.Status s = FmSats.parseStatus(r);
        assertEquals(1, s.heard); assertEquals(1, s.notHeard); assertEquals(1, s.telemetry);
        String d = FmSats.describe(s, s.lastHeard + 3 * 3_600_000L);
        assertTrue(d, d.contains("heard 1") && d.contains("not heard 1") && d.contains("3 h ago"));
        assertTrue(FmSats.describe(FmSats.parseStatus(new JSONArray()), 0).contains("no reports"));
    }
}
