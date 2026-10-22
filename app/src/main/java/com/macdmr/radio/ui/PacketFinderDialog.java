/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.macdmr.radio.ChannelStore;
import com.macdmr.radio.PacketFinder;
import com.macdmr.radio.RadioService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** PKT → Packet finder: listen across the packet frequencies for BBSs, nodes and Winlink gateways (receive only). */
public final class PacketFinderDialog {
    private PacketFinderDialog() { }

    public static void show(Context c, RadioService svc) {
        final PacketFinder pf = svc.finder();
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "Packet finder");
        body.addView(Neo.sheetHint(c, "Finds the packet stations around you: Winlink gateways, BBSs and nodes. SignalDeck only listens (it never transmits), one frequency after another, and lists every station it hears that is not plain APRS. Nodes identify only every 10 to 30 minutes, so let it run for an hour or two (or schedule it for the night). The radio goes back to its channel afterwards."));
        final TextView status = Neo.label(c, "", Theme.CYAN, 14, true); body.addView(status);
        final TextView sub = Neo.label(c, "", Neo.MUTED, 12.5f, false); body.addView(sub);
        LinearLayout r1 = new LinearLayout(c); body.addView(r1);
        final TextView go = btn(c, r1, "Start", "search", Neo.GREEN), set = btn(c, r1, "Settings", "menu", Neo.VIOLET);
        final LinearLayout list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL); body.addView(list);
        final AlertDialog[] self = { null };
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] shown = { -1 };
        final Runnable[] refresh = { null };
        refresh[0] = () -> {
            boolean on = pf.running();
            status.setText(pf.status());
            sub.setText(on ? "Until " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(pf.endsAt())) + " · " + pf.dwellMin() + " min on each of " + pf.freqs().size() + " frequencies"
                    : pf.freqs().size() + " frequencies, " + pf.dwellMin() + " min each, for " + pf.hours() + " h (Settings)");
            go.setText(on ? "Stop" : "Start");
            List<PacketFinder.Station> st = pf.stations();
            if (st.size() != shown[0] || on) { shown[0] = st.size(); fill(c, svc, list, st, self); }
            if (self[0] != null && self[0].isShowing()) h.postDelayed(refresh[0], 3000);
        };
        go.setOnClickListener(v -> {
            if (pf.running()) { pf.stop(); h.postDelayed(refresh[0], 800); return; }
            String e = pf.start(0); if (e != null) { Toast.makeText(c, e, Toast.LENGTH_LONG).show(); return; }
            h.postDelayed(refresh[0], 800);
        });
        set.setOnClickListener(v -> settings(c, pf, refresh[0]));
        self[0] = Neo.showSheet(c, body);
        refresh[0].run();
    }

    private static void fill(Context c, RadioService svc, LinearLayout list, List<PacketFinder.Station> st, AlertDialog[] self) {
        float d = Theme.dp(c);
        list.removeAllViews();
        list.addView(Neo.sheetSection(c, st.isEmpty() ? "Nothing heard yet" : "Heard (" + st.size() + ")"));
        SimpleDateFormat f = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
        for (PacketFinder.Station s : st) {
            String icon = PacketFinder.GATEWAY.equals(s.kind) ? "mail" : PacketFinder.BBS.equals(s.kind) ? "messages" : PacketFinder.NODE.equals(s.kind) ? "zone" : "radio";
            int col = PacketFinder.GATEWAY.equals(s.kind) ? Neo.GREEN : PacketFinder.BBS.equals(s.kind) ? Neo.AMBER : PacketFinder.NODE.equals(s.kind) ? Neo.CYAN : Neo.MUTED;
            String line = s.kind + " · " + String.format(Locale.US, "%.3f MHz", s.hz / 1e6) + (s.bestDbm > -200 ? " · " + s.bestDbm + " dBm" : "") + " · " + s.count + "×, last " + f.format(new Date(s.last))
                    + (s.note.isEmpty() ? "" : "\n" + s.note);
            LinearLayout t = Neo.tile(c, icon, col, s.call, line);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (int) (6 * d);
            list.addView(t, lp);
            t.setOnClickListener(v -> act(c, svc, s, self));
        }
        if (!st.isEmpty()) {
            TextView clr = Neo.button(c, "Clear the list", "stop", Neo.RED); clr.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * d)); lp.topMargin = (int) (6 * d);
            list.addView(clr, lp);
            clr.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Clear the stations found?").setPositiveButton("Clear", (dd, w) -> { svc.finder().clear(); if (self[0] != null) self[0].dismiss(); show(c, svc); }).setNegativeButton("Cancel", null).show());
        }
    }

    /** Tune to it, then open the terminal or Winlink with its callsign filled in. */
    private static void act(Context c, RadioService svc, PacketFinder.Station s, AlertDialog[] self) {
        if (svc.finder().running()) { Toast.makeText(c, "Stop the search first", Toast.LENGTH_SHORT).show(); return; }
        String[] items = PacketFinder.GATEWAY.equals(s.kind)
                ? new String[] { "Tune " + String.format(Locale.US, "%.3f", s.hz / 1e6) + " and open Winlink", "Tune and open the packet terminal", "Just tune to it" }
                : new String[] { "Tune " + String.format(Locale.US, "%.3f", s.hz / 1e6) + " and open the packet terminal", "Just tune to it" };
        new AlertDialog.Builder(c).setTitle(s.call).setItems(items, (dd, w) -> {
            ChannelStore.Channel ch = new ChannelStore.Channel(); ch.type = 1; ch.rxFreq = ch.txFreq = s.hz; ch.band = 1; ch.power = 0; ch.sq = 2; ch.name = s.call;
            svc.applyVfo(ch, true);
            String pick = items[w];
            if (self[0] != null) self[0].dismiss();
            if (pick.contains("Winlink")) { svc.winlink().setGateway(s.call, ""); WinlinkDialog.show(c, svc); }
            else if (pick.contains("terminal")) { c.getSharedPreferences("packet", Context.MODE_PRIVATE).edit().putString("termCall", s.call).putString("termVia", "").apply(); TerminalDialog.show(c, svc); }
            Toast.makeText(c, "VFO on " + String.format(Locale.US, "%.3f MHz", s.hz / 1e6) + " (low power)", Toast.LENGTH_SHORT).show();
        }).show();
    }

    private static void settings(Context c, PacketFinder pf, Runnable after) {
        float d = Theme.dp(c);
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); v.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), 0);
        v.addView(Neo.label(c, "Frequencies in MHz, separated by commas (empty: the usual 2 m packet frequencies 144.910–145.090). Add your area's 70 cm packet frequencies if you know them.", Neo.MUTED, 13, false));
        final EditText fr = new EditText(c); fr.setText(pf.freqText()); fr.setTextColor(Neo.TEXT); fr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); v.addView(fr);
        final EditText dw = new EditText(c); dw.setHint("Minutes on each frequency"); dw.setText(String.valueOf(pf.dwellMin())); dw.setInputType(InputType.TYPE_CLASS_NUMBER); dw.setTextColor(Neo.TEXT); v.addView(dw);
        final EditText hr = new EditText(c); hr.setHint("For how many hours"); hr.setText(String.valueOf(pf.hours())); hr.setInputType(InputType.TYPE_CLASS_NUMBER); hr.setTextColor(Neo.TEXT); v.addView(hr);
        v.addView(Neo.label(c, "Minutes on each frequency / how many hours in total.", Neo.MUTED, 12, false));
        new AlertDialog.Builder(c).setTitle("Packet finder settings").setView(v)
                .setPositiveButton("Save", (dd, w) -> {
                    int dwell, hours; try { dwell = Integer.parseInt(dw.getText().toString().trim()); hours = Integer.parseInt(hr.getText().toString().trim()); } catch (NumberFormatException e) { Toast.makeText(c, "Numbers only", Toast.LENGTH_SHORT).show(); return; }
                    String e = pf.setSettings(fr.getText().toString(), dwell, hours);
                    Toast.makeText(c, e == null ? "Saved" : e, Toast.LENGTH_LONG).show(); after.run();
                })
                .setNeutralButton("Defaults", (dd, w) -> { pf.setSettings("", 3, 2); after.run(); })
                .setNegativeButton("Cancel", null).show();
    }

    private static TextView btn(Context c, LinearLayout row, String label, String icon, int accent) {
        float d = Theme.dp(c);
        TextView b = Neo.button(c, label, icon, accent); b.setTextSize(14); Neo.fit(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (48 * d), 1f); lp.setMargins((int) (3 * d), (int) (8 * d), (int) (3 * d), 0);
        row.addView(b, lp);
        return b;
    }
}
