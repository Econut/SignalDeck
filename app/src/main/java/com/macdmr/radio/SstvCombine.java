/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.macdmr.radio.pkt.SstvStack;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Combine several receptions of the same SSTV picture (gallery, SignalDeck Web) into a new, cleaner one. */
public final class SstvCombine {
    private SstvCombine() { }

    /** The new picture's file (null on failure) and a line for the user. */
    public static final class Result { public final File file; public final String message; Result(File f, String m) { file = f; message = m; } }

    public static Result combine(List<File> files) {
        if (files.size() < 2) return new Result(null, "Select at least two receptions of the same picture");
        List<int[]> px = new ArrayList<>(); int w = -1, h = -1;
        for (File f : files) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return new Result(null, "Could not read " + f.getName());
            if (w < 0) { w = b.getWidth(); h = b.getHeight(); }
            else if (b.getWidth() != w || b.getHeight() != h) { b.recycle(); return new Result(null, "The pictures are different sizes (different SSTV modes): they cannot be combined"); }
            int[] a = new int[w * h]; b.getPixels(a, 0, w, 0, 0, w, h); b.recycle(); px.add(a);
        }
        SstvStack.Result r = SstvStack.combine(px, w, h, 0.3);
        if (r.used < 2) return new Result(null, "These do not look like the same picture: nothing to combine");
        Bitmap out = Bitmap.createBitmap(r.argb, w, h, Bitmap.Config.ARGB_8888);
        File dir = files.get(0).getParentFile();
        File f = new File(dir, "SSTV_combined_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new java.util.Date()) + "_" + r.used + "x.png");
        try (FileOutputStream o = new FileOutputStream(f)) { out.compress(Bitmap.CompressFormat.PNG, 100, o); }
        catch (Exception e) { return new Result(null, "Could not save: " + e.getMessage()); }
        finally { out.recycle(); }
        int left = files.size() - r.used;
        return new Result(f, "Combined " + r.used + " receptions" + (left > 0 ? " (" + left + " left out: a different picture)" : "") + " → " + f.getName());
    }
}
