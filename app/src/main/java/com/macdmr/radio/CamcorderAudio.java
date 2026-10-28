/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

/**
 * Receive audio on phones whose module drives the speaker through hardware switches and whose audio HAL routes that
 * same audio to the camcorder recording input while the radio receives (the ANYSECU W6+, measured 2026-10-27 with a
 * HackRF tone: docs/w6/RX_AUDIO_TEST.md). The audio goes to SignalDeck's squelch, recorder, captions and decoders as
 * 8 kHz stereo frames with L == R like the other backends. With {@code play} (the profile's appPlayback) it is also
 * played here, through the gate, on Android's media stream, and the module's own speaker route stays off; without it
 * the speaker is the module's own path. Transmit audio goes from the microphone to the module directly (its lines).
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
    private volatile Gate gate;
    private volatile boolean running;
    private Thread thread;
    private AudioRecord rec;
    private AudioTrack track;
    private final boolean play;

    public CamcorderAudio() { this(false); }
    public CamcorderAudio(boolean play) { this.play = play; }

    @Override public void setSink(Sink s) { sink = s; }
    @Override public void setGate(Gate g) { gate = g; }   // only used when this backend plays the audio itself
    @Override public boolean appPlaysRadio() { return play; }   // app playback: the gate holds the radio's audio back; otherwise the module drives the speaker

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
            if (play) {
                int tmin = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                        new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                        Math.max(tmin, 4 * BLOCK * 2), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                track.play();
            }
        } catch (Throwable t) { return "startRx failed: " + t.getMessage(); }
        running = true;
        final AudioRecord r = rec; final AudioTrack t = track;
        thread = new Thread(() -> {
            short[] mono = new short[BLOCK], out = new short[BLOCK]; byte[] st = new byte[4 * BLOCK];
            while (running) {
                int n = r.read(mono, 0, BLOCK);
                if (n <= 0) continue;
                Sink s = sink; if (s != null) s.onPcm(st, toStereo(mono, n, st));
                if (t != null) {                                                   // after the sink: the gate reflects this block's squelch
                    Gate g = gate; boolean open = g == null || g.open();
                    for (int i = 0; i < n; i++) out[i] = open ? (short) Math.max(-32768, Math.min(32767, mono[i] * GAIN)) : 0;
                    t.write(out, 0, n);
                }
            }
        }, "camcorder-rx");
        thread.start();
        return "receive audio from the camcorder input, 8 kHz" + (play ? ", played by SignalDeck (the module's speaker route stays off)" : " (the speaker is the module's own path)");
    }

    @Override public synchronized String stopRx() {
        running = false;
        if (thread != null) try { thread.join(500); } catch (InterruptedException ignored) { }
        if (rec != null) { try { rec.stop(); } catch (Throwable ignored) { } rec.release(); rec = null; }
        if (track != null) { try { track.stop(); } catch (Throwable ignored) { } track.release(); track = null; }
        return "receive audio stopped";
    }

    private static final String NO_TX = "transmit audio is not available on this phone yet";
    @Override public String startTx() { return NO_TX; }
    @Override public String stopTx() { return NO_TX; }
    @Override public String playPcm(short[] mono) { return NO_TX; }
}
