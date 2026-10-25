/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Streaming wrapper around {@link HeldPath#live}. The receive thread only appends samples. Each pass searches every
 * sync chain after the last one consumed, skips the module's muted PCM, and drops audio up to the last decoded frame.
 * A transmission opens only from a link-setup CRC or six LICH chunks, so squelch noise does not become a second contact.
 */
public final class HeldPathReceiver {
    public interface Listener {
        void started(M17.Lsf lsf);
        /** A link setup that arrived after the transmission had already started. */
        void link(M17.Lsf lsf);
        void frame(M17.Stream s, float quality);
        void ended(int frames, int good, String why);
    }

    private static final Logger LOG = Logger.getLogger("HeldPath");
    private static final int CAP = 20 * 8000;
    private static final int KEEP = 800;
    private final double[] taps;
    private final Listener listener;
    private final short[] buf = new short[CAP];
    private final byte[][] chunks = new byte[6][];
    private int len, ignore;
    private boolean open, stop, lsfSent;
    private int frames, good, emitted;
    private long lastEmit;
    private final Thread thread;

    public HeldPathReceiver(double[] taps, Listener listener) { this(taps, listener, true); }

    /** {@code background} false: a test calls {@link #poll} itself and no thread is started. */
    HeldPathReceiver(double[] taps, Listener listener, boolean background) {
        if (taps.length != HeldPath.TAPS) throw new IllegalArgumentException("taps");
        this.taps = taps.clone();
        this.listener = listener;
        if (!background) { thread = null; return; }
        thread = new Thread(this::loop, "m17-held");
        thread.setDaemon(true);
        thread.start();
    }

    /** 16-bit little-endian mono at 8 kHz. */
    public void feed(byte[] mono, int n) {
        if (stop) return;
        synchronized (buf) {
            for (int i = 0; i + 1 < n; i += 2) {
                if (len >= CAP) return;
                buf[len++] = (short) ((mono[i] & 0xFF) | (mono[i + 1] << 8));
            }
        }
    }

    /** One decode of whatever is buffered. The background thread calls this; tests call it after {@link #feed}. */
    void poll() {
        short[] snap; int from;
        synchronized (buf) {
            if (len - ignore < 400) { snap = null; from = ignore; }
            else { snap = Arrays.copyOf(buf, len); from = ignore; }
        }
        if (snap == null) {
            if (open && System.currentTimeMillis() - lastEmit > 1000) finish("signal lost");
            return;
        }
        // Each pass decodes the whole unconsumed buffer. Advancing through an open call would start the
        // next pass mid-frame and drop the frames a partial slice cannot hold.
        HeldPath.Live live = HeldPath.live(snap, snap.length, taps, from);
        take(live, snap.length);
    }

    public void stop() { stop = true; if (thread != null) thread.interrupt(); finish("stopped"); }

    private void loop() {
        while (!stop) {
            try { Thread.sleep(200); }
            catch (InterruptedException e) { if (stop) return; }
            try { poll(); }
            catch (Throwable t) { report(t); }
        }
    }

    private void take(HeldPath.Live live, int n) {
        int resume = live.resume8;
        boolean endBit = false;
        long now = System.currentTimeMillis();
        int added = 0;
        int index = 0;
        for (List<HeldPath.FrameHit> chain : live.chains) {
            M17.Lsf id = identity(chain);
            if (!open) {
                if (id == null) continue;
                open = true; frames = 0; good = 0; emitted = 0; lsfSent = true; Arrays.fill(chunks, null); lastEmit = now;
                listener.started(id); added++;
            } else if (id != null && !lsfSent) { lsfSent = true; listener.link(id); }
            for (HeldPath.FrameHit h : chain) {
                if (h.stream == null) continue;
                if (index >= emitted) {
                    frames++;
                    if (h.stream.lich != null && h.stream.lichErrors <= 2) good++;
                    listener.frame(h.stream, Math.max(0f, Math.min(1f, h.score)));
                    added++; lastEmit = now;
                    endBit = h.stream.last();
                }
                index++;
            }
        }
        if (index > emitted) emitted = index;
        // A decoded end bit only closes the contact once the next sync is actually gone. A bad frame
        // can set the bit in the middle of a transmission, and the rest of that transmission is still audio.
        if (endBit && live.closed) { finish("last frame"); return; }
        if (live.closed && open) { finish("signal lost"); return; }
        if (!open) advance(resume, n);
        else if (added == 0 && now - lastEmit > 1000) finish("signal lost");
    }

    /** The chain's link setup, from its LSF or from six LICH chunks. Null when neither checks. */
    private M17.Lsf identity(List<HeldPath.FrameHit> chain) {
        M17.Lsf id = null;
        byte[][] part = new byte[6][];
        for (HeldPath.FrameHit h : chain) {
            if (h.link != null && h.link.crcOk) id = h.link;
            if (h.stream == null || h.stream.lich == null) continue;
            int c = h.stream.lichCount();
            if (c >= 0 && c <= 5) part[c] = Arrays.copyOf(h.stream.lich, 5);
        }
        boolean all = true; for (byte[] p : part) if (p == null) { all = false; break; }
        if (!all) return id;
        byte[] raw = new byte[30];
        for (int i = 0; i < 6; i++) System.arraycopy(part[i], 0, raw, 5 * i, 5);
        boolean ok = M17.crc16(raw, 28) == (((raw[28] & 0xFF) << 8) | (raw[29] & 0xFF));
        return ok ? new M17.Lsf(raw, true) : id;
    }

    /** Drop audio the search has finished with, keeping {@link #KEEP} samples so the next frame's filter has a past. */
    private void advance(int resume8, int n) {
        int end = Math.max(ignore, Math.min(resume8, n));
        synchronized (buf) {
            int drop = end - KEEP;
            if (drop <= 0) { ignore = end; return; }
            if (drop >= len) { len = 0; ignore = 0; return; }
            System.arraycopy(buf, drop, buf, 0, len - drop);
            len -= drop;
            ignore = Math.min(KEEP, len);
        }
    }

    private void finish(String why) {
        int f, g;
        synchronized (buf) {
            if (!open) { len = 0; ignore = 0; return; }
            open = false; f = frames; g = good; frames = 0; good = 0; emitted = 0; lsfSent = false;
            len = 0; ignore = 0;
        }
        listener.ended(f, g, why);
    }

    private static void report(Throwable t) {
        LOG.log(Level.WARNING, "decode", t);
        try {
            Class.forName("android.util.Log").getMethod("w", String.class, String.class, Throwable.class)
                .invoke(null, "HeldPath", "decode", t);
        } catch (Throwable ignored) { }
    }
}
