/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.winlink;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Winlink e-mail: the mailbox and one session at a time, either over the internet (telnet to the Winlink CMS,
 * server.winlink.org:8772, the same B2F exchange as on the air) or over the radio to a packet gateway (RMS) in
 * AX.25 connected mode. The Winlink password lives only in this app's private settings ("winlink"/"password"); it
 * is never logged, shown, put in a device report or backup, and the session log masks the login response.
 */
public final class Winlink {
    /** The radio side: an AX.25 link to a gateway, and the station's callsign. */
    public interface Radio {
        /** Connect to the gateway and return a byte stream over the link (blocking until connected). */
        B2fClient.Wire open(String gateway, String via, StatusSink status) throws IOException;
        /** End the link (if any). */
        void close();
        /** Base callsign (no SSID), or "" when none is set. */
        String callsign();
        /** Tell the user (new mail). */
        void notifyUser(String title, String text);
        /** A received message carried a SignalDeck form code line (the host rebuilds the form). */
        default void formText(String from, String line) { }
    }
    public interface StatusSink { void status(String s); }
    public interface Listener { void changed(); }

    public static final String CMS_HOST = "server.winlink.org"; public static final int CMS_PORT = 8772;
    public static final String CMS_TARGET = "WL2K", CMS_TELNET_PASSWORD = "CMSTelnet";
    /** Bench only (DiagReceiver, adb): a test CMS on this phone's loopback (adb reverse); 0 = the real CMS. Never saved. */
    public static volatile int benchPort;

    private final Context ctx; private final Radio radio; private final WinlinkStore store; private final SharedPreferences prefs;
    private final StringBuilder log = new StringBuilder();
    private volatile String state = "Not connected"; private volatile boolean busy; private volatile String progress = "";
    private volatile Listener listener; private volatile Socket socket; private volatile boolean viaRadio, cancelled;
    private volatile long lastSession; private volatile String lastResult = "";

    public Winlink(Context ctx, Radio radio) {
        this.ctx = ctx.getApplicationContext(); this.radio = radio;
        store = new WinlinkStore(new File(this.ctx.getFilesDir(), "winlink"));
        prefs = this.ctx.getSharedPreferences("winlink", Context.MODE_PRIVATE);
    }

    public WinlinkStore store() { return store; }
    public String state() { return state; }
    public boolean busy() { return busy; }
    public String progress() { return progress; }
    public synchronized String logText() { return log.toString(); }
    public void setListener(Listener l) { listener = l; }
    public long lastSession() { return lastSession; }
    public String lastResult() { return lastResult; }

    // ---- settings --------------------------------------------------------------------------------------

    public boolean passwordSet() { return !prefs.getString("password", "").isEmpty(); }
    /** Write-only: an empty value keeps the old password; "-" clears it. */
    public void setPassword(String p) { if (p == null || p.isEmpty()) return; prefs.edit().putString("password", "-".equals(p) ? "" : p).apply(); }
    public String grid() { return prefs.getString("grid", ""); }
    public void setGrid(String g) { prefs.edit().putString("grid", g == null ? "" : g.trim()).apply(); }
    public String gateway() { return prefs.getString("gateway", ""); }
    public String via() { return prefs.getString("via", ""); }
    public void setGateway(String g, String v) { prefs.edit().putString("gateway", g.trim().toUpperCase(Locale.US)).putString("via", v.trim().toUpperCase(Locale.US)).apply(); }

    // ---- mail ------------------------------------------------------------------------------------------

    /** Put a new message in the outbox; returns an error text or null. */
    public String compose(String to, String cc, String subject, String text) {
        String call = radio.callsign();
        if (call.isEmpty()) return "Set your callsign first (PKT → Settings)";
        B2Message m = B2Message.create(call, subject.trim().isEmpty() ? "No subject" : subject.trim(), text);
        for (String a : split(to)) m.to.add(B2Message.address(a));
        for (String a : split(cc)) m.cc.add(B2Message.address(a));
        if (m.to.isEmpty()) return "Add at least one recipient (a callsign or an e-mail address)";
        if (m.subject.length() > 128) return "The subject is too long (128 characters at most)";
        if (m.body.length == 0) return "Write something first";
        try { store.save(WinlinkStore.OUTBOX, m); } catch (IOException e) { return "Could not save: " + e.getMessage(); }
        changed();
        return null;
    }
    static List<String> split(String s) {
        List<String> l = new ArrayList<>(); if (s == null) return l;
        for (String p : s.split("[,;\\s]+")) { p = p.trim(); if (!p.isEmpty()) l.add(p); }
        return l;
    }

    // ---- sessions --------------------------------------------------------------------------------------

    /** Exchange mail with the CMS over the internet. Returns an error text or null (started). */
    public String connectInternet() {
        String call = radio.callsign();
        if (call.isEmpty()) return "Set your callsign first (PKT → Settings)";
        if (busy) return "A Winlink session is already running";
        start("internet", () -> {
            Socket s = new Socket(); socket = s;
            setState("Connecting to the Winlink CMS…");
            int bp = benchPort;
            if (bp > 0) note("* bench: test CMS at 127.0.0.1:" + bp);
            s.connect(bp > 0 ? new InetSocketAddress("127.0.0.1", bp) : new InetSocketAddress(CMS_HOST, CMS_PORT), 20_000); s.setSoTimeout(1_000);
            final InputStream in = s.getInputStream(); final OutputStream out = s.getOutputStream();
            B2fClient.Wire w = socketWire(in, out);
            for (;;) {                                                         // telnet login: callsign, then the fixed telnet password
                StringBuilder sb = new StringBuilder(); int c;
                while ((c = w.read(30_000)) != '\r') { if (c != '\n') sb.append((char) c); if (sb.length() > 512) throw new IOException("unexpected answer from the CMS"); }
                String l = sb.toString().trim().toLowerCase(Locale.US);
                if (l.startsWith("callsign")) w.write((call + "\r").getBytes(StandardCharsets.US_ASCII));
                else if (l.startsWith("password")) { w.write((CMS_TELNET_PASSWORD + "\r").getBytes(StandardCharsets.US_ASCII)); break; }
            }
            setState("Connected to the CMS: exchanging mail");
            exchange(w, CMS_TARGET, 60_000);
        });
        return null;
    }

