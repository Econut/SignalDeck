/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import com.macdmr.radio.ui.ContactEditor;

import org.junit.Test;

import java.util.List;

/** The contact identity fields: RIC parsing, location parsing, and the backup extras row round trip. */
public class ContactIdentityTest {
    @Test public void ricsParseAndPrintCanonically() {
        assertArrayEquals(new int[] { 1234567, 1234568, 42 }, ChannelStore.parseRics("1234567, 1234568;42"));
        assertArrayEquals(new int[] { 7 }, ChannelStore.parseRics(" 7 x 2097152 -1 7 "));   // junk, out of range and repeats dropped
        assertEquals("1234567;42", ChannelStore.ricText(ChannelStore.parseRics("1234567 42")));
        assertEquals(0, ChannelStore.parseRics("").length);
        assertEquals(0, ChannelStore.parseRics(null).length);
    }

    @Test public void locationsParseAsGridOrLatLon() {
        double[] g = ContactEditor.parseLocation("FN31pr");
        assertNotNull(g); assertEquals(41.7, g[0], 0.1); assertEquals(-72.7, g[1], 0.1);
        double[] p = ContactEditor.parseLocation("39.7392, -104.9903");
        assertNotNull(p); assertEquals(39.7392, p[0], 1e-6); assertEquals(-104.9903, p[1], 1e-6);
        assertNull(ContactEditor.parseLocation("somewhere"));
        assertNull(ContactEditor.parseLocation("91, 0"));
        assertNull(ContactEditor.parseLocation(""));
    }

    @Test public void extrasRowCarriesEveryIdentityField() {
        ChannelStore.Contact a = new ChannelStore.Contact();
        a.name = "Test, Station"; a.number = 3100001; a.type = 0; a.callsign = "N0CALL-7"; a.rics = "1234567;1234568";
        a.notes = "Line one\nLine \"two\", with a comma"; a.homeLabel = "Denver"; a.homeLat = 39.7392; a.homeLon = -104.9903;
        assertTrue(Backup.hasExtras(a));
        String row = Backup.contactRow(a);
        List<String> cols = RadioIdStore.split(row);
        assertEquals("3100001", cols.get(0)); assertEquals("contact", cols.get(2)); assertEquals("0", cols.get(6));
        ChannelStore.Contact b = new ChannelStore.Contact(); b.name = a.name; b.number = a.number; b.type = a.type;
        Backup.applyContactRow(b, cols);
        assertEquals("N0CALL-7", b.callsign); assertEquals(2, b.callsignAuto);
        assertEquals("1234567;1234568", b.rics);
        assertEquals(a.notes, b.notes);
        assertEquals("Denver", b.homeLabel); assertEquals(39.7392, b.homeLat, 1e-6); assertEquals(-104.9903, b.homeLon, 1e-6);
        assertTrue(b.hasHome()); assertFalse(b.hasLast());
    }

    @Test public void extrasRowWithoutHomeLeavesHomeEmpty() {
        ChannelStore.Contact a = new ChannelStore.Contact(); a.name = "Pager only"; a.number = 0; a.rics = "99";
        assertTrue(Backup.hasExtras(a));
        ChannelStore.Contact b = new ChannelStore.Contact();
        Backup.applyContactRow(b, RadioIdStore.split(Backup.contactRow(a)));
        assertEquals("99", b.rics); assertEquals("", b.callsign); assertFalse(b.hasHome()); assertEquals("", b.homeLabel); assertEquals(0, b.callsignAuto);
        ChannelStore.Contact plain = new ChannelStore.Contact(); plain.name = "TG"; plain.number = 91; plain.type = 1;
        assertFalse(Backup.hasExtras(plain));
    }
}
