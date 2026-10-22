/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.macdmr.radio.RadioService;
import com.macdmr.radio.ham.Conditions;
import com.macdmr.radio.ham.SatTrack;
import com.macdmr.radio.ham.Satellites;
import com.macdmr.radio.ham.Sgp4;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** PKT → VHF/UHF conditions: tropo ducting forecast, aurora, meteor showers and the next satellite passes. */
public final class ConditionsDialog {
    private ConditionsDialog() { }
    static final int[] CLASS_COLOR = { 0xFF2A3C50, Neo.CYAN, Neo.GREEN, Neo.AMBER };

    public static void show(Context c, RadioService svc) {
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "VHF/UHF conditions");
        body.addView(Neo.sheetHint(c, "Is it worth getting on the air beyond the usual range? Tropospheric ducting is the main source of long VHF/UHF contacts; aurora and meteor showers open short paths (mostly for SSB and digital modes, sometimes FM)."));
        final TextView loading = Neo.label(c, "Checking the forecasts…", Neo.MUTED, 14, false); body.addView(loading);
        final LinearLayout cards = new LinearLayout(c); cards.setOrientation(LinearLayout.VERTICAL); body.addView(cards);
        Neo.showSheet(c, body);
        final android.location.Location me = svc.lastKnownLocation();
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Conditions.Report r = Conditions.get(me == null ? 0 : me.getLatitude(), me == null ? 0 : me.getLongitude(), me != null);
            h.post(() -> { loading.setVisibility(View.GONE); fill(c, svc, cards, r, me); });
        }, "conditions").start();
    }

    private static LinearLayout card(Context c, LinearLayout parent, String title, String icon, int accent) {
        float d = Theme.dp(c);
        LinearLayout k = new LinearLayout(c); k.setOrientation(LinearLayout.VERTICAL); k.setBackground(Neo.rowCard(c)); k.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (12 * d));
        LinearLayout head = new LinearLayout(c); head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams((int) (28 * d), (int) (28 * d)); il.rightMargin = (int) (10 * d);
        head.addView(Neo.iconTile(c, icon, accent, 28), il); head.addView(Neo.caps(c, title));
        k.addView(head);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = (int) (10 * d);
        parent.addView(k, lp);
        return k;
    }
    private static TextView big(Context c, String s, int col) { TextView t = Neo.text(c, s, col, 19, Neo.uiMedium(c)); t.setPadding(0, (int) (8 * Theme.dp(c)), 0, (int) (2 * Theme.dp(c))); return t; }
    private static TextView small(Context c, String s) { TextView t = Neo.text(c, s, Neo.MUTED, 13.5f, Neo.uiMedium(c)); t.setLineSpacing(0, 1.12f); return t; }

    private static void fill(Context c, RadioService svc, LinearLayout cards, Conditions.Report r, android.location.Location me) {
        float d = Theme.dp(c);
        SimpleDateFormat day = new SimpleDateFormat("EEE HH:mm", Locale.getDefault()), hh = new SimpleDateFormat("HH", Locale.getDefault()), date = new SimpleDateFormat("EEE d MMM", Locale.getDefault());
        // tropo
        LinearLayout t = card(c, cards, "Tropo (next 48 hours)", "signal", Neo.GREEN);
        if (r.tropo.isEmpty()) t.addView(small(c, me == null ? "Needs a position fix." : "The forecast could not be loaded."));
        else {
            long[] w = r.bestWindow();
            t.addView(big(c, w == null ? "Normal: no enhancement expected" : Conditions.CLASS_NAMES[(int) w[2]] + ", " + day.format(new Date(w[0])) + "–" + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(w[1])), w == null ? Neo.TEXT : CLASS_COLOR[(int) w[2]]));
            LinearLayout strip = new LinearLayout(c); strip.setPadding(0, (int) (8 * d), 0, (int) (2 * d));
            LinearLayout labels = new LinearLayout(c);
            for (int i = 0; i < r.tropo.size(); i++) {
                Conditions.Slot s = r.tropo.get(i);
                View v = new View(c); v.setBackgroundColor(CLASS_COLOR[s.cls]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (18 * d), 1f); lp.rightMargin = (int) (1 * d); strip.addView(v, lp);
                TextView lb = Neo.text(c, i % 6 == 0 ? hh.format(new Date(s.time)) : "", Neo.DIM, 10, Neo.uiMedium(c));
                labels.addView(lb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            t.addView(strip); t.addView(labels);
            t.addView(small(c, "Grey normal · cyan slightly enhanced · green super-refraction (longer range) · amber ducting likely (hundreds of km). From the weather forecast's temperature and humidity layers near the ground (Open-Meteo, position rounded to about 50 km); thin ducts near the ground are underestimated, so a calm, clear night after a warm day can be better than shown."));
        }
        // aurora
        LinearLayout a = card(c, cards, "Aurora", "weather", Neo.VIOLET);
        a.addView(big(c, r.kpNow < 0 ? "Kp unavailable" : String.format(Locale.US, "Kp %.1f · %s", r.kpNow, Conditions.kpWords(r.kpNow)), r.kpNow >= 5 ? Neo.AMBER : Neo.TEXT));
        List<String> al = new ArrayList<>();
        if (r.kpMax >= 0) al.add(String.format(Locale.US, "Forecast up to Kp %.1f (%s, %s)", r.kpMax, Conditions.kpWords(r.kpMax), day.format(new Date(r.kpMaxAt))));
        if (r.aurora >= 0) al.add("Aurora chance overhead now: " + r.aurora + " % (NOAA OVATION)");
        al.add("VHF aurora signals bounce off the auroral oval to the north: point the antenna north, expect a rough, buzzing sound (it suits CW and SSB better than FM). It needs Kp 5 or more at mid latitudes.");
        a.addView(small(c, android.text.TextUtils.join("\n", al)));
        // meteors
        LinearLayout m = card(c, cards, "Meteor showers", "star", Neo.AMBER);
        if (r.showers.isEmpty()) m.addView(small(c, "No major shower in the next six weeks."));
        for (Conditions.Shower s : r.showers) {
            long days = Math.round((s.peak - System.currentTimeMillis()) / 86_400_000.0);
            m.addView(big(c, s.name + " · " + (days <= 0 ? "active now" : "peak " + date.format(new Date(s.peak)) + " (in " + days + " day" + (days == 1 ? "" : "s") + ")"), days <= 1 ? Neo.AMBER : Neo.TEXT));
            m.addView(small(c, "Up to " + s.zhr + " meteors an hour" + (s.note.isEmpty() ? "" : ", " + s.note) + "."));
        }
        m.addView(small(c, "Each meteor trail reflects 2 m signals for a fraction of a second up to a few seconds (“pings”), from about 800 to 2000 km away; the best hours are before dawn."));
        // satellites
        LinearLayout sp = card(c, cards, "Next satellite passes", "sstv", Neo.CYAN);
        if (me == null) sp.addView(small(c, "Needs a position fix."));
        else {
            Map<Integer, Sgp4> el = svc.satellites().elements(); long now = System.currentTimeMillis();
            List<Object[]> passes = new ArrayList<>(); java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (Satellites.Sat s : Satellites.all(c)) {
                if (!seen.add(s.catalog)) continue;                           // the ISS once, not per mode
                Sgp4 e = el.get(s.catalog); if (e == null) continue;
                for (SatTrack.Pass p : SatTrack.passes(e, now, 12, me.getLatitude(), me.getLongitude(), me.hasAltitude() ? me.getAltitude() : 0, 20)) if (p.los > now) { passes.add(new Object[] { s.catalog == Satellites.ISS_CATALOG ? "ISS" : s.name, p }); break; }
            }
            passes.sort((x, y) -> Long.compare(((SatTrack.Pass) x[1]).aos, ((SatTrack.Pass) y[1]).aos));
            if (passes.isEmpty()) sp.addView(small(c, "No pass above 20° in the next 12 hours."));
            for (int i = 0; i < Math.min(5, passes.size()); i++) {
                SatTrack.Pass p = (SatTrack.Pass) passes.get(i)[1];
                sp.addView(big(c, passes.get(i)[0] + " · " + (p.aos <= now ? "up now" : day.format(new Date(p.aos))) + String.format(Locale.US, " · %.0f°", p.maxEl), Neo.TEXT));
            }
            sp.addView(small(c, "Passes above 20° in the next 12 hours; PKT → Satellite passes tracks them with Doppler."));
        }
        if (!r.problems.isEmpty()) cards.addView(Neo.sheetHint(c, android.text.TextUtils.join(" · ", r.problems)));
        cards.addView(Neo.sheetHint(c, "Sources: NOAA SWPC (Kp, OVATION aurora), Open-Meteo (tropo), AMSAT/SatNOGS orbits. Updated " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(r.fetched)) + "; refreshed after 20 minutes."));
    }
}
