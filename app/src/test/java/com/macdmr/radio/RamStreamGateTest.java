/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The receive-RAM stream exists only on the bench images FW27 to FW29. */
public class RamStreamGateTest {
    @Test public void onlyFw27ToFw29() {
        assertFalse(RadioService.ramStreamFirmware(26));
        assertTrue(RadioService.ramStreamFirmware(27));
        assertTrue(RadioService.ramStreamFirmware(29));
        assertFalse("FW30 dropped ops 16/17", RadioService.ramStreamFirmware(30));
        assertFalse(RadioService.ramStreamFirmware(31));
    }
}
