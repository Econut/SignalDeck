/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HideCallTest {
    @Test public void theOwnersCallNeverReachesTheBrowser() {
        assertEquals("CQ (you) DM79", WebHost.hideCall("CQ W9XYZ DM79", "W9XYZ"));
        assertEquals("K1ABC (you) R-07", WebHost.hideCall("K1ABC W9XYZ R-07", "W9XYZ"));
        assertEquals("(you) K1ABC RR73", WebHost.hideCall("<W9XYZ> K1ABC RR73", "W9XYZ"));
        assertEquals("CQ (you)", WebHost.hideCall("CQ W9XYZ/P", "W9XYZ"));
        assertEquals("221500  1500  CQ (you) DM79", WebHost.hideCall("221500  1500  CQ W9XYZ DM79", "W9XYZ"));
    }

    @Test public void otherCallsAndTextStay() {
        assertEquals("CQ AW9XYZ FN42", WebHost.hideCall("CQ AW9XYZ FN42", "W9XYZ"));
        assertEquals("CQ W9XYZA FN42", WebHost.hideCall("CQ W9XYZA FN42", "W9XYZ"));
        assertEquals("CQ K1ABC FN42", WebHost.hideCall("CQ K1ABC FN42", null));
    }
}
