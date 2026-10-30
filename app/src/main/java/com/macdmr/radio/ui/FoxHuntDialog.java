/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.macdmr.radio.ChannelStore;
import com.macdmr.radio.RadioService;
import com.macdmr.radio.ham.FoxHunt;
import com.macdmr.radio.ham.FoxPredictor;

import java.util.List;
import java.util.Locale;

/**
 * Fox hunt: live signal strength (big number, S-meter, peak hold, an optional rising beep), the compass bearing the
 * phone points at, Mark to save a reading, a map of the hunt with the prediction layers (bearing lines, the signal
 * heat map with its 50 % and 90 % regions, the combined estimate; the compact plot when no map tiles load), the
 * off-frequency trick for when the signal is too strong, and Be the fox (a beacon every so often on this channel).
 */
public final class FoxHuntDialog {
    private FoxHuntDialog() { }

    public static void show(Context c, RadioService svc) {
        if (svc == null) return;
        float d = Theme.dp(c);
        LinearLayout body = Neo.sheetBody(c, "Fox hunt");
        ChannelStore.Channel ch = svc.current();
        body.addView(Neo.sheetHint(c, "Listening on " + (ch == null ? "no channel" : ch.name + " (" + String.format(Locale.US, "%.4f", ch.rxFreq / 1e6) + " MHz)") + ". Walk or drive, point the phone where the signal is strongest and tap Mark; where the bearings cross is where the fox is."));

        final TextView big = Neo.text(c, "--", Neo.CYAN, 56, Neo.display(c)); big.setGravity(Gravity.CENTER);
        body.addView(big);
        final TextView sub = Neo.text(c, "", Neo.MUTED, 14, Neo.uiMedium(c)); sub.setGravity(Gravity.CENTER); body.addView(sub);
        final SMeterView meter = new SMeterView(c); body.addView(meter);
        final TextView info = Neo.label(c, "", 0xFFBFC8D0, 13, false); info.setPadding(0, (int) (6 * d), 0, (int) (4 * d)); body.addView(info);

        // ---- prediction: which layers, the map (or the plot when no tiles load) ----
        final android.content.SharedPreferences fp = c.getSharedPreferences("fox", Context.MODE_PRIVATE);
        final boolean[] layerOn = { fp.getBoolean("predBearings", true), fp.getBoolean("predHeat", true), fp.getBoolean("predTerrain", false) };
        final String[] layerKey = { "predBearings", "predHeat", "predTerrain" }, layerName = { "Bearings", "Heat map", "Terrain" };
        final int[] layerAccent = { Neo.CYAN, Neo.AMBER, Neo.GREEN };
        body.addView(Neo.sheetSection(c, "Prediction"));
        LinearLayout pills = new LinearLayout(c);
        final TextView[] pill = new TextView[layerOn.length];
        for (int i = 0; i < pill.length; i++) {
            pill[i] = Neo.label(c, layerName[i], layerAccent[i], 14, true); pill[i].setGravity(Gravity.CENTER);
            pill[i].setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d)); Neo.pillStyle(pill[i], layerOn[i], layerAccent[i]);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); pl.setMargins((int) (3 * d), 0, (int) (3 * d), 0);
            pills.addView(pill[i], pl);
        }
        body.addView(pills);
        // terrain: the download (once per area) and how high the fox's antenna is
        final double[] aglChoices = { FoxPredictor.FOX_AGL, 10, 30 }; final String[] aglNames = { "Fox on the ground", "Fox on a roof (10 m)", "Fox on a tower (30 m)" };
        final int[] agl = { Math.max(0, Math.min(2, fp.getInt("foxAgl", 0))) };
        final LinearLayout terrRow = new LinearLayout(c); terrRow.setBaselineAligned(false);
        final TextView aglBtn = Neo.button(c, aglNames[agl[0]], null, Neo.GREEN), dlBtn = Neo.button(c, "Download terrain", "globe", Neo.GREEN);
        for (TextView b : new TextView[] { aglBtn, dlBtn }) { b.setTextSize(13.5f); Neo.fit(b); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f); lp.setMargins((int) (3 * d), (int) (8 * d), (int) (3 * d), 0); terrRow.addView(b, lp); }
        dlBtn.setVisibility(View.GONE);
        terrRow.setVisibility(layerOn[2] ? View.VISIBLE : View.GONE);
        body.addView(terrRow);
        final TextView predNote = Neo.label(c, "", Neo.DIM, 12.5f, false); predNote.setPadding(0, (int) (6 * d), 0, (int) (6 * d)); body.addView(predNote);

        final android.widget.FrameLayout view = new android.widget.FrameLayout(c);
        final MapView mapView = new MapView(c);
        final PlotView plot = new PlotView(c, svc.fox());
        final HuntLayer layer = new HuntLayer(c);
        mapView.setOverlay(layer);
        view.addView(mapView); view.addView(plot); plot.setVisibility(View.GONE);
        final TextView swap = Neo.chip(c, "Plot", Neo.CYAN);
        android.widget.FrameLayout.LayoutParams sl0 = new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        sl0.setMargins((int) (8 * d), (int) (8 * d), 0, 0); view.addView(swap, sl0);
        body.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (300 * d)));
        swap.setOnClickListener(v -> { boolean toPlot = plot.getVisibility() != View.VISIBLE; plot.setVisibility(toPlot ? View.VISIBLE : View.GONE); mapView.setVisibility(toPlot ? View.GONE : View.VISIBLE); swap.setText(toPlot ? "MAP" : "PLOT"); });
        body.addView(Neo.sheetHint(c, "Strength-based estimates are rough: walls, cars and your body change readings by 10 dB or more. Take marks in the open, spread out, and hold the phone the same way every time."));

        LinearLayout r1 = new LinearLayout(c); r1.setBaselineAligned(false);
        TextView mark = Neo.button(c, "Mark", "locate", Neo.GREEN), beep = Neo.button(c, "Beeper off", "volume", Neo.CYAN), off = Neo.button(c, "Off-frequency", "vfo", Neo.AMBER);
        for (TextView b : new TextView[] { mark, beep, off }) { b.setTextSize(14); Neo.fit(b); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f); lp.setMargins((int) (3 * d), (int) (8 * d), (int) (3 * d), 0); r1.addView(b, lp); }
        body.addView(r1);
        LinearLayout r2 = new LinearLayout(c); r2.setBaselineAligned(false);
        TextView map = Neo.button(c, "Open in maps", "map", Neo.CYAN), clear = Neo.button(c, "Clear marks", "close", Neo.RED);
        for (TextView b : new TextView[] { map, clear }) { b.setTextSize(14); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f); lp.setMargins((int) (3 * d), (int) (6 * d), (int) (3 * d), 0); r2.addView(b, lp); }
        body.addView(r2);

        if (!svc.profile().rssi) {                                          // no signal readings (the W6): only the fox side works
            for (int i = body.getChildCount() - 1; i >= 1; i--) body.removeViewAt(i);   // keep the title, drop the hunt
            body.addView(Neo.sheetHint(c, "This radio does not report signal strength, so it cannot hunt a fox. It can be one: the beacon below is what the hunters look for."));
        }
        body.addView(Neo.sheetSection(c, "Be the fox"));
        final TextView foxBtn = Neo.button(c, svc.foxTxOn() ? "Stop the fox beacon" : "Start the fox beacon", "bolt", Neo.VIOLET);
        body.addView(foxBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(Neo.sheetHint(c, "Sends 5 seconds of beeps on this channel every minute (your callsign follows in Morse on amateur channels) until stopped or the channel changes, so others can hunt you. Hide the phone with a full battery."));

        // ---- live state ----
        final int[] peak = { -999 }; final boolean[] beeping = { false }; final int[] offset = { 0 };
        final double[] heading = { Double.NaN }; final Location[] here = { svc.lastKnownLocation() };
        final Handler h = new Handler(Looper.getMainLooper());
        final android.media.ToneGenerator[] tg = { null };
        final long[] nextBeep = { 0 };
        final FoxPredictor.Result[] pred = { null }; final int[] gen = { 0 }, seen = { -1 };
        final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        final com.macdmr.radio.coverage.Terrain terrain = new com.macdmr.radio.coverage.Terrain(c);   // used on the worker only
        final FoxPredictor.TerrainCache tcache = new FoxPredictor.TerrainCache();
        final double[][] tbox = { null };                                       // the area the terrain must cover {s, w, n, e}
        final Runnable recompute = () -> {
            final int g = ++gen[0]; final List<FoxHunt.Mark> ms = svc.fox().marks(); seen[0] = ms.size();
            final FoxPredictor.Options o = new FoxPredictor.Options(); o.bearings = layerOn[0]; o.heat = layerOn[1]; o.terrain = layerOn[2];
            o.foxAgl = aglChoices[agl[0]]; o.cache = tcache;
            ChannelStore.Channel cur = svc.current(); if (cur != null && cur.rxFreq > 0) o.hz = cur.rxFreq;
            dlBtn.setVisibility(View.GONE);
            if (!o.bearings && !o.heat) { pred[0] = null; layer.set(null, ms); plot.setEstimate(null); mapView.invalidate(); predNote.setText("Prediction off: the estimate is the bearing crossing, as without layers."); return; }
            final double[] box; if (ms.isEmpty()) box = null; else {
                double s0 = 90, w0 = 180, n0 = -90, e0 = -180;
                for (FoxHunt.Mark m : ms) { s0 = Math.min(s0, m.lat); n0 = Math.max(n0, m.lat); w0 = Math.min(w0, m.lon); e0 = Math.max(e0, m.lon); }
                double padLat = 3000 / 111_320.0, padLon = 3000 / (111_320.0 * Math.cos(Math.toRadians((s0 + n0) / 2)));   // the grid reaches ~1.5 km past the marks; room to grow
                box = new double[] { s0 - padLat, w0 - padLon, n0 + padLat, e0 + padLon };
            }
            tbox[0] = box;
            final boolean useTerrain = o.terrain && o.heat && box != null;
            if (useTerrain) predNote.setText("Working out the terrain\u2026");
            worker.execute(() -> {
                int missing = 0;
                if (useTerrain) {
                    int[] t = terrain.tiles(box[0], box[1], box[2], box[3]); missing = t[1];
                    if (missing == 0 && terrain.load(box[0], box[1], box[2], box[3], null) == null) o.elevation = terrain;   // from the phone, no network
                }
                FoxPredictor.Result r = null; try { r = FoxPredictor.predict(ms, o); } catch (Throwable ignored) { }
                final FoxPredictor.Result res = r; final int miss = missing;
                h.post(() -> {
                    if (g != gen[0]) return;                            // a newer mark or toggle arrived meanwhile
                    if (miss > 0) { dlBtn.setText(String.format(Locale.US, "Download terrain (about %.1f MB)", Math.max(0.1, miss * com.macdmr.radio.coverage.Terrain.TILE_BYTES / 1e6))); dlBtn.setVisibility(View.VISIBLE); }
                    pred[0] = res; layer.set(res, ms); plot.setEstimate(res); mapView.invalidate();
                    predNote.setText(res == null ? "Mark a reading to start." : (res.note == null ? "" : "Not used yet: " + res.note + ". ") + (res.hasMap() ? "Shaded: where the fox probably is; bright outline 50 %, faint 90 %." : ""));
                    if (!ms.isEmpty()) {
                        if (res != null && res.hasMap() && (res.layers & FoxPredictor.HEAT) != 0) mapView.fitBox(res.south, res.west, res.north, res.east);
                        else {
                            double s0 = 90, w0 = 180, n0 = -90, e0 = -180;
                            for (FoxHunt.Mark m : ms) { s0 = Math.min(s0, m.lat); n0 = Math.max(n0, m.lat); w0 = Math.min(w0, m.lon); e0 = Math.max(e0, m.lon); }
                            if (res != null) { s0 = Math.min(s0, res.lat); n0 = Math.max(n0, res.lat); w0 = Math.min(w0, res.lon); e0 = Math.max(e0, res.lon); }
                            double pad = 0.002; mapView.fitBox(s0 - pad, w0 - pad, n0 + pad, e0 + pad);
                        }
                    }
                });
            });
        };
        for (int i = 0; i < pill.length; i++) {
            final int k = i;
            pill[i].setOnClickListener(v -> { layerOn[k] = !layerOn[k]; Neo.pillStyle(pill[k], layerOn[k], layerAccent[k]); fp.edit().putBoolean(layerKey[k], layerOn[k]).apply(); terrRow.setVisibility(layerOn[2] ? View.VISIBLE : View.GONE); recompute.run(); });
        }
        aglBtn.setOnClickListener(v -> { agl[0] = (agl[0] + 1) % aglChoices.length; aglBtn.setText(aglNames[agl[0]]); fp.edit().putInt("foxAgl", agl[0]).apply(); recompute.run(); });
        dlBtn.setOnClickListener(v -> {
            final double[] box = tbox[0]; if (box == null) return;
            new AlertDialog.Builder(c).setTitle("Download terrain").setMessage("Ground heights for this area " + dlBtn.getText().toString().replace("Download terrain ", "") + ", downloaded once and kept on the phone for later hunts and the coverage maps, so the Terrain layer works offline afterwards.")
                    .setPositiveButton("Download", (x, y) -> {
                        dlBtn.setVisibility(View.GONE); predNote.setText("Downloading terrain\u2026");
                        worker.execute(() -> {
                            String err = terrain.load(box[0], box[1], box[2], box[3], (done, total) -> h.post(() -> predNote.setText("Downloading terrain " + done + " of " + total + "\u2026")));
                            h.post(() -> { if (err != null) Toast.makeText(c, err, Toast.LENGTH_LONG).show(); recompute.run(); });
                        });
                    }).setNegativeButton("Not now", null).show();
        });
        { final Location l0 = here[0]; if (l0 != null) { mapView.setMe(l0.getLatitude(), l0.getLongitude(), l0.getAccuracy()); mapView.post(() -> mapView.center(l0.getLatitude(), l0.getLongitude(), 16)); } }
        final long opened = System.currentTimeMillis(); final boolean[] fellBack = { false };
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor rot = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        final SensorEventListener sl = new SensorEventListener() {
            final float[] rm = new float[9], or = new float[3];
            @Override public void onSensorChanged(SensorEvent e) {
                SensorManager.getRotationMatrixFromVector(rm, e.values); SensorManager.getOrientation(rm, or);
                double az = Math.toDegrees(or[0]);
                Location l = here[0];
                if (l != null) az += new android.hardware.GeomagneticField((float) l.getLatitude(), (float) l.getLongitude(), (float) l.getAltitude(), System.currentTimeMillis()).getDeclination();   // magnetic to true north
                heading[0] = (az % 360 + 360) % 360;
            }
            @Override public void onAccuracyChanged(Sensor s, int a) { }
        };
        if (rot != null) sm.registerListener(sl, rot, SensorManager.SENSOR_DELAY_UI);
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        final LocationListener ll = l -> here[0] = l;
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, ll, Looper.getMainLooper()); } catch (Throwable ignored) { }

        final Runnable tick = new Runnable() {
            @Override public void run() {
                svc.pollRssiMeasure();
                int dbm = svc.lastRssi();
                if (dbm != -999 && dbm > peak[0]) peak[0] = dbm;
                big.setText(dbm == -999 ? "--" : String.valueOf(dbm));
                big.setTextColor(dbm >= -70 ? Neo.RED : dbm >= -93 ? Neo.AMBER : Neo.CYAN);
                sub.setText((dbm == -999 ? "no reading" : "dBm  ·  " + SMeterView.sUnits(dbm)) + (peak[0] != -999 ? "  ·  peak " + peak[0] : "") + (offset[0] != 0 ? "  ·  tuned " + (offset[0] / 1000) + " kHz off" : ""));
                meter.setDbm(dbm);
                StringBuilder sb = new StringBuilder();
                sb.append(Double.isNaN(heading[0]) ? "No compass" : String.format(Locale.US, "Pointing %03.0f° %s", heading[0], compass(heading[0])));
                if (svc.fox().marks().size() != seen[0]) recompute.run();   // a mark added (or cleared)
                FoxHunt.Estimate est = svc.fox().estimate(); Location me = here[0]; FoxPredictor.Result pr = pred[0];
                if (est != null && me != null) {
                    double eLat = pr != null ? pr.lat : est.lat, eLon = pr != null ? pr.lon : est.lon;
                    float[] res = new float[2]; Location.distanceBetween(me.getLatitude(), me.getLongitude(), eLat, eLon, res);
                    String what = pr != null ? (pr.layers == 0 ? "Strongest mark" : "Fox estimate (" + pr.layerNames() + (pr.confidence() != null ? ", " + pr.confidence() : "") + ")")
                            : est.fromBearings ? "Fox estimate from " + est.used + " bearings" : "Strongest mark";
                    sb.append(String.format(Locale.US, "\n%s: %s %s (%03.0f°)", what, com.macdmr.radio.region.Region.km() ? com.macdmr.radio.repeaters.GeoUtil.dist(res[0], true) : String.format(Locale.US, "%.2f mi", res[0] / 1609.344), compass(res[1]), (res[1] + 360) % 360));
                } else if (svc.fox().marks().isEmpty()) sb.append("\nNo marks yet");
                info.setText(sb.toString());
                plot.update(me, heading[0]);
                if (me != null) mapView.setMe(me.getLatitude(), me.getLongitude(), me.getAccuracy());
                if (!fellBack[0] && !mapView.anyTile() && System.currentTimeMillis() - opened > 8000) { fellBack[0] = true; if (plot.getVisibility() != View.VISIBLE) swap.performClick(); }   // no map tiles (offline): the plot
                if (beeping[0] && dbm != -999 && System.currentTimeMillis() >= nextBeep[0]) {      // faster and higher as the signal rises
                    double s = Math.max(0, Math.min(1, (dbm + 125) / 70.0));
                    try { if (tg[0] == null) tg[0] = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60); tg[0].startTone(s > 0.66 ? android.media.ToneGenerator.TONE_DTMF_D : s > 0.33 ? android.media.ToneGenerator.TONE_DTMF_9 : android.media.ToneGenerator.TONE_DTMF_1, 40); } catch (Throwable ignored) { }
                    nextBeep[0] = System.currentTimeMillis() + (long) (900 - 800 * s);
                }
                h.postDelayed(this, 250);
            }
        };
        h.post(tick);

        mark.setOnClickListener(v -> {
            if (svc.isTransmitting()) { Toast.makeText(c, "Not while transmitting: the reading would be your own signal", Toast.LENGTH_SHORT).show(); return; }
            Location me = here[0]; int dbm = svc.lastRssi();
            if (me == null) { Toast.makeText(c, "No position yet: turn on location", Toast.LENGTH_LONG).show(); return; }
            if (dbm == -999) { Toast.makeText(c, "No signal reading yet", Toast.LENGTH_SHORT).show(); return; }
            svc.fox().add(new FoxHunt.Mark(me.getLatitude(), me.getLongitude(), dbm, heading[0], System.currentTimeMillis()));
            Toast.makeText(c, String.format(Locale.US, "Marked %d dBm%s", dbm, Double.isNaN(heading[0]) ? "" : String.format(Locale.US, " toward %03.0f°", heading[0])), Toast.LENGTH_SHORT).show();
        });
        beep.setOnClickListener(v -> { beeping[0] = !beeping[0]; beep.setText(beeping[0] ? "Beeper on" : "Beeper off"); });
        off.setOnClickListener(v -> {
            ChannelStore.Channel cur = svc.current(); if (cur == null) return;
            int[] steps = { 0, 10_000, 20_000, 30_000 };
            int k = 0; for (int i = 0; i < steps.length; i++) if (steps[i] == offset[0]) k = i;
            int next = steps[(k + 1) % steps.length];
            String e = svc.foxOffset(next);
            if (e != null) { Toast.makeText(c, e, Toast.LENGTH_LONG).show(); return; }
            offset[0] = next; peak[0] = -999;
            off.setText(next == 0 ? "Off-frequency" : "+" + (next / 1000) + " kHz");
        });
        map.setOnClickListener(v -> {
            FoxHunt.Estimate est = svc.fox().estimate(); FoxPredictor.Result pr = pred[0];
            if (est == null) { Toast.makeText(c, "Mark a reading first", Toast.LENGTH_SHORT).show(); return; }
            double eLat = pr != null ? pr.lat : est.lat, eLon = pr != null ? pr.lon : est.lon;
            try { c.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(Fox)", eLat, eLon, eLat, eLon)))); } catch (Throwable t) { Toast.makeText(c, "No map app", Toast.LENGTH_SHORT).show(); }
        });
        clear.setOnClickListener(v -> { svc.fox().clear(); peak[0] = -999; recompute.run(); });
        foxBtn.setOnClickListener(v -> { String r = svc.setFoxTx(!svc.foxTxOn()); Toast.makeText(c, r, Toast.LENGTH_LONG).show(); foxBtn.setText(svc.foxTxOn() ? "Stop the fox beacon" : "Start the fox beacon"); });

        AlertDialog a = Neo.showSheet(c, body);
        a.setOnDismissListener(x -> {
            h.removeCallbacks(tick);
            worker.shutdownNow(); mapView.shutdown();
            if (sm != null) sm.unregisterListener(sl);
            try { lm.removeUpdates(ll); } catch (Throwable ignored) { }
            if (tg[0] != null) tg[0].release();
            if (offset[0] != 0) svc.foxOffset(0);       // back on the channel
        });
    }

    static String compass(double deg) { String[] n = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" }; return n[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8]; }

    /** The marks around you: dots coloured by strength with their bearing, you in the middle, the estimate as a ring. */
    static final class PlotView extends View {
        private final FoxHunt fox; private Location me; private double heading = Double.NaN;
        private FoxPredictor.Result pred;                                    // the prediction's estimate, when its layers are on
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        PlotView(Context c, FoxHunt f) { super(c); fox = f; }
        void update(Location l, double hd) { me = l; heading = hd; invalidate(); }
        void setEstimate(FoxPredictor.Result r) { pred = r; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float d = Theme.dp(getContext()), w = getWidth(), hgt = getHeight(), cx = w / 2, cy = hgt / 2;
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF0A1622); cv.drawRoundRect(0, 0, w, hgt, 14 * d, 14 * d, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(0xFF1A2C3E);
            for (int i = 1; i <= 3; i++) cv.drawCircle(cx, cy, Math.min(cx, cy) * i / 3.2f, p);
            List<FoxHunt.Mark> ms = fox.marks(); FoxHunt.Estimate est = fox.estimate();
            if (est != null && pred != null) est = new FoxHunt.Estimate(pred.lat, pred.lon, true, 0);
            if (me == null && ms.isEmpty()) { p.setStyle(Paint.Style.FILL); p.setColor(Neo.DIM); p.setTextSize(13 * d); p.setTextAlign(Paint.Align.CENTER); cv.drawText("Marks appear here", cx, cy, p); return; }
            double lat0 = me != null ? me.getLatitude() : ms.get(0).lat, lon0 = me != null ? me.getLongitude() : ms.get(0).lon, cos = Math.cos(Math.toRadians(lat0));
            double maxM = 50;
            for (FoxHunt.Mark m : ms) maxM = Math.max(maxM, dist(m.lat, m.lon, lat0, lon0, cos));
            if (est != null) maxM = Math.max(maxM, dist(est.lat, est.lon, lat0, lon0, cos));
            float scale = (float) (Math.min(cx, cy) * 0.9 / maxM);
            int max = -999; for (FoxHunt.Mark m : ms) max = Math.max(max, m.dbm);
            for (FoxHunt.Mark m : ms) {
                float x = cx + (float) (Math.toRadians(m.lon - lon0) * 6371000 * cos) * scale, y = cy - (float) (Math.toRadians(m.lat - lat0) * 6371000) * scale;
                int col = m.dbm >= -70 ? Neo.RED : m.dbm >= -93 ? Neo.AMBER : Neo.CYAN;
                if (m.hasBearing()) {
                    float len = Math.min(cx, cy) * 0.8f; double th = Math.toRadians(m.bearing);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * d); p.setColor(Neo.alpha(col, m.dbm == max ? 0xCC : 0x66));
                    cv.drawLine(x, y, x + (float) Math.sin(th) * len, y - (float) Math.cos(th) * len, p);
                }
                p.setStyle(Paint.Style.FILL); p.setColor(col); cv.drawCircle(x, y, (m.dbm == max ? 6 : 4) * d, p);
            }
            if (est != null) {
                float x = cx + (float) (Math.toRadians(est.lon - lon0) * 6371000 * cos) * scale, y = cy - (float) (Math.toRadians(est.lat - lat0) * 6371000) * scale;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * d); p.setColor(Neo.GREEN); cv.drawCircle(x, y, 11 * d, p); cv.drawCircle(x, y, 3 * d, p);
            }
            if (me != null) {                                   // you: a white arrow along the compass heading
                p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
                double th = Double.isNaN(heading) ? 0 : Math.toRadians(heading);
                android.graphics.Path ar = new android.graphics.Path();
                ar.moveTo(cx + (float) Math.sin(th) * 12 * d, cy - (float) Math.cos(th) * 12 * d);
                ar.lineTo(cx + (float) Math.sin(th + 2.5) * 8 * d, cy - (float) Math.cos(th + 2.5) * 8 * d);
                ar.lineTo(cx + (float) Math.sin(th - 2.5) * 8 * d, cy - (float) Math.cos(th - 2.5) * 8 * d);
                ar.close(); cv.drawPath(ar, p);
            }
            p.setStyle(Paint.Style.FILL); p.setColor(Neo.DIM); p.setTextSize(11 * d); p.setTextAlign(Paint.Align.LEFT);
            double ring = maxM / (0.9 * 3.2);                 // metres between rings
            cv.drawText(com.macdmr.radio.region.Region.km() ? (ring >= 1000 ? String.format(Locale.US, "rings %.1f km apart", ring / 1000) : String.format(Locale.US, "rings %.0f m apart", ring))
                    : ring >= 1609 ? String.format(Locale.US, "rings %.1f mi apart", ring / 1609.344) : String.format(Locale.US, "rings %.0f m apart", ring), 10 * d, hgt - 10 * d, p);
            cv.drawText("N ↑", w - 34 * d, 18 * d, p);
        }
        private static double dist(double la, double lo, double la0, double lo0, double cos) { double x = Math.toRadians(lo - lo0) * 6371000 * cos, y = Math.toRadians(la - la0) * 6371000; return Math.sqrt(x * x + y * y); }
    }

    /**
     * The hunt on the map: the prediction's heat (translucent, cyan to red as it gets likelier) with the 50 % (bright) and
     * 90 % (faint) outlines, the bearing lines, the marks (coloured by strength; a ring = at the radio's floor) and the
     * estimate as a green ring.
     */
    static final class HuntLayer implements MapView.Overlay {
        private final float d; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private FoxPredictor.Result r; private List<FoxHunt.Mark> marks = new java.util.ArrayList<>();
        private android.graphics.Bitmap heat; private float[] edge50 = new float[0], edge90 = new float[0];
        HuntLayer(Context c) { d = Theme.dp(c); }

        void set(FoxPredictor.Result res, List<FoxHunt.Mark> ms) {
            r = res; marks = ms; heat = null; edge50 = edge90 = new float[0];
            if (r == null || !r.hasMap()) return;
            int nx = r.nx, ny = r.ny; float max = 0; for (float v : r.p) max = Math.max(max, v);
            int[] px = new int[nx * ny];
            for (int iy = 0; iy < ny; iy++)
                for (int ix = 0; ix < nx; ix++) {
                    float v = r.p[iy * nx + ix], t = max > 0 ? v / max : 0;
                    int a = v >= r.thr90 ? (int) (0x38 + 0x70 * Math.sqrt(t)) : (int) (0x30 * Math.sqrt(t));
                    px[(ny - 1 - iy) * nx + ix] = Neo.alpha(ramp(t), Math.min(0xB0, a));   // bitmap rows run north to south
                }
            heat = android.graphics.Bitmap.createBitmap(px, nx, ny, android.graphics.Bitmap.Config.ARGB_8888);
            edge50 = edges(r.thr50); edge90 = edges(r.thr90);
        }

        /** Cyan, green, amber, red: the app's palette from unlikely to likely. */
        private static int ramp(float t) {
            int[] stops = { Neo.CYAN, Neo.GREEN, Neo.AMBER, Neo.RED };
            float x = Math.max(0, Math.min(0.999f, t)) * 3; int k = (int) x; float f = x - k;
            int a = stops[k], b = stops[k + 1];
            int rr = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f), gg = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f), bb = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
            return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }

        /** The region's outline as line segments in grid units (x east, y south from the north-west corner). */
        private float[] edges(float thr) {
            int nx = r.nx, ny = r.ny; java.util.ArrayList<Float> out = new java.util.ArrayList<>();
            for (int iy = 0; iy < ny; iy++)
                for (int ix = 0; ix < nx; ix++) {
                    if (r.p[iy * nx + ix] < thr) continue;
                    float x = ix, y = ny - 1 - iy;
                    if (iy == ny - 1 || r.p[(iy + 1) * nx + ix] < thr) { out.add(x); out.add(y); out.add(x + 1); out.add(y); }          // north side
                    if (iy == 0 || r.p[(iy - 1) * nx + ix] < thr) { out.add(x); out.add(y + 1); out.add(x + 1); out.add(y + 1); }       // south
                    if (ix == 0 || r.p[iy * nx + ix - 1] < thr) { out.add(x); out.add(y); out.add(x); out.add(y + 1); }                  // west
                    if (ix == nx - 1 || r.p[iy * nx + ix + 1] < thr) { out.add(x + 1); out.add(y); out.add(x + 1); out.add(y + 1); }     // east
                }
            float[] a = new float[out.size()]; for (int i = 0; i < a.length; i++) a[i] = out.get(i); return a;
        }

        @Override public void draw(Canvas cv, MapView m) {
            if (r != null && heat != null) {
                float l = m.screenX(r.west), rt = m.screenX(r.east), t = m.screenY(r.north), b = m.screenY(r.south);
                cv.drawBitmap(heat, null, new android.graphics.RectF(l, t, rt, b), bmp);
                float sx = (rt - l) / r.nx, sy = (b - t) / r.ny;
                p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
                for (int pass = 0; pass < 2; pass++) {
                    float[] e = pass == 0 ? edge90 : edge50; if (e.length == 0) continue;
                    float[] q = new float[e.length]; for (int i = 0; i < e.length; i += 2) { q[i] = l + e[i] * sx; q[i + 1] = t + e[i + 1] * sy; }
                    p.setStrokeWidth((pass == 0 ? 1.2f : 2f) * d); p.setColor(pass == 0 ? Neo.alpha(Neo.AMBER_LIGHT, 0x80) : 0xFFFFFFFF);
                    cv.drawLines(q, p);
                }
            }
            int max = -999; for (FoxHunt.Mark k : marks) max = Math.max(max, k.dbm);
            boolean bearOn = r == null || (r.layers & FoxPredictor.BEARINGS) != 0;
            for (FoxHunt.Mark k : marks) {                              // bearing lines, 3 km long (they leave the screen when zoomed in)
                if (!k.hasBearing()) continue;
                double th = Math.toRadians(k.bearing), len = 3000, cos = Math.cos(Math.toRadians(k.lat));
                double la = k.lat + Math.toDegrees(Math.cos(th) * len / 6371000), lo = k.lon + Math.toDegrees(Math.sin(th) * len / (6371000 * cos));
                int col = k.dbm >= -70 ? Neo.RED : k.dbm >= -93 ? Neo.AMBER : Neo.CYAN;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * d); p.setColor(Neo.alpha(col, bearOn ? (k.dbm == max ? 0xD0 : 0x80) : 0x40));
                cv.drawLine(m.screenX(k.lon), m.screenY(k.lat), m.screenX(lo), m.screenY(la), p);
            }
            for (FoxHunt.Mark k : marks) {
                float x = m.screenX(k.lon), y = m.screenY(k.lat);
                int col = k.dbm >= -70 ? Neo.RED : k.dbm >= -93 ? Neo.AMBER : Neo.CYAN;
                p.setColor(0xFF07101A); p.setStyle(Paint.Style.FILL); cv.drawCircle(x, y, (k.dbm == max ? 7 : 5.5f) * d, p);
                p.setColor(col);
                if (k.dbm <= FoxPredictor.FLOOR_DBM) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * d); cv.drawCircle(x, y, 4 * d, p); }   // heard nothing here
                else cv.drawCircle(x, y, (k.dbm == max ? 5.5f : 4) * d, p);
            }
            if (r != null && r.layers != 0) {
                float x = m.screenX(r.lon), y = m.screenY(r.lat);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * d); p.setColor(Neo.GREEN); cv.drawCircle(x, y, 12 * d, p); cv.drawCircle(x, y, 3 * d, p);
            } else if (!marks.isEmpty()) {                               // no prediction: today's estimate
                FoxHunt.Estimate e = FoxHunt.estimate(marks);
                if (e != null && e.fromBearings) { float x = m.screenX(e.lon), y = m.screenY(e.lat); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * d); p.setColor(Neo.GREEN); cv.drawCircle(x, y, 12 * d, p); cv.drawCircle(x, y, 3 * d, p); }
            }
        }
    }
}
