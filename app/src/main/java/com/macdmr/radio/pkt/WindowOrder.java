/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps decoded frames in the order they were on the air across overlapping decode windows. Each window repeats
 * the previous window's last second; a frame found there is held until the next window has been decoded too, then
 * the two are merged (duplicates dropped) and released by position. Without this a frame missed in one window and
 * caught in the overlap of the next reached the AX.25 session after a later one (bench 2026-10-22). Not thread-safe:
 * one decoder thread calls it.
 */
public final class WindowOrder {
    /** Held frames: {position in bits from the next window's start, frame}. */
    private final List<Object[]> held = new ArrayList<>();

    /**
     * One window's frames ({position in bits from this window's start, frame}), the bit position where the next
     * window will start, and whether this window began with the previous one's tail (if not, what was held is final
     * and released first). Returns the frames that are now final, in air order.
     */
    public List<byte[]> add(List<Object[]> found, double nextStartBits, boolean hasTail) {
        List<byte[]> out = new ArrayList<>();
        List<Object[]> all = new ArrayList<>();
        if (!hasTail) { held.sort(WindowOrder::byPos); for (Object[] h : held) out.add((byte[]) h[1]); }
        else all.addAll(held);
        held.clear();
        Set<String> seen = new HashSet<>();
        for (Object[] h : all) seen.add(Arrays.toString((byte[]) h[1]));
        for (Object[] f : found) if (seen.add(Arrays.toString((byte[]) f[1]))) all.add(new Object[] { ((Number) f[0]).doubleValue(), f[1] });
        all.sort(WindowOrder::byPos);
        for (Object[] f : all) {
            double pos = ((Number) f[0]).doubleValue();
            if (pos >= nextStartBits) held.add(new Object[] { pos - nextStartBits, f[1] });
            else out.add((byte[]) f[1]);
        }
        return out;
    }

    /** Release what is held (no further window came). */
    public List<byte[]> flush() { held.sort(WindowOrder::byPos); List<byte[]> out = new ArrayList<>(); for (Object[] h : held) out.add((byte[]) h[1]); held.clear(); return out; }
    public boolean holding() { return !held.isEmpty(); }

    private static int byPos(Object[] a, Object[] b) { return Double.compare(((Number) a[0]).doubleValue(), ((Number) b[0]).doubleValue()); }
}
