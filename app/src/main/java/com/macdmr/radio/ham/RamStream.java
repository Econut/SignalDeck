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
 * receive RAM as one unsolicited 0x40 frame: opcode 17, a 16-bit sequence number (little-endian, from 0, wrapping at
 * 65536), the half id (0 or 1, alternating), then 256 big-endian 16-bit samples at 8 kHz. That baseband is the FM
 * discriminator before the phone's audio path, flat down to 50 Hz, so M17 can be decoded from it without the
 * equaliser the audio path needs. A skipped sequence number, or two frames in a row with the same half id, is a
 * missed half.
 */
public final class RamStream {
    private RamStream() { }

    public static final int OP = 17;
    public static final int SAMPLES = 256;
    public static final int BODY = 4 + 2 * SAMPLES;   // opcode, seq lo, seq hi, half id, 512 sample bytes

    /** One half of the receive RAM as it came off the wire. */
    public static final class Half {
        public final int seq, id;
        public final short[] samples;
        Half(int seq, int id, short[] samples) { this.seq = seq; this.id = id; this.samples = samples; }
    }

    /** A 0x40 body that is a stream half, or null for anything else (an op 17 ack, another op's reply). */
    public static Half parse(byte[] body) {
        if (body == null || body.length != BODY || (body[0] & 0xFF) != OP) return null;
        int seq = (body[1] & 0xFF) | ((body[2] & 0xFF) << 8);
        int id = body[3] & 0x01;
        short[] s = new short[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) s[i] = (short) (((body[4 + 2 * i] & 0xFF) << 8) | (body[5 + 2 * i] & 0xFF));   // big-endian, as the RAM holds them
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
        private long halves, missed, samples;
        private boolean started;

        public Receiver(Sink sink) { this.sink = sink; }

        public synchronized void half(Half h) {
            if (h == null) return;
            if (started) {
                int expect = (lastSeq + 1) & 0xFFFF;
                if (h.seq != expect) missed += (h.seq - expect) & 0xFFFF;
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
