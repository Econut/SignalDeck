/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Streaming wrapper around {@link HeldPath}. The receive thread only appends samples. A decoder thread runs the
 * equalizer on the last few seconds and reports each new frame once.
 */
public final class HeldPathReceiver {
    public interface Listener {
        void started(M17.Lsf lsf);
        /** A link setup that arrived after the transmission had already started. */
        void link(M17.Lsf lsf);
        void frame(M17.Stream s, float quality);
        void ended(int frames, int good, String why);
    }

    private static final int CAP = 4 * 8000;
    private final double[] taps;
    private final Listener listener;
    private final short[] buf = new short[CAP];
    private final Set<Integer> seen = new HashSet<>();
    private final byte[][] chunks = new byte[6][];
    private int len;
    private boolean open, stop, lsfSent;
    private int frames, good;
    private long lastEmit;
    private final Thread thread;

    public HeldPathReceiver(double[] taps, Listener listener) {
        if (taps.length != HeldPath.TAPS) throw new IllegalArgumentException("taps");
        this.taps = taps.clone();
        this.listener = listener;
        thread = new Thread(this::loop, "m17-held");
        thread.setDaemon(true);
        thread.start();
    }

    /** 16-bit little-endian mono at 8 kHz. */
    public void feed(byte[] mono, int n) {
        if (stop) return;
        synchronized (buf) {
            for (int i = 0; i + 1 < n; i += 2) {
                if (len >= CAP) {
                    int drop = 8000;
                    System.arraycopy(buf, drop, buf, 0, len - drop);
                    len -= drop;
                }
                buf[len++] = (short) ((mono[i] & 0xFF) | (mono[i + 1] << 8));
            }
        }
    }

    public void stop() { stop = true; thread.interrupt(); finish("stopped"); }

    private void loop() {
        while (!stop) {
            try { Thread.sleep(400); } catch (InterruptedException e) { if (stop) return; }
            short[] snap;
            synchronized (buf) { if (len < 4000) continue; snap = Arrays.copyOf(buf, len); }
            try { take(snap); }
            catch (Throwable ignored) { }
        }
    }

    private void take(short[] snap) {
        List<HeldPath.FrameHit> hits = HeldPath.decode(snap, snap.length, taps, false);
        int added = 0;
        boolean endBit = false;
        long now = System.currentTimeMillis();
        for (HeldPath.FrameHit h : hits) {
            if (h.link != null) {
                if (!open) {
                    open = true; frames = 0; good = 0; lsfSent = h.link.crcOk; java.util.Arrays.fill(chunks, null); lastEmit = now;
                    listener.started(h.link); added++;
                } else if (h.link.crcOk && !lsfSent) {
                    lsfSent = true; listener.link(h.link);
                }
                continue;
            }
            if (h.stream == null || !seen.add(h.stream.fn)) continue;
            if (!open) { open = true; frames = 0; good = 0; lsfSent = false; java.util.Arrays.fill(chunks, null); listener.started(new M17.Lsf(new byte[30], false)); }
            frames++;
            if (h.stream.lich != null && h.stream.lichErrors <= 2) good++;
            listener.frame(h.stream, Math.max(0f, Math.min(1f, h.score)));
            lich(h.stream);
            added++; lastEmit = now;
            if (h.stream.last()) endBit = true;
        }
        if (endBit) { finish("last frame"); return; }
        if (open && added == 0 && now - lastEmit > 1000) finish("signal lost");
    }

    /** Six good LICH chunks are the link setup. That is how a transmission already in progress gets its callsigns. */
    private void lich(M17.Stream s) {
        if (lsfSent || s.lich == null) return;
        int c = s.lichCount(); if (c < 0 || c > 5) return;
        chunks[c] = java.util.Arrays.copyOf(s.lich, 5);
        for (byte[] ch : chunks) if (ch == null) return;
        byte[] raw = new byte[30];
        for (int i = 0; i < 6; i++) System.arraycopy(chunks[i], 0, raw, 5 * i, 5);
        boolean ok = M17.crc16(raw, 28) == (((raw[28] & 0xFF) << 8) | (raw[29] & 0xFF));
        if (!ok) return;
        lsfSent = true;
        listener.link(new M17.Lsf(raw, true));
    }

    private void finish(String why) {
        int f, g;
        synchronized (buf) {
            if (!open) return;
            open = false; f = frames; g = good; frames = 0; good = 0; lsfSent = false;
            seen.clear();
        }
        listener.ended(f, g, why);
    }
}