    /** Exchange mail with a packet gateway (RMS) on the current channel. Returns an error text or null (started). */
    public String connectRadio(String gateway, String via) {
        String call = radio.callsign();
        if (call.isEmpty()) return "Set your callsign first (PKT → Settings)";
        if (busy) return "A Winlink session is already running";
        String g = gateway == null ? "" : gateway.trim().toUpperCase(Locale.US);
        if (!g.matches("[A-Z0-9]{3,6}(-[0-9]{1,2})?")) return "Gateway callsign like N0ABC-10";
        setGateway(g, via == null ? "" : via);
        start("radio", () -> {
            viaRadio = true;
            setState("Calling " + g + "…");
            B2fClient.Wire w = radio.open(g, via, this::setState);
            setState("Connected to " + g + ": exchanging mail");
            exchange(w, g.replaceAll("-.*", ""), 240_000);
        });
        return null;
    }

    /** Stop the session now (the link is dropped; nothing half-received is kept). */
    public void cancel() {
        cancelled = true;
        Socket s = socket; if (s != null) try { s.close(); } catch (IOException ignored) { }
        if (viaRadio) radio.close();
    }

    private interface Job { void run() throws Exception; }

    private void start(String how, Job job) {
        busy = true; cancelled = false; viaRadio = false; progress = "";
        synchronized (this) { log.setLength(0); }
        note("* " + how + " session started " + B2Message.formatDate(System.currentTimeMillis()) + " UTC");
        new Thread(() -> {
            String result;
            try { job.run(); result = lastResult; }
            catch (Throwable t) {
                String why = t instanceof SocketTimeoutException ? "no answer in time" : t.getMessage() == null ? t.toString() : t.getMessage();
                result = cancelled ? "Stopped" : "Failed: " + why;
                note("* " + result);
            } finally {
                Socket s = socket; socket = null; if (s != null) try { s.close(); } catch (IOException ignored) { }
                if (viaRadio) radio.close();
                busy = false; progress = "";
            }
            lastResult = result; lastSession = System.currentTimeMillis();
            setState(result);
        }, "winlink").start();
    }

    private void exchange(B2fClient.Wire w, String target, long timeoutMs) throws IOException {
        final int[] got = { 0 };
        B2fClient.Mailbox box = new B2fClient.Mailbox() {
            @Override public List<B2Message> outbox() { List<B2Message> l = new ArrayList<>(); List<WinlinkStore.Item> items = store.list(WinlinkStore.OUTBOX); for (int i = items.size() - 1; i >= 0; i--) l.add(items.get(i).m); return l; }
            @Override public boolean have(String mid) { return store.has(WinlinkStore.INBOX, mid); }
            @Override public void received(B2Message m) throws IOException {
                store.save(WinlinkStore.INBOX, m); got[0]++; changed();
                String form = com.macdmr.radio.ham.Forms.winlinkPart(m.bodyText());
                if (form != null) try { radio.formText(B2Message.shown(m.from), form); } catch (Throwable ignored) { }
            }
            @Override public void sent(String mid) { store.markSent(mid); changed(); }
            @Override public void log(String line) { note(line); }
            @Override public void progress(String what, int done, int total) { progress = what + " · " + done + " of " + total + " bytes"; changed(); }
        };
        String version = "1.0";
        try { version = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        B2fClient c = new B2fClient(w, box, radio.callsign(), target, grid(), version, prefs.getString("password", ""), timeoutMs);
        c.run();
        lastResult = "Done: " + c.sent() + " sent, " + c.received() + " received";
        if (got[0] > 0) radio.notifyUser("Winlink", got[0] == 1 ? "1 new message" : got[0] + " new messages");
    }

    private B2fClient.Wire socketWire(InputStream in, OutputStream out) {
        return new B2fClient.Wire() {
            @Override public int read(long timeoutMs) throws IOException {
                long end = System.currentTimeMillis() + timeoutMs;
                for (;;) {
                    if (cancelled) throw new IOException("stopped");
                    try { int c = in.read(); if (c < 0) throw new IOException("the CMS closed the connection"); return c; }
                    catch (SocketTimeoutException e) { if (System.currentTimeMillis() > end) throw e; }
                }
            }
            @Override public void write(byte[] b) throws IOException { if (cancelled) throw new IOException("stopped"); out.write(b); out.flush(); }
        };
    }

    // ---- state -------------------------------------------------------------------------------------------

    private void setState(String s) { state = s; changed(); }
    private synchronized void note(String line) {
        log.append(line).append('\n');
        if (log.length() > 40_000) log.delete(0, log.length() - 30_000);
        changed();
    }
    private void changed() { Listener l = listener; if (l != null) l.changed(); }
}
