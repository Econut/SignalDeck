/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** RIC numbers, or a contact's name or callsign, the same way the phone's pager page reads them. */
public final class PagerTargets {
    private PagerTargets() { }
    /** Digits are a RIC. Anything else is looked up; {@code null} means no such contact, an empty array means the contact has no RIC. */
    public interface Book { int[] ricsOf(String nameOrCall); }

    public static int[] resolve(String raw, Book book) {
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Add a RIC or a contact");
        for (String tok : raw.trim().split("[,;\\s]+")) {
            if (tok.isEmpty()) continue;
            boolean digits = true;
            for (int i = 0; i < tok.length(); i++) if (!Character.isDigit(tok.charAt(i))) digits = false;
            if (digits) {
                int r;
                try { r = Integer.parseInt(tok); }
                catch (NumberFormatException e) { throw new IllegalArgumentException(tok + " is not a RIC (0 to 2097151)"); }
                if (r < 0 || r > 0x1FFFFF) throw new IllegalArgumentException(tok + " is not a RIC (0 to 2097151)");
                if (!out.contains(r)) out.add(r);
                continue;
            }
            int[] found = book == null ? null : book.ricsOf(tok);
            if (found == null) throw new IllegalArgumentException("No contact named " + tok);
            if (found.length == 0) throw new IllegalArgumentException(tok + " has no pager RIC");
            for (int r : found) if (r >= 0 && r <= 0x1FFFFF && !out.contains(r)) out.add(r);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Add a RIC or a contact");
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    public static String key(String token) { return token.toUpperCase(Locale.US); }
}
