/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** The message forms sent and received (forms.db): the log behind the Forms list on the phone and in SignalDeck Web. */
public final class FormStore extends SQLiteOpenHelper {
    public static final class Row { public long id, ts; public boolean outgoing; public String peer = "", via = "", status = ""; public Forms.Form form; }

    public FormStore(Context c) { super(c, "forms.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE forms (_id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, outgoing INTEGER, peer TEXT, via TEXT, status TEXT, kind TEXT, formId TEXT, payload TEXT)"); }
    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) { }

    public synchronized long save(Row r) {
        ContentValues v = new ContentValues();
        v.put("ts", r.ts); v.put("outgoing", r.outgoing ? 1 : 0); v.put("peer", r.peer); v.put("via", r.via); v.put("status", r.status);
        v.put("kind", r.form.kind); v.put("formId", r.form.id); v.put("payload", Forms.encode(r.form));
        if (r.id > 0) { getWritableDatabase().update("forms", v, "_id=?", new String[] { String.valueOf(r.id) }); return r.id; }
        return r.id = getWritableDatabase().insert("forms", null, v);
    }
    public synchronized void setStatus(long id, String status) { ContentValues v = new ContentValues(); v.put("status", status); getWritableDatabase().update("forms", v, "_id=?", new String[] { String.valueOf(id) }); }
    public synchronized void delete(long id) { getWritableDatabase().delete("forms", "_id=?", new String[] { String.valueOf(id) }); }
    public synchronized List<Row> list(int limit) {
        List<Row> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id, ts, outgoing, peer, via, status, kind, formId, payload FROM forms ORDER BY ts DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                Row r = new Row(); r.id = c.getLong(0); r.ts = c.getLong(1); r.outgoing = c.getInt(2) == 1; r.peer = c.getString(3); r.via = c.getString(4); r.status = c.getString(5);
                r.form = new Forms.Form(); r.form.kind = c.getString(6); r.form.id = c.getString(7); r.form.f.putAll(Forms.decode(c.getString(8) == null ? "" : c.getString(8)));
                out.add(r);
            }
        }
        return out;
    }
    public Row get(long id) { for (Row r : list(1000)) if (r.id == id) return r; return null; }
}
