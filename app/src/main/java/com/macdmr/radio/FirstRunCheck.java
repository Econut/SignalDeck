/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.content.Context;

import java.io.File;
import java.util.Locale;

/**
 * Everything a first run on a new or untested phone should find out, in one go, so the owner presses one button and
 * sends one zip: the device report, the PTT key capture, the radio probe (repeated with the module's power switched on
 * when it got no answer and the profile knows the power line), optionally a short trial take-over (receive only:
 * module answer, RSSI, a few seconds of the raw audio stream, then the radio is handed back to the stock app) and
 * SignalDeck's own log with personal details removed.
 */
public final class FirstRunCheck {
    public interface Progress { void step(String s); }
    private FirstRunCheck() { }

    /** Runs on a worker thread; returns the zip to share. */
    /** @param watchPkg the walkie-talkie app the owner confirmed for the 15 s watch, or null to skip that step */
    public static File run(Context c, RadioService svc, boolean trial, String watchPkg, Progress p) throws Exception {
        DeviceProfile dp = svc.profile();
        StringBuilder sum = new StringBuilder();
        if (dp.supported && !dp.stockApp.isEmpty() && !svc.isReady() && RootSetup.haveRoot()
                && !RootSetup.run("pidof " + dp.stockApp + " || true").replaceAll("[^0-9]", "").isEmpty()) {
            p.step("Closing " + dp.stockApp + " so the ports are free");
            RootSetup.run("am force-stop " + dp.stockApp);
            Thread.sleep(1500);
            sum.append("closed the stock app (").append(dp.stockApp).append(") for the checks\n");
        }

        DeviceReport.generate(c, s -> p.step("Report: " + s));
        if (watchPkg != null && !DeviceReport.lastCandidates.contains(watchPkg)) {      // the owner picked an app the ranking did not examine
            try {
                File src = new File(c.getPackageManager().getApplicationInfo(watchPkg, 0).sourceDir), dir = DeviceReport.newestDir();
                p.step("Reading " + watchPkg + "'s code");
                String note = "chosen by the owner as the walkie-talkie app\n" + DeviceReport.apkClues(src);
                if (dir != null && DeviceReport.copyFile(src, new File(dir, watchPkg + "_" + src.getName()))) note += "  copied " + watchPkg + "_" + src.getName() + "\n";
                DeviceReport.append("Clues in " + watchPkg + "'s code", note);
            } catch (Throwable t) { DeviceReport.append("Clues in " + watchPkg + "'s code", String.valueOf(t)); }
        }
        { File d = DeviceReport.newestDir(); String hint = codeHint(d == null ? "" : DeviceReport.readReport(d));
          if (hint != null) sum.append("the stock app's code names ").append(hint).append('\n'); }   // works without root

        p.step("Press and release the side PTT key a few times now (10 s)");
        DeviceReport.append("PTT key capture", DeviceReport.captureKeys(c, 10));

        String stockAnswers = null;                                              // module replies found in the stock app's own log
        String watchText = "";
        if (watchPkg != null && !svc.isReady() && RootSetup.haveRoot()) {
            lastTrace = null;
            String w = watchStockApp(c, watchPkg, p); watchText = w;
            DeviceReport.append("The stock app at work (" + watchPkg + ", 15 s, receive only)", w);
            sum.append("watched ").append(watchPkg).append(" start up for 15 s\n");
            File dir = DeviceReport.newestDir();
            if (lastTrace != null && dir != null) {                              // the whole sniff beside report.txt (radio IDs blanked)
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, "stock_serial.txt"))) { o.write(SerialTrace.redact(lastTrace).getBytes("UTF-8")); } catch (Throwable ignored) { }
                String proto = SerialTrace.family(SerialTrace.data(lastTrace));
                sum.append("its serial traffic (stock_serial.txt): ").append(proto).append('\n');
            }
            stockAnswers = stockAnswers(w);
            String rel = releaseNote(w);
            if (rel != null) sum.append("USB bridge: ").append(AgoldUsb.WARNING).append("\n  ").append(rel).append('\n');
        } else sum.append("stock app not watched: ").append(watchPkg == null ? "skipped (no app chosen)" : svc.isReady() ? "SignalDeck owns the radio" : "no root").append('\n');

        if (!svc.isReady() && !dp.stockApp.isEmpty() && RootSetup.haveRoot() && !RootSetup.run("pidof " + dp.stockApp + " || true").replaceAll("[^0-9]", "").isEmpty())
            sum.append("stock app was running again before the probe: ").append(stopFully(dp.stockApp)).append('\n');
        if (svc.isReady()) {
            sum.append("radio probe skipped: SignalDeck owns the radio (Hand back first to probe the ports)\n");
        } else {
            File dir = DeviceReport.newestDir();
            java.util.Set<String> named = RadioProbe.namedPorts(dir == null ? "" : DeviceReport.readReport(dir), watchText, dp.supported ? dp.controlPort : null);
            String r = RadioProbe.run(c, s -> p.step("Probe: " + s), false, named);
            DeviceReport.append("Radio probe", r);
            boolean answered = r.contains("answered");
            if (!answered && dp.supported && dp.powerNode != null) {             // the stock app switches the module off when it closes
                String r2 = RadioProbe.run(c, s -> p.step("Probe with power on: " + s), true, named);
                DeviceReport.append("Radio probe (module power switched on first)", r2);
                answered = r2.contains("answered"); r = r2;
            }
            if (r.contains(RadioProbe.AT_MARK))
                sum.append("radio module answered: yes, with AT commands (SA818 class)").append(atWhere(r)).append(": SignalDeck has no engine for this module family yet\n");
            else sum.append("radio module answered the version query: ").append(answered ? "yes" : "no").append('\n');
            if (!named.isEmpty()) sum.append("ports probed: only those the stock app or the profile name (").append(String.join(" ", named)).append(")\n");
            if (!answered && stockAnswers != null)
                sum.append("  but ").append(stockAnswers).append(": the module works; SignalDeck's probe did not reach the port it is on\n");
        }

        if (trial && dp.supported && !svc.isReady()) {
            String t = trialTakeOver(c, svc, p);
            DeviceReport.append("Trial take-over", t);
            sum.append("trial: ").append(t.replace("\n", "\n  ").trim()).append('\n');
        } else if (trial) sum.append("trial take-over skipped: ").append(!dp.supported ? "no profile for this phone yet" : "SignalDeck already owns the radio").append('\n');

        DeviceReport.append("SignalDeck log (personal details removed)", DeviceReport.redactAppLog(svc.recentLog()));
        DeviceReport.append("Summary", sum.toString());
        p.step("Zipping");
        return DeviceReport.zipNewest(c, true);
    }

    /**
     * Listen check (support stage 2, receive only): with SignalDeck owning the radio on a channel the owner chose because
     * stations are heard there, record for {@code seconds} the signal strength, how much of the time something was heard
     * and how much audio arrived. The owner's answer ("did you hear it?") is added by the caller.
     */
    public static String listenCheck(RadioService svc, int seconds, Progress p) throws InterruptedException { return listenCheck(svc, seconds, p, null, 0, false); }

    /** As above, recording up to {@code clipMs} of the received audio into {@code clipFile} (only while heard, if asked). */
    public static String listenCheck(RadioService svc, int seconds, Progress p, File clipFile, int clipMs, boolean onlyHeard) throws InterruptedException {
        StringBuilder r = new StringBuilder();
        ChannelStore.Channel ch = svc.current();
        if (!svc.isReady() || ch == null) return "not run: SignalDeck does not own the radio or no channel is selected\n";
        r.append(String.format(Locale.US, "channel: %s, %s MHz, %s\n", ch.name, ch.freqText(ch.rxFreq), ch.isDigital() ? "DMR" : "analog FM"));
        ClipRecorder clip = null;
        if (clipFile != null) try { clipFile.getParentFile().mkdirs(); clip = new ClipRecorder(clipFile, clipMs); svc.recordClip(clip, onlyHeard); } catch (Throwable t) { r.append("recording failed: ").append(t).append('\n'); }
        long bytes0 = svc.rxAudioBytes(), t0 = System.currentTimeMillis();
        int samples = 0, heard = 0, rmin = 0, rmax = -999; StringBuilder rs = new StringBuilder();
        while (System.currentTimeMillis() - t0 < seconds * 1000L) {
            svc.pollRssi(); Thread.sleep(1000);
            int v = svc.lastRssi(); samples++; if (svc.rxActive()) heard++;
            if (v > -999) { rmax = Math.max(rmax, v); rmin = rmin == 0 ? v : Math.min(rmin, v); }
            if (samples <= 60) rs.append(v).append(svc.rxActive() ? "*" : "").append(' ');
            p.step(String.format(Locale.US, "Listening: %d of %d s, something heard %d s", samples, seconds, heard));
        }
        long bytes = svc.rxAudioBytes() - bytes0;
        if (clip != null) { svc.recordClip(null, false); clip.finish(); r.append("recording ").append(clip.stats()).append(onlyHeard ? " (only while something was heard)" : "").append('\n'); }
        r.append(String.format(Locale.US, "something heard in %d of %d seconds; signal %d … %d dBm\n", heard, samples, rmin, rmax));
        r.append("signal each second (* = heard): ").append(rs.toString().trim()).append('\n');
        r.append(String.format(Locale.US, "receive audio: %d bytes in %d s%s\n", bytes, seconds, svc.audioStats().isEmpty() ? "" : " (" + svc.audioStats() + ")"));
        if (heard * 10 < samples && rmax > -999 && rmax <= -88)                // the radio's squelch sits near -90 dBm (measured on the Ulefone)
            r.append(String.format(Locale.US, "note: the strongest signal was %d dBm, at or below the radio's squelch level (about -90 dBm), so the squelch stayed closed most of the time — that is normal for a signal this weak. Pick a stronger channel, or turn MON on (Intercom) to hear it anyway, and run the check again.\n", rmax));
        return r.toString();
    }

    /**
     * Bench (adb {@code checks --es watch …}): which app the 15 s watch opens, as the Run all checks dialog would offer it.
     * "none" skips the watch, a package name is taken as given, "auto" (or nothing) takes the profile's stock app, else
     * the best-ranked candidate with a launcher icon, else none.
     */
    static String benchWatch(String arg, String profileApp, java.util.List<RadioAppFinder.App> ranked) {
        if ("none".equals(arg)) return null;
        if (arg != null && !arg.isEmpty() && !"auto".equals(arg)) return arg;
        if (profileApp != null && !profileApp.isEmpty()) return profileApp;
        for (RadioAppFinder.App a : ranked) if (a.launcher) return a.pkg;
        return null;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean benchBusy = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Bench (adb): Run all checks with no screen, as {@code DiagReceiver checks}. Progress, the folder and the zip go to the
     * log under the tag "BenchChecks" ("done <folder> <zip>" or "failed …" ends a run), which scripts/w6/run_checks.sh waits for.
     */
    static void benchRun(Context c, RadioService svc, String watchArg, boolean trial, long hostTime) {
        if (!benchBusy.compareAndSet(false, true)) { android.util.Log.w("BenchChecks", "busy: a run is in progress"); return; }
        if (hostTime > 0) DeviceReport.clockOffsetMs = hostTime - System.currentTimeMillis();
        new Thread(() -> {
            try {
                String watch = benchWatch(watchArg, svc.profile().supported ? svc.profile().stockApp : "",
                        "none".equals(watchArg) || svc.isReady() || !RootSetup.haveRoot() ? new java.util.ArrayList<>() : RadioAppFinder.rank(c));
                android.util.Log.i("BenchChecks", "start watch=" + watch + " trial=" + trial + " clockOffsetMs=" + DeviceReport.clockOffsetMs);
                File zip = run(c, svc, trial, watch, t -> android.util.Log.i("BenchChecks", "step " + t));
                c.getSharedPreferences("radio", Context.MODE_PRIVATE).edit().putLong("supportChecks", System.currentTimeMillis()).apply();
                File dir = DeviceReport.newestDir();
                android.util.Log.i("BenchChecks", "done " + (dir == null ? "-" : dir.getAbsolutePath()) + " " + zip.getAbsolutePath());
            } catch (Throwable t) { android.util.Log.w("BenchChecks", "failed " + t); }
            finally { benchBusy.set(false); }
        }, "bench-checks").start();
    }

    /** The first "protocol hint from its code:" in a report, or null. */
    static String codeHint(String report) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^  protocol hint from its code: (.+)$").matcher(report == null ? "" : report);
        return m.find() ? m.group(1).trim() : null;
    }

    /** " on /dev/ttyS2 at 9600 baud" from the probe's AT line, or "". */
    static String atWhere(String probe) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(RadioProbe.AT_MARK) + "( on \\S+ at \\d+ baud)").matcher(probe);
        return m.find() ? m.group(1) : "";
    }

    static final String STOCK_ANSWERS = "module replies in its log: ", NONE_RECOGNISED = "none recognised";

    /** The module-replies line of a stock-app watch, or null when it found none. */
    static String stockAnswers(String watch) {
        int a = watch.indexOf(STOCK_ANSWERS); if (a < 0) return null;
        int from = a + STOCK_ANSWERS.length(), end = watch.indexOf('\n', from);
        String t = watch.substring(from, end < 0 ? watch.length() : end).trim();
        return t.isEmpty() || t.equals(NONE_RECOGNISED) ? null : t;
    }

    private static final String KMSG = "dmesg 2>/dev/null | grep -i -E 'intercom|dmr|interphone|walkie|ptt|pwd|pdt|ttyS|ttyMT' | tail -40";

    /**
     * Start the stock walkie-talkie app the way its icon does, let it power up and listen for 15 s, and record what it does:
     * its own log lines (QuickChat logs every step: "setPttReceive", "initPcmSerialPort", its PCM packets), which serial
     * ports it opens and the line settings it gives them, and the radio driver's kernel messages. Then close it. Nobody
     * presses PTT meanwhile (the progress text says so): this only listens.
     * <p>With the sdtrace helper, the app is stopped first and started under it, so the report sees its start-up from the
     * first system call: the serial port it opens, the line settings, and every byte both ways (the W6's app sent
     * AT+DMOCONNECT, AT+DMOVERQ, AT+DMOSETGROUP, AT+DMOSETVOLUME, each ending in a bare \r). The whole trace is kept in
     * {@link #lastTrace}; the caller writes it beside the report.
     */
    static String watchStockApp(Context c, String pkg, Progress p) throws InterruptedException {
        StringBuilder r = new StringBuilder();
        String comp = RootSetup.run("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " + pkg + " | tail -1").trim();
        if (!comp.contains("/")) return "no launcher activity found for " + pkg + " (" + comp + ")\n";
        String tracer = null;
        try { tracer = AlsaDirectAudio.install(c, "sdtrace"); } catch (Throwable t) { r.append("serial sniff not available: ").append(t.getMessage()).append('\n'); }
        if (tracer != null) r.append("stopped first, so its start-up is seen: ").append(stopFully(pkg)).append('\n');   // a running app is only brought to the front
        String kBefore = RootSetup.run(KMSG);
        RootSetup.run("logcat -c");
        boolean bridged = AgoldUsb.bridged();                                  // Atom XL: the radio takes the USB port while it is on
        if (bridged) r.append("note: ").append(AgoldUsb.WARNING).append('\n');
        p.step("The stock walkie-talkie app is open for 15 s — please do not press PTT" + (bridged ? " (USB to a computer is off meanwhile)" : ""));
        final String[] started = { "" };
        Thread sniff = null;
        if (tracer != null) {                                                   // the sniffer waits for the process, then the app is started
            final String cmd = AlsaDirectAudio.quote(tracer) + " -n " + pkg + " -t 15000 -w 10000 > " + TRACE_FILE + " 2>&1 & sleep 0.3; am start -n " + comp + " 2>&1 | tail -1; wait";
            sniff = new Thread(() -> started[0] = RootSetup.run(cmd), "sdtrace");
            sniff.start();
        } else started[0] = RootSetup.run("am start -n " + comp + " 2>&1 | tail -1");
        Thread.sleep(8000);
        String pid = RootSetup.run("pidof " + pkg + " || true").trim().split("\\s+")[0];
        if (pid.matches("\\d+")) {
            String fds = RootSetup.run("ls -l /proc/" + pid + "/fd 2>/dev/null | grep -o '/dev/[A-Za-z0-9_]*' | sort -u");
            r.append("devices it holds open: ").append(fds.replace('\n', ' ')).append('\n');
            for (String dev : fds.split("\\s+")) if (dev.matches("/dev/tty\\w+"))
                r.append("  ").append(dev).append(": ").append(RootSetup.run("stty -F " + dev + " -a 2>&1 | head -2").replace('\n', ' ')).append('\n');
        } else r.append("not running after 8 s (pid: ").append(pid).append(")\n");
        if (bridged) r.append("USB devices and USB sound cards while the radio is on (root):\n").append(AgoldUsb.whileOn());
        Thread.sleep(7000);
        if (pid.matches("\\d+")) {                                            // again at the end: a daemon-backed app (Armor 3WT) connects only after start-up
            r.append("at the end of the watch it holds: ").append(RootSetup.run("ls -l /proc/" + pid + "/fd 2>/dev/null | grep -o '/dev/[A-Za-z0-9_/]*' | sort -u").replace('\n', ' ')).append('\n');
            r.append("its TCP connections: ").append(DeviceReport.tcpOf(RootSetup.run("cat /proc/net/tcp /proc/net/tcp6 2>/dev/null"), RootSetup.run("ls -l /proc/" + pid + "/fd 2>/dev/null"))).append('\n');
        }
        String log = pid.matches("\\d+") ? RootSetup.run("logcat -d --pid=" + pid + " 2>/dev/null | tail -600") : RootSetup.run("logcat -d 2>/dev/null | tail -400");
        int hexLines = 0; StringBuilder kept = new StringBuilder();
        for (String l : log.split("\n")) {
            if (l.contains("ClassLoader referenced unknown path")) continue;   // framework noise
            if (l.matches(".*[0-9A-Fa-f ,]{120,}.*")) { hexLines++; if (hexLines <= 3) kept.append(l, 0, Math.min(l.length(), 260)).append(" …\n"); continue; }   // data dumps: a sample is enough
            kept.append(l.length() > 260 ? l.substring(0, 260) + " …" : l).append('\n');
        }
        String ans = AgoldFrames.answers(log);
        r.append(STOCK_ANSWERS).append(ans == null ? NONE_RECOGNISED : ans).append('\n');
        r.append("\nits log (").append(hexLines).append(" data-dump lines shortened to 3 samples; personal details removed):\n").append(DeviceReport.redact(kept.toString()));
        String kAfter = RootSetup.run(KMSG);
        r.append("\nradio driver kernel messages that appeared meanwhile:\n").append(newLines(kBefore, kAfter));
        if (sniff != null) {
            sniff.join(15000);
            String trace = RootSetup.run("cat " + TRACE_FILE + " 2>&1; rm -f " + TRACE_FILE);
            lastTrace = trace;
            r.append("\nits serial traffic from its first call (sdtrace; every byte in stock_serial.txt, radio IDs blanked):\n").append(SerialTrace.summary(trace));
            for (String l : trace.split("\n")) if (l.contains("sdtrace:") && (l.contains("attach") || l.contains("not found") || l.contains("not listed"))) r.append("  ").append(l.trim()).append('\n');
        }
        r.append("\nstart ").append(comp).append(": ").append(started[0].trim()).append('\n');
        if (bridged) r.append(RELEASED).append(AgoldUsb.release(pkg).replace("\n", "\n  ").trim()).append('\n');   // a force-stop alone leaves the radio on and USB off
        r.append("closed: ").append(stopFully(pkg)).append('\n');
        return r.toString();
    }

    private static final String TRACE_FILE = "/data/local/tmp/signaldeck_sdtrace.txt";
    static final String RELEASED = "radio released before closing: ";

    /** The release lines of a watch, for the summary, or null when the watch did not release a USB-bridged radio. */
    static String releaseNote(String watch) {
        int a = watch == null ? -1 : watch.indexOf(RELEASED); if (a < 0) return null;
        int end = watch.indexOf("\nclosed: ", a);
        return watch.substring(a + RELEASED.length(), end < 0 ? watch.length() : end).trim();
    }
    /** The last watch's raw serial trace (null without the sniffer); written redacted beside the report. */
    static volatile String lastTrace;

    /**
     * Force-stop an app and make sure it stays stopped: QuickChat restarts itself (its service), and the probe found it
     * holding both ports again right after a single force-stop (2026-10-21). Up to 4 tries over ~6 s.
     */
    static String stopFully(String pkg) throws InterruptedException {
        for (int i = 1; i <= 4; i++) {
            RootSetup.run("am force-stop " + pkg);
            Thread.sleep(1500);
            if (RootSetup.run("pidof " + pkg + " || true").replaceAll("[^0-9]", "").isEmpty()) return "stopped" + (i > 1 ? " (after " + i + " tries: it restarted itself)" : "");
        }
        return "still running after 4 force-stops: " + RootSetup.run("pidof " + pkg + " || true").trim();
    }

    /** Lines of {@code after} that were not in {@code before}. */
    private static String newLines(String before, String after) {
        java.util.HashSet<String> seen = new java.util.HashSet<>(java.util.Arrays.asList(before.split("\n")));
        StringBuilder out = new StringBuilder();
        for (String l : after.split("\n")) if (!l.trim().isEmpty() && !seen.contains(l)) out.append("  ").append(l).append('\n');
        return out.length() == 0 ? "  (none)\n" : out.toString();
    }

    /** Take the radio for a few seconds (receive only), record what happened, give it back. */
    private static String trialTakeOver(Context c, RadioService svc, Progress p) throws InterruptedException {
        StringBuilder r = new StringBuilder();
        p.step("Trial: taking over the radio (receive only)");
        String kBefore = RootSetup.run(KMSG);
        long t0 = System.currentTimeMillis();
        svc.bringUp();
        String link = svc.link();
        while (System.currentTimeMillis() - t0 < 45000) {
            Thread.sleep(500);
            link = svc.link();
            if ("ready".equals(link) || link.startsWith("error") || "no root".equals(link) || "unsupported".equals(link)) break;
            p.step("Trial: " + link);
        }
        long took = System.currentTimeMillis() - t0;
        r.append("result: ").append("ready".equals(link) ? "radio came up in " + took / 1000 + " s" : "did not come up (" + link + " after " + took / 1000 + " s)").append('\n');
        if ("ready".equals(link)) {
            if (!svc.profile().verified) {                              // untested phone: give the audio path something to carry
                p.step("Trial: listening on 446.00625 MHz with the squelch open");
                svc.listenTest(446_006_250, "Listen test 446.00625");
                Thread.sleep(3000);                                     // the module settles after a retune
                // the module only streams audio while its squelch is open, and QuickChat sets a separate global squelch (0x30):
                // the monitor command (0x2F, as the MON key) forces it open, so noise flows with nobody on the channel
                svc.setMonitor(true);
                Thread.sleep(1500);
                r.append("tuned 446.00625 MHz analog, squelch open and monitor on (receive only)\n");
            }
            p.step("Trial: reading signal strength and audio");
            File dir = DeviceReport.newestDir();
            File raw = dir != null ? new File(dir, "rx_audio_raw.bin") : null;
            String cap = raw != null ? svc.captureRxAudio(raw, 5000) : "no report folder";
            StringBuilder rs = new StringBuilder();
            for (int i = 0; i < 5; i++) { svc.pollRssi(); Thread.sleep(1200); rs.append(svc.lastRssi()).append(' '); }
            Thread.sleep(1000);
            r.append("RSSI readings (dBm, -999 = no answer): ").append(rs.toString().trim()).append('\n');
            r.append("receive audio: ").append(cap).append('\n');
            r.append("received audio bytes so far: ").append(svc.rxAudioBytes()).append('\n');
            byte[] head = svc.rxAudioHead();                           // the first bytes the audio port ever delivered
            if (head != null && head.length > 0 && dir != null) {
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, "rx_audio_first.bin"))) { o.write(head); } catch (java.io.IOException e) { r.append("could not save the first bytes: ").append(e).append('\n'); }
                StringBuilder hx = new StringBuilder(); for (int i = 0; i < Math.min(64, head.length); i++) hx.append(String.format(Locale.US, "%02x ", head[i] & 0xFF));
                r.append("first ").append(head.length).append(" bytes saved to rx_audio_first.bin; they start: ").append(hx.toString().trim()).append('\n');
            }
            if (!svc.profile().verified) svc.setMonitor(false);
        }
        p.step("Trial: handing the radio back");
        svc.shutDown(true);
        Thread.sleep(3000);
        r.append("handed back; stock app enabled: ").append(RootSetup.stockAppEnabled(svc.profile())).append('\n');
        r.append("radio driver kernel messages during the trial:\n").append(newLines(kBefore, RootSetup.run(KMSG)));
        r.append(String.format(Locale.US, "(trial ran %d s)\n", (System.currentTimeMillis() - t0) / 1000));
        return r.toString();
    }
}
