/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * On a phone SignalDeck has a profile for: is this phone's stock module firmware in the signed firmware catalogue?
 * Compares the version the module first reported (its stock firmware) and the stock app's firmware file hash with the
 * catalogue's images and variants. A variant the catalogue lacks is offered once for submission to the firmware
 * project's "Firmware variant seen" issue form, filled in, in the browser; the user checks it and presses Submit.
 * Nothing is sent by the app, and the firmware file itself never leaves the phone.
 */
public final class FirmwareVariantCheck {
    private FirmwareVariantCheck() { }

    public static final String ISSUES = "https://github.com/IIMacGyverII/AndroidRadio_Firmware_Project/issues/new";
    private static final String PREFS = "radio";
    /** SignalDeck's own bench and release tags (DMR003.FW27.V022, DMR003.R4a0.V022): never a phone's stock firmware. */
    private static final Pattern OWN_TAG = Pattern.compile("^DMR\\d{3}\\.(FW\\d+|R\\d[0-9A-Za-z]*)\\.V\\d+$");

    /** What the phone has. assetSha is "" when the stock app's firmware file could not be read. */
    public static final class Facts {
        public final String model, stockTag, stockPkg, stockPkgVersion, assetSha;
        public Facts(String model, String stockTag, String stockPkg, String stockPkgVersion, String assetSha) {
            this.model = model == null ? "" : model.trim(); this.stockTag = stockTag == null ? "" : stockTag.trim();
            this.stockPkg = stockPkg == null ? "" : stockPkg; this.stockPkgVersion = stockPkgVersion == null ? "" : stockPkgVersion;
            this.assetSha = assetSha == null ? "" : assetSha.trim().toLowerCase(Locale.US);
        }
        /** One key per variant: the prompt is shown once per key. */
        public String key() { return stockTag + "|" + assetSha; }
    }

    /** What the catalogue (and the built-in table) knows: version tags and file hashes. */
    public static final class Known {
        public final List<String> tags = new ArrayList<>(), shas = new ArrayList<>();
        public int serial;
    }

    /** null when the catalogue lists this variant (or there is nothing to judge); otherwise what is missing, for the issue's message. */
    public static String missing(Facts f, Known k) {
        if (f.stockTag.isEmpty() || OWN_TAG.matcher(f.stockTag).matches()) return null;
        boolean tag = k.tags.contains(f.stockTag), sha = f.assetSha.isEmpty() || k.shas.contains(f.assetSha);
        if (tag && sha) return null;
        if (!tag && !sha) return "the module version " + f.stockTag + " and the stock app's firmware file are not in the firmware catalogue";
        if (!tag) return "the module version " + f.stockTag + " is not in the firmware catalogue";
        return "the stock app's firmware file (sha256 " + f.assetSha + ") is not in the firmware catalogue";
    }

    /** Tags and hashes from a verified catalogue body, plus SignalDeck's built-in images. */
    public static Known known(byte[] catalogue) {
        Known k = new Known();
        for (com.macdmr.radio.fw.ImageTable.Row r : com.macdmr.radio.fw.ImageTable.rows()) { k.tags.add(r.tag); k.shas.add(r.sha256); }
        if (catalogue == null) return k;
        try {
            JSONObject o = new JSONObject(new String(catalogue, StandardCharsets.UTF_8));
            k.serial = o.optInt("serial");
            JSONArray im = o.optJSONArray("images");
            if (im != null) for (int i = 0; i < im.length(); i++) {
                JSONObject r = im.optJSONObject(i); if (r == null) continue;
                add(k.tags, r.optString("tag")); add(k.shas, r.optString("sha256").toLowerCase(Locale.US));
            }
            JSONArray devs = o.optJSONArray("variants");
            if (devs != null) for (int i = 0; i < devs.length(); i++) {
                JSONObject d = devs.optJSONObject(i); JSONArray vs = d == null ? null : d.optJSONArray("variants");
                if (vs != null) for (int j = 0; j < vs.length(); j++) {
                    JSONObject v = vs.optJSONObject(j); if (v == null) continue;
                    add(k.tags, v.optString("moduleVersion"));
                    if (!v.isNull("assetSha256")) add(k.shas, v.optString("assetSha256").toLowerCase(Locale.US));
                }
            }
        } catch (Exception ignored) { }
        return k;
    }
    private static void add(List<String> l, String s) { if (s != null && !s.isEmpty() && !l.contains(s)) l.add(s); }

