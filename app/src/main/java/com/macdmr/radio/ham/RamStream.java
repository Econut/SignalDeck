/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import java.util.Locale;

/**
 * The receive-RAM stream of the FW27 bench firmware (phonedmrapp radio_firmware/plan-rx-ram-stream.md): with the
 * module's mode 5 applied and the UART at 230400, command 0x40 op 17 pushes every completed half of the HR-C7000's FM
 * receive RAM as one unsolicited 0x40 frame. FW27 reuses op 5's copy-and-send, so the body is op 5's: 256 big-endian
 * 16-bit samples at 8 kHz in bytes 0..511, then the sequence byte at 512 (op 5 counts 0..5; a free-running 0..255 is
 * taken too) and the half id (0 or 1, alternating) at 513. The older 516-byte form (opcode 17, a 16-bit sequence, the
 * half id, then the samples) is still parsed. That baseband is the FM
 * discriminator before the phone's audio path, flat down to 50 Hz, so M17 can be decoded from it without the
 * equaliser the audio path needs. A skipped sequence number, or two frames in a row with the same half id, is a
 * missed half.
 */
public final class RamStream {
    private RamStream() { }

    public static final int OP = 17;
    public static final int SAMPLES = 256;
    public static final int BODY = 2 * SAMPLES + 2;   // op 5's reply: 512 sample bytes, sequence, half id
    public static final int BODY_OP17 = 4 + 2 * SAMPLES;   // the first design: opcode, seq lo, seq hi, half id, samples

    /** One half of the receive RAM as it came off the wire. */
    public static final class Half {
        public final int seq, id;
        public final short[] samples;
        Half(int seq, int id, short[] samples) { this.seq = seq; this.id = id; this.samples = samples; }
    }

    /** A 0x40 body that is a stream half, or null for anything else (an op 17 ack, another op's reply). */
    public static Half parse(byte[] body) {
        if (body == null) return null;
        int at, seq, id;
        if (body.length == BODY) { at = 0; seq = body[512] & 0xFF; id = body[513] & 0x01; }
        else if (body.length == BODY_OP17 && (body[0] & 0xFF) == OP) { at = 4; seq = (body[1] & 0xFF) | ((body[2] & 0xFF) << 8); id = body[3] & 0x01; }
        else return null;
        short[] s = new short[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) s[i] = (short) (((body[at + 2 * i] & 0xFF) << 8) | (body[at + 1 + 2 * i] & 0xFF));   // big-endian, as the RAM holds them
        return new Half(seq, id, s);
    }

    /** Taps that leave the stream as it is: the equaliser's centre tap alone. */
    public static double[] identityTaps() {
        double[] w = new double[HeldPath.TAPS];
        w[HeldPath.TAPS / 2] = 1.0;
        return w;
    }

    /**
     * Reassembles the halves into the 8 kHz 16-bit little-endian stream the held-path receiver already takes, and keeps
     * the count of halves and misses. The receiver it feeds decides what the samples mean; this class only moves bytes.
     */
    /** Where the reassembled 8 kHz little-endian stream goes: the held-path receiver's {@code feed}. */
    public interface Sink { void feed(byte[] mono, int n); }

    public static final class Receiver {
        private final Sink sink;
        private int lastSeq = -1, lastId = -1;
        private int seqMod = 6;                          // op 5's sequence counts 0..5; a value of 6 or more means a wider counter
        private long halves, missed, samples;
        private boolean started;

        public Receiver(Sink sink) { this.sink = sink; }

        public synchronized void half(Half h) {
            if (h == null) return;
            if (h.seq >= seqMod) seqMod = h.seq >= 256 ? 65536 : 256;
            if (started) {
                int expect = (lastSeq + 1) % seqMod;
                if (h.seq != expect) missed += ((h.seq - expect) % seqMod + seqMod) % seqMod;
                else if (h.id == lastId) missed++;              // the sequence said nothing was lost, the half id says one was
            }
            started = true; lastSeq = h.seq; lastId = h.id; halves++; samples += h.samples.length;
            byte[] le = new byte[2 * h.samples.length];
            for (int i = 0; i < h.samples.length; i++) { le[2 * i] = (byte) h.samples[i]; le[2 * i + 1] = (byte) (h.samples[i] >> 8); }
            if (sink != null) sink.feed(le, le.length);
        }

        public synchronized long halves() { return halves; }
        public synchronized long missed() { return missed; }
        /** Seconds of audio the halves carried. */
        public synchronized double seconds() { return samples / 8000.0; }
        public synchronized String stats() {
            return String.format(Locale.US, "%d halves (%.1f s), %d missed", halves, seconds(), missed);
        }
    }
}
