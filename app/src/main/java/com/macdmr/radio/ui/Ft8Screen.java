/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.macdmr.radio.RadioService;
import com.macdmr.radio.ham.Ft8Station;

import java.util.List;
import java.util.Locale;

/**
 * FT8 over FM: the slot clock, a waterfall of the channel's audio (tap it to choose your transmit frequency), the
 * decodes of every slot (tap a CQ to answer it), the QSO in progress, and the controls.
 */
public final class Ft8Screen extends ModeScreen {
    static final double F_LO = 200, F_HI = 2800;
    private final Ft8Station st;
    private final SlotClock clock;
    private final Waterfall fall;
    private final TextView qso, status;
    private final LinearLayout list;
    private final TextView periodBtn, haltBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int shown = -1, sentShown = -1;
    private final Runnable frame = new Runnable() { @Override public void run() { if (st.running()) { fall.addLine(st.spectrum(fall.columns(), F_LO, F_HI)); clock.invalidate(); } ui.postDelayed(this, 160); } };

    public Ft8Screen(Context c, RadioService svc, Runnable onClose) {
        super(c, svc, "FT8", "ft8", Neo.CYAN, onClose);
        st = svc.ft8();
        st.setListener(() -> ui.post(this::refresh));

        LinearLayout body = new LinearLayout(c); body.setOrientation(VERTICAL); body.setPadding(dp(14), dp(4), dp(14), dp(6));
        clock = new SlotClock(c);
        body.addView(clock, new LayoutParams(LayoutParams.MATCH_PARENT, dp(30)));
        fall = new Waterfall(c);
        LayoutParams fl = new LayoutParams(LayoutParams.MATCH_PARENT, dp(118)); fl.topMargin = dp(6);
        body.addView(fall, fl);
        qso = Neo.text(c, "", Neo.AMBER_LIGHT, 15, Neo.mono(c)); qso.setPadding(0, dp(8), 0, 0);
        body.addView(qso);
        status = Neo.text(c, "", Neo.MUTED, 12.5f, Neo.uiMedium(c)); status.setPadding(0, dp(2), 0, dp(4));
        body.addView(status);
        ScrollView sv = new ScrollView(c); sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(c); list.setOrientation(VERTICAL);
        sv.addView(list);
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
        col.addView(body);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        LinearLayout listBox = new LinearLayout(c); listBox.setOrientation(VERTICAL); listBox.setPadding(dp(14), 0, dp(14), 0);
        listBox.addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        col.addView(listBox, svl);
        content.addView(col, ll);

        LinearLayout r1 = buttonRow();
        button(r1, "Call CQ", "send", Neo.GREEN, v -> { toast(st.callCq()); refresh(); });
        haltBtn = button(r1, "Halt TX", "stop", Neo.RED, v -> { st.haltTx(); refresh(); });
        button(r1, "Send text", "txt", Neo.CYAN, v -> sendText());
        LinearLayout r2 = buttonRow();
        periodBtn = button(r2, "", "scan", Neo.AMBER, v -> { st.setTxParity(1 - st.txParity()); refresh(); });
        button(r2, "Settings", "menu", Neo.CYAN, v -> settings());
        button(r2, "Stop FT8", "power", Neo.RED, v -> { svc.setFt8(false); close(); });
        refresh();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); ui.post(frame); }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); ui.removeCallbacks(frame); }

    @Override protected boolean stillRunning() { return st.running(); }

    @Override protected void refresh() {
        com.macdmr.radio.ChannelStore.Channel ch = svc.current();
        setSubtitle("FT8 over FM on " + (ch != null ? "Ch " + ch.number + " · " + ch.name : "this channel"));
        String next = st.nextMessage();
        set(qso, st.txEnabled() && next != null ? "TX " + (st.txParity() == 0 ? "1st" : "2nd") + " ▸ " + next : st.partner() != null ? "QSO with " + st.partner() : "Not transmitting");
        color(qso, st.txEnabled() ? Neo.AMBER_LIGHT : Neo.DIM);
        set(status, st.status() + "  ·  transmit at " + Math.round(st.txHz()) + " Hz" + (svc.ft8Grid().isEmpty() ? "" : "  ·  grid " + svc.ft8Grid()));
        set(periodBtn, st.txParity() == 0 ? "TX 1st (:00 :30)" : "TX 2nd (:15 :45)");
        haltBtn.setAlpha(st.txEnabled() ? 1f : 0.45f);
        List<Ft8Station.Heard> h = st.heard(); List<String> sent = st.sent();
        if (h.size() != shown || sent.size() != sentShown) { shown = h.size(); sentShown = sent.size(); rebuild(h, sent); }
        fall.marker = st.txHz(); fall.invalidate();
    }

    /** Newest first: each slot's decodes, and our own transmissions in between. */
    private void rebuild(List<Ft8Station.Heard> h, List<String> sent) {
        list.removeAllViews();
        if (h.isEmpty() && sent.isEmpty()) {
            list.addView(Neo.sheetHint(c, "Listening. Every 15 s the last slot is decoded and listed here, newest at the top. Tap a CQ to answer it; "
                + "the app then runs the exchange (grid, report, R-report, RR73, 73) by itself and logs the contact.\n\n"
                + "FT8 over FM talks to stations sending FT8 audio on this FM channel: another SignalDeck, or WSJT-X through an FM radio. "
                + "It cannot hear SSB FT8 (144.174 / 432.174 MHz), and FM lets only the strongest station of each slot through.")); return;
        }
        int si = sent.size() - 1;
        long lastSlot = Long.MIN_VALUE;
        for (int i = h.size() - 1; i >= 0; i--) {
            final Ft8Station.Heard e = h.get(i);
            if (e.slot != lastSlot) { lastSlot = e.slot; }
            TextView row = Neo.text(c, String.format(Locale.US, "%s %+3d %+4.1f %4.0f  %s", e.time(), e.d.snrDb, e.d.dtS, e.d.freqHz, e.d.text), e.toMe ? Neo.AMBER_LIGHT : e.cq ? Neo.GREEN_LIGHT : Neo.TEXT, 13.5f, Neo.mono(c));
            row.setSingleLine(true); row.setPadding(dp(8), dp(7), dp(8), dp(7));
            row.setBackground(e.toMe ? Neo.tint(c, Neo.AMBER, 8, 1) : e.cq ? Neo.tint(c, Neo.GREEN, 8, 0) : null);
            row.setOnClickListener(v -> { String err = st.reply(e); toast(err != null ? err : "Calling " + e.from + " in the " + (st.txParity() == 0 ? "1st" : "2nd") + " period"); refresh(); });
            LayoutParams rl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); rl.topMargin = dp(2);
            list.addView(row, rl);
            if (list.getChildCount() > 120) break;
        }
        if (!sent.isEmpty()) {
            list.addView(Neo.sheetSection(c, "Sent"));
            for (int i = sent.size() - 1; i >= 0 && i >= sent.size() - 20; i--) {
                TextView row = Neo.text(c, sent.get(i), 0xFFFF9AA6, 13f, Neo.mono(c)); row.setSingleLine(true); row.setPadding(dp(8), dp(4), dp(8), dp(4));
                list.addView(row);
            }
        }
    }

    private void sendText() {
        LinearLayout b = Neo.sheetBody(c, "Send an FT8 message");
        b.addView(Neo.sheetHint(c, "Any standard message (for example \"CQ " + safeCall() + "\") or up to 13 characters of free text: A-Z, 0-9, space and + - . / ?. It goes out once, in your period."));
        final EditText in = new EditText(c); in.setTextColor(Neo.TEXT); in.setHintTextColor(Neo.DIM); in.setTypeface(Neo.mono(c)); in.setHint("TNX 73 GL");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); in.setSingleLine(true);
        b.addView(in);
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        LinearLayout r = new LinearLayout(c);
        TextView send = Neo.button(c, "Send", "send", Neo.AMBER);
        send.setOnClickListener(v -> { String e = st.sendText(in.getText().toString()); toast(e != null ? e : "Queued for your next period"); if (e == null) dlg[0].dismiss(); refresh(); });
        r.addView(send, new LinearLayout.LayoutParams(0, dp(48), 1f));
        b.addView(r);
        dlg[0] = Neo.showSheet(c, b);
    }

    private String safeCall() { String s = svc.packet() != null ? svc.packet().callsign() : ""; return s.isEmpty() ? "YOURCALL" : s; }

    private void settings() {
        LinearLayout b = Neo.sheetBody(c, "FT8 settings");
        b.addView(Neo.sheetSection(c, "Grid sent with CQ and replies"));
        b.addView(Neo.sheetHint(c, "FT8 sends a 4-character Maidenhead grid (about 100 x 200 km). Leave it empty to use the phone's location, type one to fix it, or type - to send no grid. Now: " + (svc.ft8Grid().isEmpty() ? "none" : svc.ft8Grid()) + "."));
        final EditText g = new EditText(c); g.setTextColor(Neo.TEXT); g.setTypeface(Neo.mono(c)); g.setText(svc.ft8GridSetting()); g.setSingleLine(true);
        g.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); g.setHint("from location");
        b.addView(g);
        b.addView(Neo.sheetSection(c, "Transmit frequency"));
        b.addView(Neo.sheetHint(c, "The audio frequency of your signal (" + Math.round(st.txHz()) + " Hz). Tap the waterfall to move it. On FM it matters little: the receiver hears one station at a time."));
        b.addView(Neo.sheetSection(c, "Clock"));
        b.addView(Neo.sheetHint(c, clockHint()));
        LinearLayout r = new LinearLayout(c);
        TextView save = Neo.button(c, "Save", "check", Neo.GREEN), clear = Neo.button(c, "Clear list", "close", Neo.RED);
        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        save.setOnClickListener(v -> {
            String s = g.getText().toString().trim().toUpperCase(Locale.US);
            if (!s.isEmpty() && !s.equals("-") && !s.matches("[A-R]{2}[0-9]{2}([A-X]{2})?")) { toast("A grid is two letters A-R and two digits, like FN42"); return; }
            svc.setFt8Grid(s); dlg[0].dismiss(); refresh();
        });
        clear.setOnClickListener(v -> { st.clear(); dlg[0].dismiss(); });
        r.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1f)); r.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1f));
        b.addView(r);
        dlg[0] = Neo.showSheet(c, b);
    }

    /** FT8 needs the clock within about a second: the decodes' DT shows how far off the phone (or the other station) is. */
    private String clockHint() {
        List<Ft8Station.Heard> h = st.heard();
        if (h.isEmpty()) return "FT8 needs your clock within about a second of the other station's. The phone sets its clock from the network; after the first decodes, their DT column shows the difference.";
        double s = 0; int n = 0; for (int i = Math.max(0, h.size() - 20); i < h.size(); i++) { s += h.get(i).d.dtS; n++; }
        double dt = s / n;
        return String.format(Locale.US, "Recent decodes average DT %+.1f s. ", dt) + (Math.abs(dt) < 0.8 ? "That is fine." : "That is a lot: check the phone's automatic time setting (or the other station's clock).");
    }

    // ---- views ----

    /** The 15 s slot: where we are in it, which period it is, and whether we transmit in it. */
    private final class SlotClock extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        SlotClock(Context c) { super(c); t.setTypeface(Neo.mono(c)); t.setTextSize(dp(12.5f)); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            long slot = st.slotNow(); int parity = (int) (slot & 1);
            boolean ours = st.txEnabled() && parity == st.txParity();
            p.setStyle(Paint.Style.FILL); p.setColor(Neo.TRACK); cv.drawRoundRect(0, 0, w, h, r, r, p);
            double s = st.secondsInSlot();
            p.setColor(Neo.alpha(ours ? Neo.RED : parity == 0 ? Neo.CYAN : Neo.GREEN, 0x90));
            cv.drawRoundRect(0, 0, (float) (w * s / 15.0), h, r, r, p);
            p.setColor(Neo.alpha(Neo.TEXT, 0x50)); float sig = (float) (w * (0.5 + 12.64) / 15.0); cv.drawRect(sig, dp(4), sig + dp(1), h - dp(4), p);
            t.setColor(Neo.TEXT);
            String label = String.format(Locale.US, "%s period  %4.1f s", parity == 0 ? "1st" : "2nd", s) + (ours ? "  · TX" : "");
            cv.drawText(label, dp(10), h / 2 + t.getTextSize() / 3, t);
        }
    }

    /** A scrolling waterfall of the channel audio; tap to set the transmit frequency. */
    private final class Waterfall extends View {
        private Bitmap bmp; private int[] px; private final Paint p = new Paint(), m = new Paint(Paint.ANTI_ALIAS_FLAG), lbl = new Paint(Paint.ANTI_ALIAS_FLAG);
        double marker = 1500; private float floor = -80;
        Waterfall(Context c) {
            super(c); m.setColor(Neo.RED); m.setStrokeWidth(dp(2)); lbl.setColor(Neo.DIM); lbl.setTextSize(dp(10)); lbl.setTypeface(Neo.mono(c));
            setContentDescription("Waterfall; tap to choose the transmit frequency");
        }
        int columns() { return Math.max(64, Math.min(400, getWidth() / 2)); }
        void addLine(float[] db) {
            if (getWidth() == 0 || getHeight() == 0) return;
            int w = db.length, h = Math.max(1, getHeight() / 2);
            if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h) { bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); px = new int[w * h]; java.util.Arrays.fill(px, 0xFF06121A); }
            float[] sorted = db.clone(); java.util.Arrays.sort(sorted); float med = sorted[w / 2];
            floor = floor * 0.9f + med * 0.1f;
            System.arraycopy(px, 0, px, w, w * (h - 1));                 // scroll down a line, the newest on top
            for (int i = 0; i < w; i++) px[i] = heat(Math.max(0, Math.min(1, (db[i] - floor - 3) / 30f)));
            bmp.setPixels(px, 0, w, 0, 0, w, h);
            invalidate();
        }
        private int heat(float v) {                                      // navy -> cyan -> amber -> white
            int r, g, b;
            if (v < 0.4f) { float k = v / 0.4f; r = (int) (6 + 10 * k); g = (int) (18 + 150 * k); b = (int) (26 + 170 * k); }
            else if (v < 0.75f) { float k = (v - 0.4f) / 0.35f; r = (int) (16 + 239 * k); g = (int) (168 + 22 * k); b = (int) (196 - 150 * k); }
            else { float k = (v - 0.75f) / 0.25f; r = 255; g = (int) (190 + 65 * k); b = (int) (46 + 209 * k); }
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (bmp != null) { p.setFilterBitmap(true); cv.drawBitmap(bmp, null, new Rect(0, 0, (int) w, (int) h), p); }
            else cv.drawColor(0xFF06121A);
            for (int f = 500; f < F_HI; f += 500) { float x = (float) ((f - F_LO) / (F_HI - F_LO) * w); lbl.setColor(Neo.alpha(Neo.DIM, 0xA0)); cv.drawText(String.valueOf(f), x + dp(2), h - dp(3), lbl); }
            float x = (float) ((marker - F_LO) / (F_HI - F_LO) * w);
            m.setAlpha(0xC0); cv.drawLine(x, 0, x, h, m);
            float x2 = (float) ((marker + 7 * 6.25 - F_LO) / (F_HI - F_LO) * w); m.setAlpha(0x60); cv.drawLine(x2, 0, x2, h, m);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                double f = F_LO + (F_HI - F_LO) * e.getX() / Math.max(1, getWidth());
                st.setTxHz(f); marker = st.txHz(); invalidate(); refresh(); performClick();
            }
            return true;
        }
        @Override public boolean performClick() { return super.performClick(); }
    }
}
