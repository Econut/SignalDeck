/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.web;

/**
 * Routes added for the browser pager page. Restricted mode (PR 26) keeps the full
 * {@code ROUTE_MODES} and {@code ROUTE_ADMIN} tables; these rows fold into those tables.
 * Each route is gated by one {@code modeAllowed} or {@code adminAllowed} call.
 */
public final class WebRoutes {
    private WebRoutes() { }
    /** path, mode id. */
    public static final String[][] ROUTE_MODES = {
            { "/api/pager/send", "pocsag" },
            { "/api/pager/monitor", "pocsag" },
            { "/api/m17/sms", "m17" },
            { "/api/messages", "dmrtext" } };
    /** path, admin id. Restart is a report-level action; the updater is the web admin action. */
    public static final String[][] ROUTE_ADMIN = {
            { "/api/device/restart", "report" },
            { "/api/device/update", "web" } };

    public static String mode(String path) {
        for (String[] m : ROUTE_MODES) if (path.equals(m[0])) return m[1];
        return null;
    }
    public static String admin(String path) {
        for (String[] a : ROUTE_ADMIN) if (path.equals(a[0])) return a[1];
        return null;
    }
}
