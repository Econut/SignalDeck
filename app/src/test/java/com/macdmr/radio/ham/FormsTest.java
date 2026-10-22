/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class FormsTest {
    private static Forms.Form ics() {
        Forms.Form f = new Forms.Form(); f.kind = Forms.ICS213; f.id = "K7";
        f.f.put("TO", "Shelter manager, Red Cross"); f.f.put("FR", "Net control | EOC"); f.f.put("SU", "Water supply");
        f.f.put("DT", "2026-10-22"); f.f.put("TM", "1430"); f.f.put("MS", "Need 200 gallons of drinking water at the high school shelter by 1800. Back\\door access only."); f.f.put("AP", "J. Smith, EOC");
        return f;
    }
    @Test public void roundTripThroughAprsSizedParts() {
        Forms.Form f = ics();
        List<String> parts = Forms.split(f, 67);
        assertTrue(parts.size() > 1);
        for (String p : parts) { assertTrue(p.length() + " " + p, p.length() <= 67); assertNotNull(p, Forms.part(p)); }
        Collections.reverse(parts);                                           // any order
        Forms.Assembler a = new Forms.Assembler(); Forms.Form got = null;
        for (int i = 0; i < parts.size(); i++) { got = a.add("N0CALL-7", parts.get(i), 1000 + i); if (i < parts.size() - 1) assertNull(got); }
        assertNotNull(got); assertEquals(Forms.ICS213, got.kind); assertEquals("K7", got.id);
        for (String k : f.f.keySet()) assertEquals(k, f.f.get(k), got.f.get(k));      // "|" and "\" survive
    }
    /** By Winlink: one message, readable as text, with a code line SignalDeck turns back into the form (through a real B2 message). */
    @Test public void roundTripThroughWinlink() throws Exception {
        Forms.Form f = ics();
        String[] m = Forms.winlink(f);
        assertEquals("ICS-213: Water supply", m[0]);
        assertTrue(m[1], m[1].startsWith("ICS-213 GENERAL MESSAGE") && m[1].contains("Need 200 gallons"));
        com.macdmr.radio.winlink.B2Message msg = com.macdmr.radio.winlink.B2Message.create("N0CALL", m[0], m[1]); msg.to.add("N0ABC");
        com.macdmr.radio.winlink.B2Message back = com.macdmr.radio.winlink.B2Message.parse(com.macdmr.radio.winlink.Lzhuf.decompressB2(com.macdmr.radio.winlink.Lzhuf.compressB2(msg.toBytes())));
        String line = Forms.winlinkPart(back.bodyText());
        assertNotNull(back.bodyText(), line);
        Forms.Form got = new Forms.Assembler().add("N0CALL", line, 0);
        assertNotNull(got);
        for (String k : f.f.keySet()) assertEquals(k, f.f.get(k), got.f.get(k));
        assertNull(Forms.winlinkPart("Just an ordinary e-mail.\nNothing to see."));
        Forms.Form r = new Forms.Form(); r.kind = Forms.RADIOGRAM; r.id = "AB"; r.f.put("NR", "12"); r.f.put("MS", "ARRIVED SAFELY ALL WELL LOVE AND KISSES TO EVERYONE AT HOME SEE YOU NEXT WEEK");
        String subj = Forms.winlink(r)[0];
        assertTrue(subj, subj.startsWith("Radiogram 12: ARRIVED") && subj.endsWith("...") && subj.length() < 80);
    }
    @Test public void radiogramAndText() {
        Forms.Form r = new Forms.Form(); r.kind = Forms.RADIOGRAM; r.id = "AB"; r.f.put("NR", "12"); r.f.put("PR", "R"); r.f.put("MS", "ARRIVED SAFELY ALL WELL LOVE"); r.f.put("CK", String.valueOf(Forms.words("ARRIVED SAFELY ALL WELL LOVE")));
        assertEquals("5", r.f.get("CK"));
        List<String> p = Forms.split(r, 120); assertEquals(1, p.size()); assertTrue(p.get(0).startsWith("RADIOGRAM AB 1/1 "));
        Forms.Form back = new Forms.Assembler().add("1234567", p.get(0), 0); assertEquals("ARRIVED SAFELY ALL WELL LOVE", back.f.get("MS"));
        String t = Forms.text(back); assertTrue(t, t.startsWith("ARRL RADIOGRAM") && t.contains("NUMBER: 12") && t.contains("TEXT: ARRIVED"));
        assertNull(Forms.part("hello there"));                                 // not a part
        assertNull(new Forms.Assembler().add("X", "ICS213 K7 4/3 x", 0));
    }
}
