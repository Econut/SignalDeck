/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Receive audio on phones whose module drives the speaker through hardware switches and whose audio HAL routes that
 * same audio to the camcorder recording input while the radio receives (the ANYSECU W6+, measured 2026-10-27 with a
 * HackRF tone: docs/w6/RX_AUDIO_TEST.md). The speaker is the module's own path, so nothing is played here: the audio is
 * only handed to SignalDeck's squelch, recorder, captions and decoders, as 8 kHz stereo frames with L == R like the
 * other backends. Transmit audio is not known on these phones yet.
 */
public final class CamcorderAudio implements AudioBackend {
    static final int RATE = 8000, BLOCK = 256;          // 32 ms blocks
    /**
     * The camcorder input carries the radio about 21 dB lower than the other phones' audio (W6, 2026-10-27: a 1 kHz tone
     * at 2.5 kHz deviation arrived at -44 dBFS with the module volume at 4 of 8, and the SSTV decoder skipped a Robot 36
     * picture as "likely noise"). A fixed gain of 12 brings it to about -22 dBFS; samples are clipped, never wrapped.
     */
    static final int GAIN = 12;
    private volatile Sink sink;
    private volatile boolean running;
    private Thread thread;
    private AudioRecord rec;

    @Override public void setSink(Sink s) { sink = s; }
    @Override public void setGate(Gate g) { }           // the module's own speaker path: the gate does not apply

    /** Mono 16-bit samples, times {@link #GAIN} and clipped, to the stereo (L == R) little-endian frames the sink takes. */
    static int toStereo(short[] mono, int n, byte[] out) { return toStereo(mono, n, out, GAIN); }
    static int toStereo(short[] mono, int n, byte[] out, int gain) {
        for (int i = 0; i < n; i++) {
            int v = Math.max(-32768, Math.min(32767, mono[i] * gain));
            byte lo = (byte) v, hi = (byte) (v >> 8);
            out[4 * i] = lo; out[4 * i + 1] = hi; out[4 * i + 2] = lo; out[4 * i + 3] = hi;
        }
        return 4 * n;
    }

    @Override public synchronized String startRx(boolean speaker) {
        if (running) return "receive audio already running (camcorder input)";
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8 * BLOCK * 2));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); rec = null; return "startRx failed: the camcorder input could not be opened"; }
            rec.startRecording();
        } catch (Throwable t) { return "startRx failed: " + t.getMessage(); }
        running = true;
        final AudioRecord r = rec;
        thread = new Thread(() -> {
            short[] mono = new short[BLOCK]; byte[] st = new byte[4 * BLOCK];
            while (running) {
                int n = r.read(mono, 0, BLOCK);
                if (n <= 0) continue;
                Sink s = sink; if (s != null) s.onPcm(st, toStereo(mono, n, st));
            }
        }, "camcorder-rx");
        thread.start();
        return "receive audio from the camcorder input, 8 kHz (the speaker is the module's own path)";
    }

    @Override public synchronized String stopRx() {
        running = false;
        if (thread != null) try { thread.join(500); } catch (InterruptedException ignored) { }
        if (rec != null) { try { rec.stop(); } catch (Throwable ignored) { } rec.release(); rec = null; }
        return "receive audio stopped";
    }

    private static final String NO_TX = "transmit audio is not available on this phone yet";
    @Override public String startTx() { return NO_TX; }
    @Override public String stopTx() { return NO_TX; }
    @Override public String playPcm(short[] mono) { return NO_TX; }
}
