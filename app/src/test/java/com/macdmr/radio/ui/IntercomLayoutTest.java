/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import static org.junit.Assert.*;

import org.junit.Test;

/** The compact intercom: which phones get it, and that the regular page keeps the sizes it had before. */
public class IntercomLayoutTest {
    @Test public void whichPhonesAreCompact() {
        assertTrue(IntercomView.compactFor(569, "auto"));       // ANYSECU W6: 480 x 854 at 240 dpi
        assertFalse(IntercomView.compactFor(800, "auto"));      // Armor 26 Ultra
        assertFalse(IntercomView.compactFor(700, "auto"));
        assertTrue(IntercomView.compactFor(699, "auto"));
        assertFalse(IntercomView.compactFor(0, "auto"));        // unknown height: keep the regular page
        assertTrue(IntercomView.compactFor(800, "compact"));    // the owner's choice wins both ways
        assertFalse(IntercomView.compactFor(569, "regular"));
    }

    /** The Armor's page: these are the numbers the build code used before the size table existed. Changing one changes the Armor. */
    @Test public void regularSizesAreThePageAsItWas() {
        IntercomView.Size r = IntercomView.REGULAR;
        assertFalse(r.compact);
        assertEquals(16, r.side); assertEquals(60, r.headerMin); assertEquals(28, r.logo); assertEquals(44, r.menu);
        assertEquals(50, r.arrow); assertEquals(13, r.arrowPad); assertEquals(18, r.arrowSide); assertEquals(50f, r.numSp, 0);
        assertEquals(15.5f, r.valueSp, 0); assertEquals(22, r.valueH); assertEquals(17f, r.meterSp, 0); assertEquals(12.5f, r.meterQuietSp, 0);
        assertEquals(15.5f, r.scanSp, 0); assertEquals(44, r.scanMinH); assertEquals(14, r.sqTop); assertEquals(32, r.sqH);
        assertEquals(96, r.capMax); assertEquals(22, r.capBottom); assertEquals(17f, r.headSp, 0); assertEquals(12.5f, r.histSp, 0);
        assertEquals(182, r.bottomH); assertEquals(0, r.ptt); assertEquals(76, r.satW); assertEquals(54, r.satH); assertEquals(12, r.satSide); assertEquals(59, r.satStep);
        assertFalse(r.chipsBeside);
    }

    @Test public void compactIsTheSamePageSmaller() {
        IntercomView.Size r = IntercomView.REGULAR, k = IntercomView.COMPACT;
        assertTrue(k.compact && k.chipsBeside);
        assertTrue(k.headerMin < r.headerMin && k.arrow < r.arrow && k.numSp < r.numSp && k.valueSp < r.valueSp && k.bottomH < r.bottomH);
        // the six keys in three rows still fit beside a 132 dp talk key on a 320 dp screen, and in their 150 dp block
        assertTrue(2 * (k.satSide + k.satW) + k.ptt <= 320);
        assertTrue(k.satTop0 + 2 * k.satStep + k.satH <= k.bottomH);
        assertTrue(k.ptt <= k.bottomH);
    }
}