    /** The issue form, filled in (field ids of .github/ISSUE_TEMPLATE/firmware-variant.yml). Only the phone model, versions and hash. */
    public static String issueUrl(Facts f, String why, String appVersion) {
        String stockApp = f.stockPkg.isEmpty() ? "" : f.stockPkg + (f.stockPkgVersion.isEmpty() ? "" : " " + f.stockPkgVersion);
        String msg = "SignalDeck " + appVersion + ": " + why + ".";
        return ISSUES + "?template=firmware-variant.yml"
                + "&title=" + enc("[variant] " + (f.stockTag.isEmpty() ? "unknown version" : f.stockTag) + " on " + f.model)
                + "&model=" + enc(f.model) + "&version=" + enc(f.stockTag) + "&stockapp=" + enc(stockApp)
                + "&hash=" + enc(f.assetSha) + "&message=" + enc(msg);
    }
    static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return ""; }
    }

    // ---- the stored result the Device page shows ----

    /** The open finding: key, url and message, or null when the catalogue lists this phone's firmware. */
    public static final class Finding {
        public final String key, url, why; public final boolean opened;
        Finding(String key, String url, String why, boolean opened) { this.key = key; this.url = url; this.why = why; this.opened = opened; }
    }

    public static Finding finding(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = p.getString("fwVariantKey", "");
        if (key.isEmpty()) return null;
        return new Finding(key, p.getString("fwVariantUrl", ""), p.getString("fwVariantWhy", ""), p.getBoolean("fwVariantOpened:" + key, false));
    }

    /** The user opened the issue form for this variant (the notice then says it is waiting for the catalogue). */
    public static void markOpened(Context c, String key) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("fwVariantOpened:" + key, true).apply(); }

    /**
     * Worker thread. Fetches the catalogue at most once a day, then stores the finding (or clears it).
     * @return the finding when it is new and was never offered before (the caller asks once), else null
     */
    public static Finding run(Context c, RadioService svc) {
        DeviceProfile dp = svc.profile();
        if (!dp.supported || !svc.isReady() || svc.stockFirmware().isEmpty()) return null;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - p.getLong("fwCatalogCheckedAt", 0) > 24L * 3600_000) {
            com.macdmr.radio.fw.FirmwareCatalog.fetch(c);
            p.edit().putLong("fwCatalogCheckedAt", now).apply();
        }
        if (com.macdmr.radio.fw.FirmwareCatalog.cached(c) == null) return null;          // no verified catalogue: nothing to judge against
        byte[] body;
        try { body = java.nio.file.Files.readAllBytes(new File(c.getFilesDir(), "firmware/catalog/firmware.json").toPath()); }   // the file cached() just verified
        catch (Exception e) { return null; }
        String sha = "";
        // the stock app's firmware file; not for the built-in factory tag, whose file the firmware page verifies (and reading it there keeps the backup copy)
        boolean builtIn = com.macdmr.radio.fw.ImageTable.FACTORY.tag.equals(svc.stockFirmware().trim());
        try { if (!builtIn && !dp.stockApp.isEmpty() && RootSetup.haveRoot()) sha = com.macdmr.radio.fw.FirmwareVault.factory(c).sha256; } catch (Throwable ignored) { }
        Facts f = new Facts(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, svc.stockFirmware(), dp.stockApp,
                com.macdmr.radio.fw.FirmwareVault.stockVersion(c, dp.stockApp), sha);
        String why = missing(f, known(body));
        if (why == null) { p.edit().remove("fwVariantKey").remove("fwVariantUrl").remove("fwVariantWhy").apply(); return null; }
        String ver; try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Throwable t) { ver = ""; }
        String url = issueUrl(f, why, ver);
        p.edit().putString("fwVariantKey", f.key()).putString("fwVariantUrl", url).putString("fwVariantWhy", why).apply();
        if (p.getBoolean("fwVariantAsked:" + f.key(), false)) return null;
        p.edit().putBoolean("fwVariantAsked:" + f.key(), true).apply();
        return finding(c);
    }
}
