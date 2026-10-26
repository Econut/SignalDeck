/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

/** The cool-down guard's rules, with readings passed in (the bench's numbers of 2026-10-25). */
public class ThermalGuardTest {
    @Test public void aLongKeyDoesNotStartOnAHotPhone() {
        assertNull(ThermalGuard.tooHotToStart(47.9, 36.1));                       // the bench's cool start
        assertNull(ThermalGuard.tooHotToStart(49.9, 41.9));
        assertNotNull(ThermalGuard.tooHotToStart(51.6, 38.0));                    // the Martin M1 that reached 63.2 C started here
        assertNotNull(ThermalGuard.tooHotToStart(45.0, 43.9));                    // the battery after that key
        assertNull(ThermalGuard.tooHotToStart(Double.NaN, Double.NaN));           // unreadable: the timer rule applies instead
        assertTrue(ThermalGuard.tooHotToStart(55.0, 30.0).contains("radio zone"));
        assertTrue(ThermalGuard.tooHotToStart(40.0, 44.0).contains("battery"));
    }

    @Test public void theInKeyStopsAreTheBenchRules() {
        assertNull(ThermalGuard.stopNow(59.9, 44.9, 58.5, 44.0));
        assertNotNull(ThermalGuard.stopNow(60.1, 40.0, Double.NaN, Double.NaN));  // zone over 60
        assertNotNull(ThermalGuard.stopNow(50.0, 45.1, Double.NaN, Double.NaN));  // battery over 45
        assertNotNull(ThermalGuard.stopNow(55.0, 40.0, 52.5, 40.0));              // +2.5 C in 10 s
        assertNull(ThermalGuard.stopNow(55.0, 40.0, 53.5, 40.0));                 // +1.5 C in 10 s is fine
        assertNull(ThermalGuard.stopNow(Double.NaN, Double.NaN, Double.NaN, Double.NaN));
        assertTrue(ThermalGuard.stopNow(63.2, 43.9, 60.0, 43.5).contains("60"));
    }

    @Test public void shortKeysAreNeverHeld() {
        ThermalGuard g = new ThermalGuard();
        assertNull(g.gate(28_500));
        assertNull(g.gate(ThermalGuard.LONG_MS));
    }
}
