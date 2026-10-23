/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Bench tools for the developer, reachable only from adb: RadioService registers it at runtime with the DUMP
 * permission required of the sender, which the shell holds and apps cannot get.
 * <pre>
 * am broadcast -a com.macdmr.radio.DIAG --es cmd inject --es path /sdcard/Download/x.wav
 * am broadcast -a com.macdmr.radio.DIAG --es cmd isline --es line "SRC>APRS,TCPIP*::DEST     :hi{1"
 * am broadcast -a com.macdmr.radio.DIAG --es cmd heard --es call DEST        (as if DEST was heard on the radio)
 * am broadcast -a com.macdmr.radio.DIAG --es cmd benchIgate --ez on true       (gate to RF without APRS-IS and off 144.390)
 * am broadcast -a com.macdmr.radio.DIAG --es cmd pkt --es what object|bulletin|telemetry|weather|cwid|beacon|connect|disconnect
 * </pre>
 * {@code inject} plays a WAV file (16-bit PCM, any rate, mono or stereo) into the receive path in real time, as if
 * the radio heard it: decoders, the parrot and the DTMF remote see it, nothing is keyed by the injection itself.
 */
public final class DiagReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.macdmr.radio.DIAG";
    @Override public void onReceive(Context c, Intent i) {
        RadioService s = RadioService.running;
        String cmd = i.getStringExtra("cmd");
        Log.i("DiagReceiver", "cmd " + cmd);
        if (s == null || cmd == null) return;
        switch (cmd) {
            case "inject": s.injectWav(new java.io.File(i.getStringExtra("path"))); break;
            case "isline": if (s.packet() != null) s.packet().diagServerLine(i.getStringExtra("line")); break;
            case "heard": if (s.packet() != null) s.packet().diagHeard(i.getStringExtra("call")); break;
            case "vfo": {                                                       // bench: the VFO on --ei hz (wide; low power unless --ei power 1; nothing is keyed)
                int hz = i.getIntExtra("hz", 0); if (hz < 136_000_000 || hz > 480_000_000) { Log.w("DiagReceiver", "vfo: bad hz " + hz); break; }
                s.diagVfo(hz, i.getIntExtra("power", 0) == 1 ? 1 : 0); break;
            }
            case "scope": {                                                     // bench: --ei from --ei to --ei step --ei settle (ms) --ei sweeps N: log N sweeps, then stop
                final int sw = Math.max(1, i.getIntExtra("sweeps", 2));
                s.scope().setTimeoutMs(i.getIntExtra("timeout", 900));
                s.scopeSq = Math.max(0, Math.min(9, i.getIntExtra("sq", 9)));
                String e = s.scopeStart(i.getIntExtra("from", 146_400_000), i.getIntExtra("to", 146_640_000), i.getIntExtra("step", 25_000), i.getIntExtra("settle", 40));
                Log.i("DiagReceiver", "scope: " + (e == null ? "started" : e));
                if (e == null) new Thread(() -> {
                    int done = 0; long seen = -1;
                    for (int k = 0; k < 600 && done < sw; k++) {
                        try { Thread.sleep(250); } catch (InterruptedException x) { return; }
                        java.util.List<float[]> h = s.scope().history(); if (h.size() > seen && h.size() > 0) { seen = h.size(); done++; float[] r = h.get(h.size() - 1); StringBuilder b = new StringBuilder(); for (float v : r) b.append(Float.isNaN(v) ? "nan" : String.format(java.util.Locale.US, "%.1f", v)).append(' '); Log.i("DiagReceiver", "scope sweep " + done + " (" + s.scope().sweepMs() + " ms): " + b + " | ms " + java.util.Arrays.toString(s.scope().latency())); }
                    }
                    s.scopeStop();
                }, "diag-scope").start();
                break;
            }
            case "reportzip": new Thread(() -> { try { Log.i("DiagReceiver", "reportzip: " + DeviceReport.zipNewest(c, false, false)); } catch (Throwable t) { Log.w("DiagReceiver", "reportzip: " + t); } }, "diag-zip").start(); break;   // bench: the summary + zip
            case "txwav": { String nm = i.getStringExtra("name"); if (nm != null && nm.matches("[A-Za-z0-9_-]{1,40}")) Log.i("DiagReceiver", "txwav: " + s.diagTxWav(new java.io.File(c.getCacheDir(), "tx_" + nm + ".wav"))); break; }   // bench: transmit cache/tx_<name>.wav (low power VFO)
            case "rxvol": s.diagAnalogVolume(Math.max(0, Math.min(255, i.getIntExtra("v", 8)))); break;   // bench: the module volume byte of the current analog channel
            case "rxclip": {                                                    // bench: record the receive audio (8 kHz mono, before any app squelch) for --ei ms into cache/rxclip_<name>.wav
                final int ms = Math.max(1000, Math.min(120_000, i.getIntExtra("ms", 10_000)));
                String nm = i.getStringExtra("name"); if (nm == null || !nm.matches("[A-Za-z0-9_-]{1,40}")) nm = "clip";
                try {
                    final java.io.File f = new java.io.File(c.getCacheDir(), "rxclip_" + nm + ".wav");
                    final ClipRecorder cr = new ClipRecorder(f, ms); s.recordClip(cr, false);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { s.recordClip(null, false); cr.finish(); Log.i("DiagReceiver", "rxclip done: " + f + " " + f.length() + " B"); }, ms + 300);
                    Log.i("DiagReceiver", "rxclip start " + ms + " ms");
                } catch (Throwable t) { Log.w("DiagReceiver", "rxclip: " + t); }
                break;
            }
            case "wlbench": com.macdmr.radio.winlink.Winlink.benchPort = Math.max(0, Math.min(65535, i.getIntExtra("port", 0))); Log.i("DiagReceiver", "wlbench port " + com.macdmr.radio.winlink.Winlink.benchPort); break;   // bench: test CMS on 127.0.0.1 (0 = off)
            case "wldiagreset": s.winlink().resetDiag(); Log.i("DiagReceiver", "wldiagreset"); break;   // bench: the first-connection question again, log off
            case "wlconnect": Log.i("DiagReceiver", "wlconnect: " + s.winlink().connectInternet()); break;
            case "smsbench": s.sms(i.getIntExtra("type", 0), i.getIntExtra("from", 0), "[bench] " + i.getStringExtra("text")); break;   // bench: a DMR text as if received (0x2D type, sender ID)
            case "smsbenchclear": Log.i("DiagReceiver", "smsbenchclear: " + s.store().deleteBenchMessages()); break;
            case "formtext": s.onFormText(i.getStringExtra("from"), i.getStringExtra("text"), "bench"); break;   // bench: a text as if it arrived (form parts)
            case "testtone": {                                                  // bench: key a 1 kHz tone for --ei ms (≤ 5 s) on the current channel or VFO
                int ms = Math.max(200, Math.min(5000, i.getIntExtra("ms", 2000)));
                Log.i("DiagReceiver", "testtone " + ms + " ms"); s.diagTestTone(ms); break;
            }
            case "vfooff": s.diagVfoOff(); break;
            // bench: load a signed profile list from a file (--es path …), regardless of its serial, to try advisories on this
            // phone without publishing them; "profileclear" removes it again (and the owner's overrides)
            case "profiletest": Log.i("DiagReceiver", "profiletest: " + ProfileRegistry.loadForTest(new java.io.File(i.getStringExtra("path")))); break;
            // bench: the DMR audio check for --ei seconds (default 10) on the current channel; the section goes to logcat, not to a report
            // bench: the update path's root install (older versions included) from a local APK (--es path …); the app restarts as that APK
            case "rootinstall": { final String path = i.getStringExtra("path"); if (path != null && path.endsWith(".apk")) new Thread(() -> Log.i("DiagReceiver", "rootinstall: " + Updater.rootInstall(path)), "rootinstall").start(); break; }
            case "audiocheck": case "dmrcheck": { final int sec = i.getIntExtra("seconds", 10); final Context ctx = c.getApplicationContext();
                new Thread(() -> { try { for (String l : DmrAudioCheck.run(ctx, s, sec, t -> { }).split("\n")) Log.i("DmrAudioCheck", l); } catch (Throwable t) { Log.w("DmrAudioCheck", t); } }, "dmrcheck").start(); break; }
            case "profileclear": Log.i("DiagReceiver", "profileclear: " + ProfileRegistry.clearForTest()); break;
            case "web": Log.i("DiagReceiver", "web: " + (s.web() == null ? "no host" : String.valueOf(s.web().setEnabled(i.getBooleanExtra("on", true))) + " running=" + s.web().running() + " " + s.web().urls())); break;
            case "aprs144": Log.i("DiagReceiver", "aprs144: " + s.aprsMonitor(i.getBooleanExtra("on", true))); break;      // standard APRS monitoring on/off (receive)
            case "issaprs": {                                                   // ISS APRS: track the next pass and every one after it
                String e = s.startIssMode(com.macdmr.radio.ham.Satellites.APRS);
                if (e == null) s.setIssRepeat(com.macdmr.radio.ham.Satellites.APRS);
                Log.i("DiagReceiver", "issaprs: " + (e == null ? "tracking" : e)); break;
            }
            case "isssstv": {                                                   // ISS SSTV: track the next pass and every one after it (an SSTV event)
                String e = s.startIssMode(com.macdmr.radio.ham.Satellites.SSTV);
                if (e == null) s.setIssRepeat(com.macdmr.radio.ham.Satellites.SSTV);
                Log.i("DiagReceiver", "isssstv: " + (e == null ? "tracking" : e)); break;
            }
            case "afsk": {                                                      // bench: --ez legacy true = the old single-slicer decoder
                boolean legacy = i.getBooleanExtra("legacy", false);
                com.macdmr.radio.pkt.AFSKDecoder.MULTI_SLICER = !legacy; com.macdmr.radio.pkt.AFSKDecoder.BIT_FIX = !legacy;
                com.macdmr.radio.pkt.AFSKDecoderIQ.PREFILTER = !legacy; com.macdmr.radio.pkt.AFSKDecoderIQ.toneFilterSize = legacy ? 32 : 52;
                Log.i("DiagReceiver", "afsk decoder: " + (legacy ? "legacy" : "new")); break;
            }
            case "sstv": {                                                      // bench: --ez on, --ez park (144.500), --ez cont (continuous Robot 36)
                if (s.packet() != null && i.hasExtra("cont")) s.packet().setSstvContinuousMode(i.getBooleanExtra("cont", false));
                Log.i("DiagReceiver", "sstv: " + s.sstvMonitor(i.getBooleanExtra("on", false), i.getBooleanExtra("park", true))); break;
            }
            case "leadtest": if (s.packet() != null) Log.i("DiagReceiver", "leadtest: " + s.packet().diagLeadTest(i.getIntExtra("kind", 0), i.getIntExtra("ms", 1600), i.getIntExtra("reps", 1), String.valueOf(i.getStringExtra("tag")))); break;   // one status packet, current channel
            case "sattest": {                                                   // a fake ISS pass from a minute from now for 30 s (or --el ms), --es key iss-rptr|iss-aprs|iss-sstv; nothing is keyed
                com.macdmr.radio.ham.Sgp4 e = s.satellites().elements().get(25544);
                if (e == null) { Log.w("DiagReceiver", "no ISS elements"); break; }
                long len = i.getLongExtra("len", 30_000);
                com.macdmr.radio.ham.SatTrack.Pass p = new com.macdmr.radio.ham.SatTrack.Pass(); p.aos = System.currentTimeMillis() + 60_000; p.los = p.aos + len; p.tca = p.aos; p.maxEl = 45;
                String key = i.getStringExtra("key"); if (key == null) key = "iss-rptr";
                Log.i("DiagReceiver", "sattest " + key + ": " + s.trackSat(com.macdmr.radio.ham.Satellites.byKey(s, key), e, p)); break;
            }
            case "iconpng": try {                                  // the launcher icon as the launcher draws it (adaptive, circle mask), for checking the artwork
                android.graphics.drawable.Drawable d = s.getPackageManager().getApplicationIcon(s.getPackageName());
                android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(bm); d.setBounds(0, 0, 432, 432); d.draw(cv);
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(s.getExternalFilesDir(null), "icon.png"))) { bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); }
                Log.i("DiagReceiver", "icon saved");
            } catch (Throwable t) { Log.w("DiagReceiver", "iconpng: " + t); } break;
            case "callstart": Log.i("DiagReceiver", "talk call: " + com.macdmr.radio.car.RadioCallService.start(s)); break;   // places the call only: no transmission unless a wheel start is pending
            case "callend": com.macdmr.radio.car.RadioCallService.end(); break;
            case "benchIgate": if (s.packet() != null) s.packet().benchIgate = i.getBooleanExtra("on", false); break;
            case "pkt": if (s.packet() != null) Log.i("DiagReceiver", "pkt " + i.getStringExtra("what") + ": " + s.packet().diagSend(i.getStringExtra("what"))); break;
            default: Log.w("DiagReceiver", "unknown cmd " + cmd);
        }
    }
}
