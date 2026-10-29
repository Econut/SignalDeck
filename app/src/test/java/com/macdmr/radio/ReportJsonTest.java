/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** report.json from a report's text: header, sections with sizes, the draft profile and the summary lines. */
public class ReportJsonTest {
    private static String res(String name) throws Exception {
        return new String(ReportJsonTest.class.getResourceAsStream("/report/" + name).readAllBytes(), "UTF-8");
    }

    @Test public void fromTheAtomXlExcerpt() throws Exception {
        String report = "==== SignalDeck device report 1.0.0-beta.20  Wed Oct 28 15:25:27 CDT 2026 ====\nheader line\n\n" + res("atomxl_excerpt.txt")
                + "\n\n==== Summary ====\nwatched com.agold.intercom start up for 15 s\nradio module answered the version query: yes\ndraft profile (confidence: evidence); nothing here is applied:\n  stockApp     x\n";
        Map<String, String> phone = new LinkedHashMap<>(); phone.put("model", "Atom_XL"); phone.put("android", "11");
        JSONObject o = ReportJson.build(report, phone);
        assertEquals(ReportJson.SCHEMA, o.getInt("schema"));
        assertEquals("1.0.0-beta.20", o.getString("signaldeck"));
        assertEquals("Wed Oct 28 15:25:27 CDT 2026", o.getString("generated"));
        assertEquals("Atom_XL", o.getJSONObject("phone").getString("model"));
        JSONArray s = o.getJSONArray("sections");
        assertEquals("SignalDeck device report 1.0.0-beta.20  Wed Oct 28 15:25:27 CDT 2026", s.getJSONObject(0).getString("title"));
        assertEquals("Summary", s.getJSONObject(s.length() - 1).getString("title"));
        boolean watch = false;
        for (int i = 0; i < s.length(); i++) if (s.getJSONObject(i).getString("title").startsWith("The stock app at work")) { watch = true; assertTrue(s.getJSONObject(i).getInt("lines") > 20); }
        assertTrue(watch);
        JSONObject d = o.getJSONObject("draft");
        assertEquals("/dev/ttyACM0 (USB serial to a bridge MCU)", d.getJSONObject("controlPort").getString("value"));
        assertEquals(DraftProfile.HIGH, d.getJSONObject("controlPort").getString("confidence"));
        JSONArray sum = o.getJSONArray("summary");
        assertEquals(2, sum.length());                                            // the draft block is not repeated in the summary
        assertEquals("radio module answered the version query: yes", sum.getString(1));
    }

    @Test public void anEmptyReportIsStillValid() throws Exception {
        JSONObject o = ReportJson.build("", null);
        assertEquals(0, o.getJSONArray("sections").length());
        assertEquals(0, o.getJSONArray("summary").length());
        assertEquals("unknown", o.getJSONObject("draft").getJSONObject("protocol").getString("value"));
    }
}
