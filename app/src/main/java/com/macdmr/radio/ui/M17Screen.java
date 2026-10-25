/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.macdmr.radio.M17Station;
import com.macdmr.radio.RadioService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** M17 receive: who is talking now (source, destination, their message), how clean it is, and what was heard. */
public final class M17Screen extends ModeScreen {
    private final M17Station st;
    private final TextView now, who, meta, stats, note;
    private final LinearLayout list;
    private int shown = -1;

    public M17Screen(Context c, RadioService svc, Runnable onClose) {
        super(c, svc, "M17", "voice", Neo.VIOLET, onClose);
        st = svc.m17();
        ScrollView sv = new ScrollView(c); sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(c); body.setOrientation(VERTICAL); body.setPadding(dp(14), dp(8), dp(14), dp(10));
        sv.addView(body);
        now = Neo.text(c, "", Neo.VIOLET_LIGHT, 20, Neo.display(c)); now.setGravity(Gravity.CENTER); now.setPadding(0, dp(10), 0, dp(4)); body.addView(now);
        who = Neo.text(c, "", Neo.TEXT, 26, Neo.mono(c)); who.setGravity(Gravity.CENTER); body.addView(who);
        meta = Neo.text(c, "", Neo.AMBER_LIGHT, 15, Neo.uiMedium(c)); meta.setGravity(Gravity.CENTER); meta.setPadding(0, dp(4), 0, 0); body.addView(meta);
        stats = Neo.text(c, "", Neo.MUTED, 13, Neo.mono(c)); stats.setGravity(Gravity.CENTER); stats.setPadding(0, dp(6), 0, dp(10)); body.addView(stats);
        body.addView(Neo.sheetSection(c, "Heard"));
        list = new LinearLayout(c); list.setOrientation(VERTICAL); body.addView(list);
        note = Neo.sheetHint(c, ""); body.addView(note);
        content.addView(sv);
        LinearLayout r = buttonRow();
        if (svc.m17CanSend()) {
            android.widget.TextView talk = button(r, "Hold to talk", "voice", Neo.RED, null);
            talk.setOnTouchListener((v, e) -> {
                int a = e.getAction();
                if (a == android.view.MotionEvent.ACTION_DOWN) { svc.m17Voice(true); return true; }
                if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) { svc.m17Voice(false); return true; }
                return false;
            });
        }
        button(r, "Stop M17", "stop", Neo.RED, v -> { svc.setM17(false); close(); });
        refresh();
    }

    @Override protected boolean stillRunning() { return st.running(); }

    @Override protected void refresh() {
        com.macdmr.radio.ChannelStore.Channel ch = svc.current();
        setSubtitle("Receiving M17 on " + (ch != null ? "Ch " + ch.number + " · " + ch.name : "this channel"));
        M17Station.Heard h = st.current();
        if (h != null) {
            set(now, "RECEIVING"); color(now, Neo.GREEN_LIGHT);
            set(who, (h.src.isEmpty() ? "…" : h.src) + "  →  " + (h.dst.isEmpty() ? "…" : h.dst));
            set(meta, h.encrypted ? "Encrypted: not played" : h.data ? "Data, not voice" : h.meta != null ? "“" + h.meta + "”" : "");
            set(stats, String.format(Locale.US, "%.1f s  ·  %d frames  ·  signal %d %%", (h.end - h.start) / 1000.0, h.frames, Math.round(st.quality() * 100)));
        } else {
            set(now, "LISTENING FOR M17"); color(now, Neo.VIOLET_LIGHT);
            set(who, ""); set(meta, ""); set(stats, "");
        }
        List<M17Station.Heard> hl = st.heard();
        if (hl.size() != shown || h != null) {
            shown = hl.size(); list.removeAllViews();
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.US);
            for (int i = hl.size() - 1; i >= 0 && i >= hl.size() - 30; i--) {
                M17Station.Heard e = hl.get(i);
                String line = String.format(Locale.US, "%s  %-9s → %-9s %4.1f s%s", f.format(new Date(e.start)), e.src.isEmpty() ? "?" : e.src, e.dst.isEmpty() ? "?" : e.dst, (e.end - e.start) / 1000.0,
                    e.frames > 0 ? String.format(Locale.US, "  %d%%", 100 * e.good / e.frames) : "");
                TextView t = Neo.text(c, line, e == h ? Neo.GREEN_LIGHT : Neo.TEXT, 13, Neo.mono(c)); t.setSingleLine(true); t.setPadding(0, dp(3), 0, dp(3));
                list.addView(t);
            }
            if (hl.isEmpty()) list.addView(Neo.sheetHint(c, "Nothing yet. Tune the M17 frequency (the channel or the VFO). A transmission is found from its sync and played on the phone's speaker."));
        }
        set(note.getText().length() == 0 ? note : note, (svc.m17CanSend()
            ? "Hold to talk sends your voice as M17, to everyone (@ALL), for up to 28 seconds. Your callsign is the one in APRS settings. "
            : "Receive only on this firmware: transmitting M17 needs the module firmware with the symbol engine. ")
            + "The radio's audio path removes the low tones M17 needs, so SignalDeck rebuilds them on receive (" + st.calibration() + ")."
            + (st.held() ? " The module's voice filter is held open, so a transmission already in progress can still lock." : " It needs a fairly strong signal and the start of each transmission (its preamble)."));
    }
}
