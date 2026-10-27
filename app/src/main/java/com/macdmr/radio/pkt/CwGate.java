/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import com.macdmr.radio.ham.CwDecoder;

/**
 * The Morse decoder, paused while packet audio is received: AFSK through the radio can look like keyed text to it.
 * While {@link AfskPresence} is active the audio does not reach the decoder; when it becomes active, what the decoder
 * had gathered is dropped unreported (the packet's own start may already be in it).
 */
public final class CwGate {
    private final CwDecoder cw;
    private final AfskPresence afsk = new AfskPresence();
    private boolean paused;

    public CwGate(CwDecoder cw) { this.cw = cw; }

    /** 8 kHz mono 16-bit little-endian receive audio that passed the squelch. */
    public void process(byte[] mono, int n, long nowMs) {
        afsk.process(mono, n);
        if (gate(nowMs)) return;
        cw.process(mono, n);
    }
    public void process(short[] pcm, long nowMs) {
        afsk.process(pcm);
        if (gate(nowMs)) return;
        cw.process(pcm);
    }

    /** True while packet audio holds the decoder off. */
    public boolean paused() { return paused; }

    /** The packet decoder delivered a frame (any thread). */
    public void packetHeard(long nowMs) { afsk.packetHeard(nowMs); }

    /** The squelch closed or the channel changed. */
    public void flush() { if (!paused) cw.flush(); afsk.reset(); }

    private boolean gate(long nowMs) {
        boolean on = afsk.active(nowMs);
        if (on && !paused) cw.discard();
        paused = on;
        return on;
    }
}
