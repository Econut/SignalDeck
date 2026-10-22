/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class SchedulerTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("America/Chicago");
    private static long at(int y, int mo, int d, int h, int mi) { Calendar c = Calendar.getInstance(TZ); c.clear(); c.set(y, mo - 1, d, h, mi, 0); return c.getTimeInMillis(); }
    private static Scheduler.Entry weekly(int days, int h, int m) { Scheduler.Entry e = new Scheduler.Entry(); e.days = days; e.hour = h; e.minute = m; return e; }

    @Test public void weekdaysAndTheWeekBoundary() {
        long thu = at(2026, 10, 22, 13, 0);                                           // Thursday 22 Oct 2026, 13:00
        assertEquals(at(2026, 10, 22, 19, 0), Scheduler.nextStart(weekly(1 << 3, 19, 0), thu, TZ));        // Thursday 19:00, later today
        assertEquals(at(2026, 10, 29, 9, 0), Scheduler.nextStart(weekly(1 << 3, 9, 0), thu, TZ));          // Thursday 09:00 has passed: next week
        assertEquals(at(2026, 10, 27, 19, 30), Scheduler.nextStart(weekly(1 << 1, 19, 30), thu, TZ));      // Tuesday
        assertEquals(at(2026, 10, 25, 8, 0), Scheduler.nextStart(weekly(1 << 6, 8, 0), thu, TZ));          // Sunday
        assertEquals(at(2026, 10, 23, 7, 0), Scheduler.nextStart(weekly(0x7F, 7, 0), thu, TZ));            // every day: tomorrow morning
        assertEquals(at(2026, 10, 24, 10, 0), Scheduler.nextStart(weekly(0x60, 10, 0), thu, TZ));          // weekends: Saturday
    }
    @Test public void onceAndDisabled() {
        long now = at(2026, 10, 22, 13, 0);
        Scheduler.Entry e = weekly(0, 2, 26); e.date = "2026-10-23";
        assertEquals(at(2026, 10, 23, 2, 26), Scheduler.nextStart(e, now, TZ));
        e.date = "2026-10-21"; assertEquals(-1, Scheduler.nextStart(e, now, TZ));      // in the past
        Scheduler.Entry w = weekly(0x7F, 19, 0); w.enabled = false; assertEquals(-1, Scheduler.nextStart(w, now, TZ));
        assertEquals("Weekdays 19:00 for 60 min", weekly(0x1F, 19, 0).when());
        Scheduler.Entry b = weekly(1 << 1 | 1 << 3, 6, 5); b.action = Scheduler.BEACON; assertEquals("Tue, Thu 06:05", b.when());
    }
}
