/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.macdmr.radio.ChannelStore;
import com.macdmr.radio.RadioService;
import com.macdmr.radio.winlink.B2Message;
import com.macdmr.radio.winlink.Winlink;
import com.macdmr.radio.winlink.WinlinkStore;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** PKT → Winlink e-mail: inbox, outbox and sent mail, compose, and a session over the internet or a radio gateway. */
public final class WinlinkDialog {
    private WinlinkDialog() { }
    static final String ISSUES = "https://github.com/IIMacGyverII/SignalDeck/issues";

    public static void show(Context c, RadioService svc) {
        final Winlink w = svc.winlink();
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "Winlink e-mail");
        body.addView(Neo.sheetHint(c, "E-mail by radio: write messages to callsigns or internet addresses, then connect to send what is in the outbox and collect what is waiting for you. Over the radio through a Winlink packet gateway (RMS) on 2 m or 70 cm, or over the internet when you have it."));
        LinearLayout beta = Neo.tile(c, "alert", Neo.AMBER, "New: not yet tested with the real Winlink system",
                "Please tell us if it works for you: tap here to report it on GitHub.");
        beta.setOnClickListener(v -> { try { c.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ISSUES))); } catch (Exception e) { toast(c, ISSUES); } });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); bl.bottomMargin = (int) (8 * d);
        body.addView(beta, bl);
        final TextView state = Neo.label(c, "", Theme.CYAN, 14, true); body.addView(state);
        final TextView prog = Neo.label(c, "", Neo.MUTED, 12.5f, false); body.addView(prog);
        final AlertDialog[] self = { null }; final Runnable[] later = { null };
        LinearLayout r1 = new LinearLayout(c); body.addView(r1);
        final TextView net = btn(c, r1, "Connect: internet", "globe", Neo.CYAN), rf = btn(c, r1, "Connect: radio", "radio", Neo.GREEN);
        LinearLayout r2 = new LinearLayout(c); body.addView(r2);
        btn(c, r2, "New message", "plus", Neo.AMBER).setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); compose(c, svc, "", "", "", ""); });
        btn(c, r2, "Gateways", "map", Neo.GREEN).setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); gateways(c, svc); });
        btn(c, r2, "Settings", "menu", Neo.VIOLET).setOnClickListener(v -> settings(c, svc, () -> { if (later[0] != null) later[0].run(); }));
        TextView logB = Neo.button(c, "Session log", "report", Neo.MUTED); logB.setTextSize(14);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * d)); ll.topMargin = (int) (6 * d);
        body.addView(logB, ll);
        logB.setOnClickListener(v -> sessionLog(c, svc));
        final LinearLayout lists = new LinearLayout(c); lists.setOrientation(LinearLayout.VERTICAL); body.addView(lists);

        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] wasBusy = { w.busy() };
        final Runnable refresh = () -> {
            boolean busy = w.busy();
            state.setText(w.state());
            prog.setText(busy ? w.progress() : (w.passwordSet() ? "" : "No Winlink password set yet (Settings): needed for your mailbox once your callsign is registered."));
            net.setText(busy ? "Stop" : "Connect: internet"); rf.setEnabled(!busy); rf.setAlpha(busy ? 0.4f : 1f);
            if (wasBusy[0] != busy || lists.getChildCount() == 0) { wasBusy[0] = busy; fillLists(c, svc, lists, self); }
        };
        final boolean[] queued = { false };                                    // at most four refreshes a second, the last one never dropped
        w.setListener(() -> h.post(() -> { if (queued[0]) return; queued[0] = true; h.postDelayed(() -> { queued[0] = false; refresh.run(); }, 250); }));
        net.setOnClickListener(v -> {
            if (w.busy()) { w.cancel(); return; }
            String e = w.connectInternet(); if (e != null) toast(c, e);
            refresh.run();
        });
        rf.setOnClickListener(v -> radioConnect(c, svc, refresh));
        later[0] = refresh;
        refresh.run();
        self[0] = Neo.showSheet(c, body);
        self[0].setOnDismissListener(x -> w.setListener(null));
    }

    private static void fillLists(Context c, RadioService svc, LinearLayout lists, AlertDialog[] self) {
        lists.removeAllViews();
        WinlinkStore st = svc.winlink().store();
        float d = Theme.dp(c);
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault());
        String[][] folders = { { WinlinkStore.INBOX, "Inbox", "inbox" }, { WinlinkStore.OUTBOX, "Outbox (goes out at the next connection)", "send" }, { WinlinkStore.SENT, "Sent", "check" } };
        for (String[] fo : folders) {
            List<WinlinkStore.Item> items = st.list(fo[0]);
            lists.addView(Neo.sheetSection(c, fo[1] + (items.isEmpty() ? ": empty" : " (" + items.size() + ")")));
            int shown = 0;
            for (WinlinkStore.Item it : items) {
                if (++shown > 40) { lists.addView(Neo.sheetHint(c, (items.size() - 40) + " older not shown.")); break; }
                boolean in = WinlinkStore.INBOX.equals(fo[0]); boolean unread = in && !st.isRead(it.m.mid);
                String who = in ? "From " + B2Message.shown(it.m.from) : "To " + shownList(it.m.to);
                long t = it.m.time();
                String sub = who + (t > 0 ? " · " + f.format(new Date(t)) : "") + (it.m.files.isEmpty() ? "" : " · 📎 " + it.m.files.size());
                LinearLayout tile = Neo.tile(c, fo[2], unread ? Neo.GREEN : in ? Neo.CYAN : Neo.MUTED, (unread ? "● " : "") + it.m.subject, sub);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (int) (6 * d);
                lists.addView(tile, lp);
                tile.setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); view(c, svc, it); });
            }
        }
    }

    static String shownList(List<String> l) { StringBuilder b = new StringBuilder(); for (String a : l) { if (b.length() > 0) b.append(", "); b.append(B2Message.shown(a)); } return b.length() == 0 ? "—" : b.toString(); }

    /** The imported gateway list, nearest first: tune and connect, or hand the frequencies to the packet finder. */
    static void gateways(Context c, RadioService svc) {
        final Winlink w = svc.winlink();
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "Winlink gateways");
        body.addView(Neo.sheetHint(c, "1. Open winlink.org opens Winlink's gateway list in your browser: tap RMS List, choose Packet, then CSV File to download it (if the buttons are cut off, turn on the browser's Desktop site). 2. Come back and tap Import the CSV: the file you just downloaded is at the top of Recent, or in Downloads. SignalDeck keeps the 1200-baud packet gateways this radio can tune and sorts them by distance from the phone's last known position (worked out on the phone; it is not sent anywhere). Tap one to tune it and connect."));
        LinearLayout r = new LinearLayout(c); body.addView(r);
        btn(c, r, "Open winlink.org", "globe", Neo.CYAN).setOnClickListener(v -> { try { c.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://winlink.org/RMSChannels"))); } catch (Exception e) { toast(c, "https://winlink.org/RMSChannels"); } });
        btn(c, r, "Import the CSV", "import", Neo.GREEN).setOnClickListener(v -> {
            if (!(c instanceof com.macdmr.radio.MainActivity)) { toast(c, "Open this from the app"); return; }
            ((com.macdmr.radio.MainActivity) c).pickDocument(uri -> new Thread(() -> {
                String msg;
                try (java.io.InputStream in = c.getContentResolver().openInputStream(uri)) {
                    byte[] b = in.readAllBytes(); if (b.length > 8 << 20) throw new IllegalArgumentException("The file is too large");
                    msg = w.importGateways(new String(b, java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) { msg = "Could not import: " + e.getMessage(); }
                final String m = msg;
                new Handler(Looper.getMainLooper()).post(() -> { new AlertDialog.Builder(c).setTitle("Winlink gateways").setMessage(m).setPositiveButton("OK", null).show(); gateways(c, svc); });
            }).start());
        });
        java.util.List<com.macdmr.radio.winlink.RmsList.Gateway> l = w.gateways();
        long at = w.gatewaysImportedAt();
        body.addView(Neo.sheetSection(c, l.isEmpty() ? "No list imported yet" : l.size() + " gateways \u00B7 imported " + new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(at))));
        final AlertDialog[] self = { null };
        if (!l.isEmpty()) {
            TextView pf = Neo.button(c, "Packet finder: listen on the nearest 10", "search", Neo.VIOLET); pf.setTextSize(14);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * d)); pl.bottomMargin = (int) (8 * d);
            body.addView(pf, pl);
            pf.setOnClickListener(v -> {
                java.util.LinkedHashSet<String> fr = new java.util.LinkedHashSet<>();
                for (com.macdmr.radio.winlink.RmsList.Gateway g : l) { if (fr.size() >= 10) break; fr.add(String.format(Locale.US, "%.4f", g.hz / 1e6)); }
                String e = svc.finder().setSettings(String.join(", ", fr), 5, 2);
                if (e != null) { toast(c, e); return; }
                if (self[0] != null) self[0].dismiss(); PacketFinderDialog.show(c, svc);
                toast(c, "Packet finder set to " + fr.size() + " gateway frequencies, 5 min each");
            });
        }
        int shown = 0;
        for (com.macdmr.radio.winlink.RmsList.Gateway g : l) {
            if (++shown > 60) { body.addView(Neo.sheetHint(c, (l.size() - 60) + " farther ones not shown.")); break; }
            String dist = g.km < 0 ? "" : (Locale.getDefault().getCountry().equals("US") || Locale.getDefault().getCountry().equals("GB") ? Math.round(g.km * 0.621371) + " mi " : Math.round(g.km) + " km ") + com.macdmr.radio.ham.Maidenhead.compass(g.bearing) + " \u00B7 ";
            LinearLayout t = Neo.tile(c, "mail", Neo.GREEN, g.call, dist + String.format(Locale.US, "%.4f MHz", g.hz / 1e6) + (g.hours.isEmpty() || g.hours.equals("00-23") ? "" : " \u00B7 hours " + g.hours) + (g.qth.isEmpty() ? "" : "\n" + g.qth));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (int) (6 * d);
            body.addView(t, lp);
            t.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle(g.call).setItems(new String[] { "Tune " + String.format(Locale.US, "%.4f", g.hz / 1e6) + " and connect", "Tune and listen first", "Use it as my gateway" }, (dd, which) -> {
                w.setGateway(g.call, "");
                if (which <= 1) { ChannelStore.Channel ch = new ChannelStore.Channel(); ch.type = 1; ch.rxFreq = ch.txFreq = g.hz; ch.band = 1; ch.power = 1; ch.sq = 2; ch.name = g.call; svc.applyVfo(ch, true); }
                if (self[0] != null) self[0].dismiss();
                if (which == 0) { new Handler(Looper.getMainLooper()).postDelayed(() -> { String e = w.connectRadio(g.call, ""); if (e != null) toast(c, e); }, 4500); toast(c, "Tuning " + String.format(Locale.US, "%.4f MHz", g.hz / 1e6) + ", connecting in a few seconds"); }
                show(c, svc);
            }).show());
        }
        if (!l.isEmpty()) {
            TextView clr = Neo.button(c, "Remove the list", "stop", Neo.RED); clr.setTextSize(14);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * d)); cl.topMargin = (int) (6 * d);
            body.addView(clr, cl);
            clr.setOnClickListener(v -> { w.clearGateways(); if (self[0] != null) self[0].dismiss(); gateways(c, svc); });
        }
        self[0] = Neo.showSheet(c, body);
    }

    private static void radioConnect(Context c, RadioService svc, Runnable refresh) {
        final Winlink w = svc.winlink();
        float d = Theme.dp(c);
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); v.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), 0);
        TextView hint = Neo.label(c, "Tune the gateway's channel first (an analog channel; most packet gateways use 1200 baud on 2 m or 70 cm, listed at winlink.org under RMS channels). A session takes minutes: every transmission starts with a 1.6-second lead-in and frames lost on the air are sent again.", Neo.MUTED, 13, false);
        v.addView(hint);
        final EditText gw = new EditText(c); gw.setHint("Gateway, e.g. N0ABC-10"); gw.setText(w.gateway()); gw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); gw.setTextColor(Neo.TEXT); v.addView(gw);
        final EditText via = new EditText(c); via.setHint("Via digipeaters (optional)"); via.setText(w.via()); via.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); via.setTextColor(Neo.TEXT); v.addView(via);
        new AlertDialog.Builder(c).setTitle("Connect to a radio gateway").setView(v)
                .setPositiveButton("Connect", (dd, x) -> { String e = w.connectRadio(gw.getText().toString(), via.getText().toString()); if (e != null) toast(c, e); refresh.run(); })
                .setNegativeButton("Cancel", null).show();
    }

    private static void settings(Context c, RadioService svc, Runnable after) {
        final Winlink w = svc.winlink();
        float d = Theme.dp(c);
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); v.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), 0);
        v.addView(Neo.label(c, "Your callsign comes from PKT → Settings. The first connection with a new callsign creates its Winlink account; set its password at winlink.org and enter it here. It is kept only in SignalDeck's private settings, never shown, logged, put in a device report or a backup.", Neo.MUTED, 13, false));
        final EditText pw = new EditText(c); pw.setHint(w.passwordSet() ? "Password (set; leave empty to keep, - to clear)" : "Winlink password"); pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); pw.setTextColor(Neo.TEXT); v.addView(pw);
        final EditText grid = new EditText(c); grid.setHint("Grid square (optional, e.g. JO01)"); grid.setText(w.grid()); grid.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); grid.setTextColor(Neo.TEXT); v.addView(grid);
        v.addView(Neo.label(c, "The grid square is sent to the gateway at login (optional).", Neo.MUTED, 12, false));
        new AlertDialog.Builder(c).setTitle("Winlink settings").setView(v)
                .setPositiveButton("Save", (dd, x) -> {
                    String g = grid.getText().toString().trim();
                    if (!g.isEmpty() && !g.matches("(?i)[A-R]{2}[0-9]{2}([A-X]{2})?")) { toast(c, "Grid square like JO01 or JO01ab"); return; }
                    w.setPassword(pw.getText().toString()); w.setGrid(g); toast(c, "Saved"); after.run();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private static void sessionLog(Context c, RadioService svc) {
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "Winlink session log");
        String t = svc.winlink().logText();
        TextView tv = Neo.text(c, t.isEmpty() ? "No session yet." : t, 0xFFCFE8D8, 12, Neo.mono(c));
        tv.setBackground(Neo.rowCard(c)); tv.setPadding((int) (10 * d), (int) (8 * d), (int) (10 * d), (int) (8 * d)); tv.setTextIsSelectable(true);
        body.addView(tv);
        Neo.showSheet(c, body);
    }

    static void compose(Context c, RadioService svc, String to, String cc, String subject, String text) {
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "New Winlink message");
        body.addView(Neo.sheetHint(c, "To: callsigns (they read it in their Winlink mailbox) or e-mail addresses, separated by commas. Plain text; keep it short on the radio. It is stored in the outbox and goes out at your next connection."));
        final EditText toE = field(c, body, "To", to, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false);
        final EditText ccE = field(c, body, "Cc (optional)", cc, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false);
        final EditText subE = field(c, body, "Subject", subject, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
        final EditText txt = field(c, body, "Message", text, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true);
        final TextView size = Neo.label(c, "", Neo.MUTED, 12, false); body.addView(size);
        txt.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int e) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int e) { }
            @Override public void afterTextChanged(android.text.Editable s) { size.setText(s.length() + " characters"); }
        });
        TextView save = Neo.button(c, "Put in the outbox", "send", Neo.GREEN);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * d)); sl.topMargin = (int) (10 * d);
        body.addView(save, sl);
        final AlertDialog dlg = Neo.showSheet(c, body);
        save.setOnClickListener(v -> {
            String e = svc.winlink().compose(toE.getText().toString(), ccE.getText().toString(), subE.getText().toString(), txt.getText().toString());
            if (e != null) { toast(c, e); return; }
            toast(c, "In the outbox"); dlg.dismiss(); show(c, svc);
        });
    }

    private static void view(Context c, RadioService svc, WinlinkStore.Item it) {
        float d = Theme.dp(c);
        final B2Message m = it.m; final Winlink w = svc.winlink();
        if (WinlinkStore.INBOX.equals(it.folder)) w.store().markRead(m.mid);
        LinearLayout body = Neo.sheetBody(c, m.subject);
        long t = m.time();
        body.addView(Neo.sheetHint(c, "From " + B2Message.shown(m.from) + "\nTo " + shownList(m.to) + (m.cc.isEmpty() ? "" : "\nCc " + shownList(m.cc))
                + (t > 0 ? "\n" + new SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(t)) : "") + "\nMessage ID " + m.mid));
        TextView tv = Neo.text(c, m.bodyText().replace("\r\n", "\n"), Neo.TEXT, 15, Neo.uiMedium(c)); tv.setTextIsSelectable(true);
        tv.setBackground(Neo.rowCard(c)); tv.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        body.addView(tv);
        for (B2Message.Attachment a : m.files) {
            LinearLayout tile = Neo.tile(c, "import", Neo.CYAN, "📎 " + a.name, (a.data.length < 1024 ? a.data.length + " bytes" : (a.data.length + 1023) / 1024 + " KB") + " · tap to open or share");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = (int) (6 * d);
            body.addView(tile, lp);
            tile.setOnClickListener(v -> shareAttachment(c, a));
        }
        final AlertDialog[] self = { null };
        LinearLayout r = new LinearLayout(c); body.addView(r);
        if (WinlinkStore.INBOX.equals(it.folder)) {
            btn(c, r, "Reply", "messages", Neo.GREEN).setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); compose(c, svc, B2Message.shown(m.from), "", m.subject.startsWith("Re:") ? m.subject : "Re: " + m.subject, "\n\n--- " + B2Message.shown(m.from) + " wrote:\n" + m.bodyText().replace("\r\n", "\n")); });
        }
        btn(c, r, "Forward", "send", Neo.CYAN).setOnClickListener(v -> { if (self[0] != null) self[0].dismiss(); compose(c, svc, "", "", "Fw: " + m.subject, "\n\n--- Forwarded message from " + B2Message.shown(m.from) + ":\n" + m.bodyText().replace("\r\n", "\n")); });
        btn(c, r, "Delete", "stop", Neo.RED).setOnClickListener(v -> new AlertDialog.Builder(c).setTitle(WinlinkStore.OUTBOX.equals(it.folder) ? "Delete this message? It will not be sent." : "Delete this message?")
                .setPositiveButton("Delete", (dd, x) -> { w.store().delete(it.folder, m.mid); if (self[0] != null) self[0].dismiss(); show(c, svc); }).setNegativeButton("Cancel", null).show());
        self[0] = Neo.showSheet(c, body);
    }

    private static void shareAttachment(Context c, B2Message.Attachment a) {
        try {
            File dir = new File(c.getCacheDir(), "share"); dir.mkdirs();
            String name = a.name.replaceAll("[\\\\/:*?\"<>|]", "_"); if (name.isEmpty()) name = "attachment";
            File f = new File(dir, name);
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(a.data); }
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(c, c.getPackageName() + ".files", f);
            String type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "");
            if (type == null) type = "application/octet-stream";
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(u, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent send = new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent ch = Intent.createChooser(send, a.name); ch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { view });
            c.startActivity(ch);
        } catch (Exception e) { toast(c, "Could not open: " + e.getMessage()); }
    }

    private static EditText field(Context c, LinearLayout target, String label, String value, int type, boolean multi) {
        float d = Theme.dp(c);
        LinearLayout r = new LinearLayout(c); r.setOrientation(LinearLayout.VERTICAL); r.setBackground(Neo.rowCard(c)); r.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (4 * d));
        r.addView(Neo.label(c, label, 0xFF9AA4AE, 12, false));
        EditText e = new EditText(c); e.setText(value); e.setInputType(type); e.setTextSize(16); e.setTextColor(Neo.TEXT); e.setBackground(null);
        if (multi) { e.setMinLines(5); e.setGravity(android.view.Gravity.TOP); } else e.setSingleLine(true);
        r.addView(e);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = (int) (6 * d);
        target.addView(r, lp);
        return e;
    }

    private static TextView btn(Context c, LinearLayout row, String label, String icon, int accent) {
        float d = Theme.dp(c);
        TextView b = Neo.button(c, label, icon, accent); b.setTextSize(14); Neo.fit(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (48 * d), 1f); lp.setMargins((int) (3 * d), (int) (8 * d), (int) (3 * d), 0);
        row.addView(b, lp);
        return b;
    }
    private static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }
    @SuppressWarnings("unused") private static void gone(View v) { v.setVisibility(View.GONE); }
}
