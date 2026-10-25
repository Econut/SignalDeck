/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.macdmr.radio.ChannelStore;
import com.macdmr.radio.RadioService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One channel's history: when it was last heard and what was heard on it (voice, DMR callers, texts, tones, Morse
 * repeater identifications and what they said, packet traffic, recordings, what you sent) plus its alerts, newest
 * first. The lines are the intercom's own history lines, filtered to this channel; a DMR caller is a tap target
 * for the contact.
 */
public final class ChannelHistoryDialog {
    private ChannelHistoryDialog() { }

    public static void show(Context c, RadioService svc, ChannelStore.Channel ch) {
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, ch.name);
        List<Object[]> rows = svc.store().channelHistory(ch.number, 300);
        int total = svc.store().channelHistoryCount(ch.number);
        long lastHeard = 0; String lastWhat = "";
        int voice = 0, texts = 0, tones = 0, ids = 0, pkt = 0, tx = 0;
        for (Object[] r : rows) {
            String kind = (String) r[1], text = (String) r[2];
            boolean heard = kind.equals("rx") || kind.equals("dmr") || kind.equals("sms") || kind.equals("pkt") || kind.equals("tone") || kind.equals("rec") || kind.equals("m17");
            if (heard && lastHeard == 0) { lastHeard = (Long) r[0]; lastWhat = what(kind, text); }
            if (kind.equals("rx") || kind.equals("dmr") || kind.equals("m17")) voice++;
            else if (kind.equals("sms")) texts++;
            else if (kind.equals("tone")) tones++;
            else if (kind.equals("tx")) tx++;
            else if (kind.equals("pkt")) { if (text.contains("Morse") || text.contains("Repeater ID")) ids++; else pkt++; }
        }
        body.addView(Neo.sheetHint(c, (lastHeard > 0 ? "Last heard " + ContactEditor.ago(lastHeard) + " (" + lastWhat + ")." : "Nothing heard on this channel yet.")
                + (total > 0 ? "  " + total + (total == 1 ? " entry" : " entries") + (total > rows.size() ? ", newest " + rows.size() + " shown" : "")
                + ": " + voice + " voice, " + texts + " texts, " + tones + " tones, " + ids + " repeater IDs, " + pkt + " packet, " + tx + " of yours." : "")));
        SimpleDateFormat f = new SimpleDateFormat("EEE MM-dd HH:mm:ss", Locale.US);
        String day = "";
        for (Object[] r : rows) {
            long ts = (Long) r[0]; String kind = (String) r[1], text = (String) r[2]; int rssi = (Integer) r[3], dmrId = (Integer) r[4];
            String dd = new SimpleDateFormat("EEEE, MMM d", Locale.US).format(new Date(ts));
            if (!dd.equals(day)) { day = dd; body.addView(Neo.sheetSection(c, dd)); }
            boolean alert = LastHeardDialog.isAlert(text) || kind.equals("warn") || kind.equals("alert") || kind.equals("busy");
            LinearLayout row = new LinearLayout(c); row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(alert ? Neo.tint(c, Neo.AMBER, 12, 1) : Neo.rowCard(c)); row.setPadding((int) (12 * d), (int) (7 * d), (int) (12 * d), (int) (7 * d));
            TextView t = Neo.label(c, text, alert ? Theme.AMBER : kind.equals("tx") ? Neo.AMBER_LIGHT : kind.equals("dmr") || kind.equals("sms") ? Neo.VIOLET_LIGHT : Theme.CYAN, 14, true);
            row.addView(t);
            String who = dmrId > 0 ? svc.callerName(dmrId) : "";
            row.addView(Neo.label(c, f.format(new Date(ts)) + "  ·  " + what(kind, text) + (rssi > -200 && rssi < 0 ? "  ·  " + rssi + " dBm" : "")
                    + (dmrId > 0 ? "  ·  " + (who.isEmpty() ? "ID " + dmrId : who + " (" + dmrId + ")") + "  ·  tap for the contact" : ""), 0xFFBFC8D0, 12, false));
            if (dmrId > 0) { final int id = dmrId; row.setClickable(true); row.setOnClickListener(v -> ContactEditor.open(c, svc, ContactEditor.Identity.dmr(id), null)); }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (int) (5 * d);
            body.addView(row, lp);
        }
        if (rows.isEmpty()) body.addView(Neo.label(c, "Voice, callers, texts, tones, repeater identifications, packet traffic and your own transmissions on this channel will be listed here.", 0xFFBFC8D0, 14, false));
        final AlertDialog[] self = new AlertDialog[1];
        if (total > 0) {
            TextView clear = Neo.button(c, "Clear this channel's history", "stop", Neo.RED); clear.setTextSize(14);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); bl.setMargins(0, (int) (12 * d), 0, 0);
            body.addView(clear, bl);
            clear.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Clear the history of " + ch.name + "?").setMessage("Captions are kept; everything else heard on this channel is removed.")
                    .setPositiveButton("Clear", (dd, w) -> { svc.store().clearChannelHistory(ch.number); if (self[0] != null) self[0].dismiss(); }).setNegativeButton("Cancel", null).show());
        }
        self[0] = Neo.showSheet(c, body);
    }

    /** What a history line was, from its kind and wording. */
    static String what(String kind, String text) {
        if (text == null) text = "";
        switch (kind) {
            case "rx": return "voice";
            case "dmr": return "DMR voice";
            case "m17": return "M17 voice";
            case "sms": return "DMR text";
            case "tone": return "tone";
            case "rec": return "recording";
            case "tx": return "transmitted";
            case "pkt": return text.contains("Repeater ID") ? "repeater identification" : text.contains("Morse") ? "Morse" : text.contains("APRS") || text.startsWith("📡") ? "packet" : text.contains("SSTV") ? "SSTV" : text.contains("DTMF") ? "DTMF" : "packet";
            case "warn": case "alert": case "busy": return "alert";
            case "car": return "car";
            case "cov": return "coverage";
            case "chan": return "channel";
            default: return kind;
        }
    }
}
