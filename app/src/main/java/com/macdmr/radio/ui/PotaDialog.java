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
import com.macdmr.radio.RadioService;
import com.macdmr.radio.ham.Logbook;
import com.macdmr.radio.ham.Pota;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** PKT → POTA / SOTA: your activation (log, duplicate check, ADIF) and the activators on the air now. */
public final class PotaDialog {
    private PotaDialog() { }

    public static void show(Context c, RadioService svc) {
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "POTA / SOTA");
        body.addView(Neo.sheetHint(c, "Parks on the Air and Summits on the Air: hams operate from parks and summits, others work them. Activate from a park or summit and log your contacts here, or find activators on the air and work them."));
        final AlertDialog[] self = { null };
        // ---- your activation ----
        body.addView(Neo.sheetSection(c, "Your activation"));
        Pota.Activation a = Pota.active(c);
        if (a != null) {
            String day = Pota.utcDay(System.currentTimeMillis());
            int today = svc.logbook().activation(a.ref, day).size(), need = Pota.needed(a.program);
            LinearLayout card = new LinearLayout(c); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(Neo.tint(c, Neo.GREEN, 12, 1)); card.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (12 * d));
            card.addView(Neo.text(c, a.program + " " + a.ref, Neo.GREEN_LIGHT, 20, Neo.display(c)));
            if (!a.name.isEmpty()) card.addView(Neo.text(c, a.name, Neo.TEXT, 15, Neo.uiMedium(c)));
            card.addView(Neo.text(c, "Since " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(a.since)) + " · today (UTC) " + today + " of " + need + " contacts" + (today >= need ? ": it counts" : ""), today >= need ? Neo.GREEN : Neo.MUTED, 14, Neo.uiMedium(c)));
            body.addView(card);
            LinearLayout r = row(c, body);
            btn(c, r, "Log a contact", "plus", Neo.GREEN, v -> { if (self[0] != null) self[0].dismiss(); log(c, svc, null); });
            btn(c, r, "Export ADIF", "send", Neo.CYAN, v -> export(c, svc, a));
            LinearLayout r2 = row(c, body);
            btn(c, r2, "End the activation", "stop", Neo.RED, v -> new AlertDialog.Builder(c).setTitle("End " + a.ref + "?").setMessage("Its contacts stay in the logbook (and can still be exported).").setPositiveButton("End", (dd, w) -> { Pota.end(c); if (self[0] != null) self[0].dismiss(); show(c, svc); }).setNegativeButton("Cancel", null).show());
        } else {
            body.addView(Neo.sheetHint(c, "Start one when you are set up in a park or on a summit: your contacts are then logged under its reference and counted (POTA: 10 in a UTC day, SOTA: 4)."));
            LinearLayout r = row(c, body);
            btn(c, r, "Activate a park…", "map", Neo.GREEN, v -> { if (self[0] != null) self[0].dismiss(); chooseRef(c, svc, Pota.POTA); });
            btn(c, r, "Activate a summit…", "signal", Neo.AMBER, v -> { if (self[0] != null) self[0].dismiss(); chooseRef(c, svc, Pota.SOTA); });
        }
        // ---- activators on the air ----
        body.addView(Neo.sheetSection(c, "On the air now"));
        final TextView status = Neo.label(c, "Loading the POTA and SOTA spots…", Neo.MUTED, 14, false); body.addView(status);
        final LinearLayout list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL); body.addView(list);
        self[0] = Neo.showSheet(c, body);
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<String> problems = new ArrayList<>(); List<Pota.Spot> spots = Pota.spots(problems);
            h.post(() -> {
                int workable = 0; for (Pota.Spot s : spots) if (s.workable()) workable++;
                status.setText((workable == 0 ? "Nobody is on FM on 2 m or 70 cm right now" + (spots.isEmpty() ? "." : " (" + spots.size() + " activators on HF and other modes, listed below).") : workable + " on FM on 2 m / 70 cm: tap one to tune or log it.") + (problems.isEmpty() ? "" : "  " + android.text.TextUtils.join(", ", problems)));
                SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
                int shown = 0;
                for (Pota.Spot s : spots) {
                    if (++shown > 40) break;
                    String sub = String.format(Locale.US, "%s %.4f MHz %s · %s %s", s.program, s.freqHz / 1e6, s.mode == null ? "" : s.mode, s.ref, s.name) + (s.where == null || s.where.isEmpty() ? "" : " (" + s.where + ")") + (s.time > 0 ? " · " + hm.format(new Date(s.time)) : "");
                    LinearLayout t = Neo.tile(c, s.program.equals(Pota.SOTA) ? "signal" : "map", s.workable() ? Neo.GREEN : Neo.DIM, s.call, sub);
                    if (!s.workable()) t.setAlpha(0.6f);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (int) (6 * d);
                    list.addView(t, lp);
                    t.setOnClickListener(v -> spotActions(c, svc, s, self[0]));
                }
            });
        }, "pota-spots").start();
    }

    private static void spotActions(Context c, RadioService svc, Pota.Spot s, AlertDialog sheet) {
        List<String> items = new ArrayList<>();
        if (s.workable()) items.add(String.format(Locale.US, "Tune to %.4f MHz (VFO)", s.freqHz / 1e6));
        items.add("Log " + s.call + " (hunting " + s.ref + ")");
        new AlertDialog.Builder(c).setTitle(s.call + " · " + s.ref).setItems(items.toArray(new String[0]), (dd, w) -> {
            if (s.workable() && w == 0) {
                ChannelStore.Channel v = new ChannelStore.Channel(); v.type = 1; v.rxFreq = v.txFreq = (int) s.freqHz; v.band = 1; v.power = 1; v.sq = 2; v.name = s.call + " " + s.ref;
                svc.applyVfo(v); Toast.makeText(c, "Tuned to " + s.call, Toast.LENGTH_SHORT).show();
            } else { if (sheet != null) sheet.dismiss(); log(c, svc, s); }
        }).show();
    }

    private static void chooseRef(Context c, RadioService svc, String program) {
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, Pota.POTA.equals(program) ? "Activate a park" : "Activate a summit");
        final EditText ref = field(c, body, Pota.POTA.equals(program) ? "Park reference (US-1234, GB-0001…)" : "Summit reference (W0C/FR-001, G/SE-001…)", "");
        ref.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        TextView go = Neo.button(c, "Start the activation", "check", Neo.GREEN); body.addView(go);
        final LinearLayout near = new LinearLayout(c); near.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog dlg = Neo.showSheet(c, body);
        go.setOnClickListener(v -> {
            final String r = ref.getText().toString().trim().toUpperCase(Locale.US);
            if (!program.equals(Pota.programOf(r))) { Toast.makeText(c, "That does not look like a " + program + " reference", Toast.LENGTH_SHORT).show(); return; }
            go.setEnabled(false);
            new Thread(() -> { String name = Pota.lookup(r); new Handler(Looper.getMainLooper()).post(() -> {
                go.setEnabled(true);
                if (name == null) { new AlertDialog.Builder(c).setTitle(r + " was not found").setMessage(program + " does not know it (or could not be reached). Start anyway?").setPositiveButton("Start", (dd, w) -> { Pota.start(c, r, ""); dlg.dismiss(); show(c, svc); }).setNegativeButton("Cancel", null).show(); return; }
                Pota.start(c, r, name); dlg.dismiss(); Toast.makeText(c, "Activating " + r + ": " + name, Toast.LENGTH_SHORT).show(); show(c, svc);
            }); }, "pota-lookup").start();
        });
        if (!Pota.POTA.equals(program)) return;
        body.addView(Neo.sheetSection(c, "Parks near you"));
        final TextView st = Neo.label(c, "Looking…", Neo.MUTED, 14, false); body.addView(st); body.addView(near);
        final android.location.Location me = svc.lastKnownLocation();
        if (me == null) { st.setText("Needs a position fix."); return; }
        new Thread(() -> {
            List<Pota.Park> ps; try { ps = Pota.parksNear(me.getLatitude(), me.getLongitude()); } catch (Throwable t) { ps = null; }
            final List<Pota.Park> f = ps;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (f == null) { st.setText("POTA could not be reached."); return; }
                st.setText(f.isEmpty() ? "No park within 60 km." : "Tap one to fill in its reference:");
                for (int i = 0; i < Math.min(25, f.size()); i++) {
                    Pota.Park p = f.get(i);
                    LinearLayout t = Neo.tile(c, "map", Neo.GREEN, p.ref, p.name + String.format(Locale.US, " · %.1f km", p.km));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (int) (6 * d);
                    near.addView(t, lp); t.setOnClickListener(v -> ref.setText(p.ref));
                }
            });
        }, "pota-parks").start();
    }

    /** Quick log: call, reports, notes; the frequency the radio is on (or the spot's); the duplicate check. */
    private static void log(Context c, RadioService svc, Pota.Spot hunted) {
        float d = Theme.dp(c);
        final Pota.Activation a = Pota.active(c);
        LinearLayout body = Neo.sheetBody(c, hunted != null ? "Log " + hunted.call : "Log a contact");
        ChannelStore.Channel cur = svc.current();
        final long freq = hunted != null ? hunted.freqHz : cur != null ? cur.rxFreq : 0;
        body.addView(Neo.sheetHint(c, String.format(Locale.US, "%.4f MHz FM", freq / 1e6) + (a != null ? " · under your activation " + a.ref : "") + (hunted != null ? " · their " + hunted.program + " " + hunted.ref : "")));
        final EditText call = field(c, body, "Callsign", hunted != null ? hunted.call : "");
        call.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText sent = field(c, body, "Report sent", "59"), rcvd = field(c, body, "Report received", "59");
        final EditText theirRef = field(c, body, "Their park or summit (park-to-park, optional)", hunted != null ? hunted.ref : "");
        theirRef.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText notes = field(c, body, "Notes (optional)", "");
        TextView save = Neo.button(c, "Save", "check", Neo.GREEN);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (50 * d)); sl.topMargin = (int) (12 * d);
        body.addView(save, sl);
        final AlertDialog dlg = Neo.showSheet(c, body);
        save.setOnClickListener(v -> {
            String cs = call.getText().toString().trim().toUpperCase(Locale.US);
            if (Logbook.baseCall(cs) == null) { Toast.makeText(c, "Enter a callsign", Toast.LENGTH_SHORT).show(); return; }
            Runnable doSave = () -> {
                Logbook.Entry e = new Logbook.Entry(); e.ts = System.currentTimeMillis(); e.call = cs; e.freqHz = freq; e.mode = "FM"; e.qso = true;
                e.rstSent = sent.getText().toString().trim(); e.rstRcvd = rcvd.getText().toString().trim(); e.notes = notes.getText().toString().trim(); e.source = a != null ? a.program + " activation" : "POTA/SOTA hunting";
                String tr = theirRef.getText().toString().trim().toUpperCase(Locale.US); if (Pota.programOf(tr) != null) { e.sig = Pota.programOf(tr); e.sigInfo = tr; }
                if (a != null) { e.mySig = a.program; e.mySigInfo = a.ref; }
                svc.logbook().save(e); dlg.dismiss();
                int n = a == null ? 0 : svc.logbook().activation(a.ref, Pota.utcDay(e.ts)).size();
                Toast.makeText(c, cs + " logged" + (a != null ? " (" + n + " of " + Pota.needed(a.program) + " today)" : ""), Toast.LENGTH_SHORT).show();
                show(c, svc);
            };
            if (a != null && svc.logbook().isDupe(cs, freq, System.currentTimeMillis(), a.ref))
                new AlertDialog.Builder(c).setTitle(cs + " is already in the log").setMessage("Worked on this band today under " + a.ref + ". Log it again anyway?").setPositiveButton("Log again", (dd, w) -> doSave.run()).setNegativeButton("Cancel", null).show();
            else doSave.run();
        });
    }

    /** POTA's file name: CALL@REF-YYYYMMDD.adi (one per UTC day), saved to Download/DMR/Logbook and shared. */
    private static void export(Context c, RadioService svc, Pota.Activation a) {
        try {
            String me = svc.packet() == null ? "" : svc.packet().callsign();
            String day = Pota.utcDay(System.currentTimeMillis());
            List<Logbook.Entry> es = svc.logbook().activation(a.ref, null);
            if (es.isEmpty()) { Toast.makeText(c, "No contacts under " + a.ref + " yet", Toast.LENGTH_SHORT).show(); return; }
            String adif = Logbook.adif(es, com.macdmr.radio.BuildConfig.VERSION_NAME, me);
            java.io.File dir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "DMR/Logbook"); dir.mkdirs();
            java.io.File out = new java.io.File(dir, (me.isEmpty() ? "MYCALL" : me) + "@" + a.ref.replace('/', '_') + "-" + day + ".adi");
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(out), java.nio.charset.StandardCharsets.UTF_8)) { w.write(adif); }
            Toast.makeText(c, "Saved Download/DMR/Logbook/" + out.getName() + (me.isEmpty() ? " (set your callsign in APRS settings for the station field)" : ""), Toast.LENGTH_LONG).show();
            c.startActivity(android.content.Intent.createChooser(new android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_SUBJECT, out.getName()).putExtra(android.content.Intent.EXTRA_TEXT, adif), "Send the log"));
        } catch (Throwable t) { Toast.makeText(c, "Export failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private static LinearLayout row(Context c, LinearLayout body) { LinearLayout r = new LinearLayout(c); body.addView(r); return r; }
    private static void btn(Context c, LinearLayout r, String label, String icon, int accent, android.view.View.OnClickListener l) {
        float d = Theme.dp(c); TextView b = Neo.button(c, label, icon, accent); b.setTextSize(14); Neo.fit(b); b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (48 * d), 1f); lp.setMargins((int) (3 * d), (int) (6 * d), (int) (3 * d), 0); r.addView(b, lp);
    }
    private static EditText field(Context c, LinearLayout body, String label, String value) {
        float d = Theme.dp(c);
        body.addView(Neo.sheetSection(c, label));
        EditText e = new EditText(c); e.setText(value); e.setSingleLine(true);
        e.setTextColor(Neo.TEXT); e.setHintTextColor(Neo.DIM); e.setTextSize(16); e.setBackground(Neo.rowCard(c)); e.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        body.addView(e);
        return e;
    }
}
