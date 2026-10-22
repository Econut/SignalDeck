/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.winlink;

import static org.junit.Assert.*;

import org.junit.Test;

/** The winlink.org gateway CSV (made-up rows in its exact layout). */
public class RmsListTest {
    static final String CSV = "\uFEFFTimestamp,Callsign,BaseCallsign,GridSquare,Frequency,Mode,Hours,Sysop,QTH\r\n"
            + "10/22/2026 7:50:00 PM,G0AAA-10,G0AAA,IO91wm,144.9300 MHz,Packet 1200,00-23,- ,\"LONDON, -, United Kingdom\"\r\n"
            + "10/22/2026 7:50:00 PM,G0BBB-10,G0BBB,IO92ja,145.0700 MHz,Packet 1200,06-22,- ,\"OXFORD, -, United Kingdom\"\r\n"
            + "10/22/2026 7:50:00 PM,G0CCC-10,G0CCC,IO83qm,144.9500 MHz,Packet 9600,00-23,- ,\"MANCHESTER, -, United Kingdom\"\r\n"
            + "10/22/2026 7:50:00 PM,G0DDD,G0DDD,IO91wm,\"3,585.000 KHz\",\"Pactor 3,4\",00-23,- ,\"LONDON, -, United Kingdom\"\r\n"
            + "10/22/2026 7:50:00 PM,G0EEE-10,G0EEE,IO91wm,223.4000 MHz,Packet 1200,00-23,- ,\"LONDON, -, United Kingdom\"\r\n"
            + "10/22/2026 7:50:00 PM,G0FFF-10,G0FFF,IO93fn,441.0500 MHz,Packet 1200,INVALID,- ,\"YORK, -, United Kingdom\"\r\n";

    @Test public void keepsOnlyWhatThisRadioCanUse() {
        RmsList.Result r = RmsList.parse(CSV);
        assertEquals(6, r.rows); assertEquals(3, r.usable.size()); assertEquals(1, r.hf); assertEquals(1, r.otherBaud); assertEquals(1, r.outOfRange);
        assertEquals(144_930_000, r.usable.get(0).hz); assertEquals("G0AAA-10", r.usable.get(0).call); assertEquals("LONDON, -, United Kingdom", r.usable.get(0).qth);
        assertEquals(441_050_000, r.usable.get(2).hz);
    }

    @Test public void nearestFirst() {
        java.util.List<RmsList.Gateway> l = RmsList.parse(CSV).usable;
        RmsList.rank(l, 51.75, -1.26);                                        // Oxford
        assertEquals("G0BBB-10", l.get(0).call); assertEquals("G0AAA-10", l.get(1).call); assertEquals("G0FFF-10", l.get(2).call);
        assertTrue(l.get(1).km > 60 && l.get(1).km < 110);
        assertEquals(RmsList.fromJson(RmsList.toJson(l)).get(0).call, "G0BBB-10");
    }

    @Test public void frequencies() {
        assertEquals(3_585_000L, RmsList.freqHz("3,585.000 KHz"));
        assertEquals(145_050_000L, RmsList.freqHz("145.0500 MHz"));
        assertEquals(0L, RmsList.freqHz("n/a"));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsOtherFiles() { RmsList.parse("Name,Phone\r\nBob,123\r\n"); }
}
