/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

public class SoftSqScanTest {
    @Test public void scanGivesEveryAnalogChannelSoftSq() {
        assertTrue(RadioService.softSqApplies(false, false, true, true));    // Soft SQ only because of the scan: the slider must keep it on
        assertTrue(RadioService.softSqApplies(false, true, false, true));    // the channel's own switch
        assertTrue(RadioService.softSqApplies(false, true, true, false));
        assertFalse(RadioService.softSqApplies(false, false, true, false));  // scan setting off: the radio's own squelch
        assertFalse(RadioService.softSqApplies(false, false, false, true));  // not scanning
        assertFalse(RadioService.softSqApplies(true, true, true, true));     // never on a digital channel
    }

    @Test public void newChannelsStartAtLevelFive() {
        assertEquals(5, new ChannelStore.Channel().softSqLevel);             // the help says so
    }
}
