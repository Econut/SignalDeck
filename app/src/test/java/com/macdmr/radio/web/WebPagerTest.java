/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.web;

import static org.junit.Assert.*;

import org.junit.Test;

public class WebPagerTest {
    @Test public void digitsAndAContactBecomeRics() {
        PagerTargets.Book book = name -> {
            if ("n0call".equalsIgnoreCase(name) || "Bench".equalsIgnoreCase(name)) return new int[] { 1001, 1002 };
            if ("empty".equalsIgnoreCase(name)) return new int[0];
            return null;
        };
        assertArrayEquals(new int[] { 1001 }, PagerTargets.resolve("1001", book));
        assertArrayEquals(new int[] { 1001, 1002 }, PagerTargets.resolve("Bench", book));
        assertArrayEquals(new int[] { 1003, 1001, 1002 }, PagerTargets.resolve("1003, N0CALL", book));
    }

    @Test public void aBadTargetIsRefused() {
        PagerTargets.Book book = name -> null;
        try { PagerTargets.resolve("", book); fail(); } catch (IllegalArgumentException expected) { }
        try { PagerTargets.resolve("2097152", book); fail(); } catch (IllegalArgumentException expected) { }
        try { PagerTargets.resolve("nobody", book); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void newRoutesNameTheirRestrictedModeGate() {
        assertEquals("pocsag", WebRoutes.mode("/api/pager/send"));
        assertEquals("pocsag", WebRoutes.mode("/api/pager/monitor"));
        assertEquals("m17", WebRoutes.mode("/api/m17/sms"));
        assertEquals("dmrtext", WebRoutes.mode("/api/messages"));
        assertEquals("report", WebRoutes.admin("/api/device/restart"));
        assertEquals("web", WebRoutes.admin("/api/device/update"));
        assertNull(WebRoutes.mode("/api/pager/pages"));
        assertNull(WebRoutes.admin("/api/device"));
    }
}
