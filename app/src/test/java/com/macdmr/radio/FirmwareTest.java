/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

public class FirmwareTest {
    @Test public void firstReportBecomesStockAndStays() {
        assertEquals("DMR003.TEST.V001", Firmware.stockAfter("", " DMR003.TEST.V001 "));
        assertEquals("DMR003.TEST.V001", Firmware.stockAfter("DMR003.TEST.V001", "DMR003.MOD1.V001"));
        assertEquals("", Firmware.stockAfter("", ""));
    }

    @Test public void currentShownOnlyWhenItDiffers() {
        assertEquals("", Firmware.line("", "DMR003.TEST.V001"));                       // nothing reported yet
        assertEquals("firmware DMR003.TEST.V001 (stock)", Firmware.line("DMR003.TEST.V001", "DMR003.TEST.V001"));
        assertEquals("firmware DMR003.TEST.V001 (stock)", Firmware.line("DMR003.TEST.V001", ""));   // module not answering: keep the stock line
        assertEquals("firmware DMR003.MOD1.V001\nstock DMR003.TEST.V001", Firmware.line("DMR003.TEST.V001", "DMR003.MOD1.V001"));
        assertTrue(Firmware.differs("DMR003.TEST.V001", "DMR003.MOD1.V001"));
        assertFalse(Firmware.differs("DMR003.TEST.V001", ""));
    }
}
