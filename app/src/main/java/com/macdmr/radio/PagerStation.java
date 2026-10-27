/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.util.Log;

import com.macdmr.radio.ham.MessageThreads;
import com.macdmr.radio.ham.Pocsag;
import com.macdmr.radio.ham.PocsagDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * POCSAG receive. Audio is taken below the squelch. A stub-tagged module uses the held-path taps;
 * stock firmware uses the plain slicer. Monitor mode steps a frequency list and waits out a preamble.
 */
public final class PagerStation {
    public interface Host {
        void page(ChannelStore.PageRow row);
        void tune(int hz);
        int currentHz();
        double[] taps();
        boolean held();
        MessageThreads.Contacts contacts();
    }

    private final Host host;
    private volatile boolean running, monitor, held;
    private final short[] ring = new short[8000 * 8];
    private int at, filled, fresh;
    private final List<String> seen = new ArrayList<>();
    private int[] scanHz = new int[0];
    private int baud = 1200;
    private Thread scan;
    private volatile boolean preamble;

    public PagerStation(Host host) { this.host = host; }
    public boolean running() { return running; }
    public boolean monitoring() { return monitor; }
    public boolean preamble() { return preamble; }
    /** How long to sit on a frequency: one preamble plus one batch. */
    public static long dwellMs(int baud) { return baud <= 512 ? 2300 : baud >= 2400 ? 700 : 1100; }

    public synchronized void start(boolean heldPath) {
        running = true; held = heldPath; fresh = 0;
    }
    public synchronized void stop() {
        running = false; monitor = false;
        if (scan != null) scan.interrupt();
    }

    public void setScan(int[] hz, int baud) {
        this.baud = baud == 512 || baud == 2400 ? baud : 1200;
        scanHz = hz == null ? new int[0] : hz.clone();
    }

    public synchronized String monitor(boolean on) {
        if (on && (scanHz == null || scanHz.length < 2)) return "Add at least two pager frequencies";
        monitor = on;
        if (scan != null) scan.interrupt();
        if (!on) return null;
        scan = new Thread(this::scanLoop, "pager-scan");
        scan.setDaemon(true);
        scan.start();
        return null;
    }

    private void scanLoop() {
        try {
            while (monitor && running) {
                for (int hz : scanHz) {
                    if (!monitor || !running) return;
                    host.tune(hz);
                    long until = System.currentTimeMillis() + dwellMs(baud);
                    while (monitor && System.currentTimeMillis() < until) Thread.sleep(100);
                    while (monitor && preamble) Thread.sleep(100);
                }
            }
        } catch (InterruptedException ignored) { }
    }

    /** 8 kHz mono, little-endian, squelch or not. */
    public void feed(byte[] mono, int n) {
        if (!running || mono == null || n < 2) return;
        synchronized (ring) {
            for (int i = 0; i + 1 < n; i += 2) {
                ring[at] = (short) ((mono[i] & 0xFF) | (mono[i + 1] << 8));
                at = (at + 1) % ring.length;
                if (filled < ring.length) filled++;
                fresh++;
            }
        }
        if (fresh < 8000) return;
        fresh = 0;
        short[] snap; int len;
        synchronized (ring) {
            len = filled; snap = new short[len];
            int start = (at - len + ring.length) % ring.length;
            for (int i = 0; i < len; i++) snap[i] = ring[(start + i) % ring.length];
        }
        short[] tail = len > 8000 ? java.util.Arrays.copyOfRange(snap, len - 8000, len) : snap;
        preamble = PocsagDecoder.hearingPreamble(tail);
        final boolean useHeld = held;
        final double[] taps = useHeld ? host.taps() : null;
        new Thread(() -> decode(snap, taps), "pager-decode").start();
    }

    private void decode(short[] snap, double[] taps) {
        PocsagDecoder.Hit hit = taps != null ? PocsagDecoder.bestHeld(snap, taps) : PocsagDecoder.best(snap);
        if (hit == null || hit.pages == null) return;
        for (Pocsag.Page p : hit.pages) {
            if (p.text == null || p.text.isEmpty()) continue;
            String mark = p.ric + "\n" + p.text;
            synchronized (seen) {
                if (seen.contains(mark)) continue;
                seen.add(mark); while (seen.size() > 40) seen.remove(0);
            }
            MessageThreads.Key key = MessageThreads.page(p.ric, p.text, host.contacts());
            ChannelStore.PageRow row = new ChannelStore.PageRow();
            row.ts = System.currentTimeMillis(); row.hz = host.currentHz(); row.ric = p.ric; row.func = p.func; row.baud = hit.baud;
            row.text = p.text; row.corrected = p.corrected; row.signer = Pocsag.sender(p.text) == null ? "" : Pocsag.sender(p.text);
            row.transport = "page"; row.threadKey = threadKey(key); row.outgoing = false;
            try { host.page(row); } catch (Throwable t) { Log.i("PagerStation", "page: " + t); }
        }
    }

    public static String threadKey(MessageThreads.Key key) {
        if (key == null) return "";
        switch (key.kind) {
            case CONTACT: return "c:" + key.contactId;
            case CALLSIGN: return "call:" + key.label;
            case EVERYONE: return "everyone";
            default: return "ric:" + key.address;
        }
    }
}
