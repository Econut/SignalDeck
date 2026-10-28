/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The adb bench driver for Run all checks: which app the watch opens, and the note about a wrong phone clock. */
public class BenchChecksTest {
    private static RadioAppFinder.App app(String pkg, boolean launcher) {
        RadioAppFinder.App a = new RadioAppFinder.App(); a.pkg = pkg; a.label = pkg; a.launcher = launcher; return a;
    }

    @Test public void watchChoice() {
        List<RadioAppFinder.App> ranked = new ArrayList<>();
        ranked.add(app("com.vendor.radioservice", false));        // no launcher icon: the dialog does not offer it either
        ranked.add(app("com.flyscale.analogpoc", true));
        assertEquals("com.flyscale.analogpoc", FirstRunCheck.benchWatch("auto", "", ranked));
        assertEquals("com.flyscale.analogpoc", FirstRunCheck.benchWatch(null, null, ranked));
        assertEquals("com.pri.prizeinterphone", FirstRunCheck.benchWatch("auto", "com.pri.prizeinterphone", ranked));   // a profile's app comes first
        assertEquals("com.other.app", FirstRunCheck.benchWatch("com.other.app", "com.pri.prizeinterphone", ranked));
        assertNull(FirstRunCheck.benchWatch("none", "com.pri.prizeinterphone", ranked));
        assertNull(FirstRunCheck.benchWatch("auto", "", new ArrayList<>()));
    }

    @Test public void clockNote() {
        long phone = 1779705948000L;                               // 2026-05-25 10:45:48 UTC, the W6's clock at its build date
        assertNull(DeviceReport.clockNote(phone, 0));
        assertNull(DeviceReport.clockNote(phone, 90_000));        // under two minutes: the phone's clock is fine
        long off = 155L * 86_400_000L + 3 * 3600_000L + 15 * 60_000L;
        assertEquals("phone 2026-05-25 10:45 UTC, host 2026-10-27 14:00 UTC: the phone is 155 d 3 h 15 min behind (folder and header use the host's time)",
                DeviceReport.clockNote(phone, off));
        assertTrue(DeviceReport.clockNote(phone, -600_000L).contains("10 min ahead"));
    }
}
