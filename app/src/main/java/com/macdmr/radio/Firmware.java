/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

/**
 * The radio module's firmware as SignalDeck has seen it: the stock version (the first one the module ever reported
 * on this phone, kept for good) and the one it reports now, so an owner who loads other firmware can tell what the
 * phone came with. The version is the module's answer to the 0x34 query.
 */
public final class Firmware {
    private Firmware() { }

    /** The version to keep as stock: the stored one, else the first non-empty report. */
    static String stockAfter(String stored, String reported) {
        if (stored != null && !stored.isEmpty()) return stored;
        return reported == null ? "" : reported.trim();
    }

    /** True when the module now reports another version than the stock one (unknown counts as not different). */
    public static boolean differs(String stock, String current) {
        return stock != null && !stock.isEmpty() && current != null && !current.isEmpty() && !stock.equals(current);
    }

    /** The Device page line: the stock version, and the current one only when it differs; "" before the first report. */
    public static String line(String stock, String current) {
        if (stock == null || stock.isEmpty()) return "";
        return differs(stock, current) ? "firmware " + current + "\nstock " + stock : "firmware " + stock + " (stock)";
    }
}
