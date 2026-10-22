/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.winlink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The Winlink mailbox on the phone: one file per message (its uncompressed B2 form, MID.b2f) in inbox/, outbox/ and
 * sent/ under the app's own files (private to SignalDeck). Read marks are empty MID.read files in inbox/.
 */
public final class WinlinkStore {
    public static final String INBOX = "inbox", OUTBOX = "outbox", SENT = "sent";
    private final File root;

    public WinlinkStore(File root) { this.root = root; for (String f : new String[] { INBOX, OUTBOX, SENT }) new File(root, f).mkdirs(); }

    public static final class Item { public final String folder; public final B2Message m; Item(String folder, B2Message m) { this.folder = folder; this.m = m; } }

    /** Newest first; unreadable files are skipped. */
    public synchronized List<Item> list(String folder) {
        List<Item> out = new ArrayList<>();
        File[] fs = new File(root, folder).listFiles((d, n) -> n.endsWith(".b2f"));
        if (fs == null) return out;
        for (File f : fs) { try { out.add(new Item(folder, B2Message.parse(Files.readAllBytes(f.toPath())))); } catch (Exception ignored) { } }
        out.sort((a, b) -> Long.compare(b.m.time(), a.m.time()));
        return out;
    }
    public synchronized int count(String folder) { File[] fs = new File(root, folder).listFiles((d, n) -> n.endsWith(".b2f")); return fs == null ? 0 : fs.length; }
    public synchronized int unread() {
        File[] fs = new File(root, INBOX).listFiles((d, n) -> n.endsWith(".b2f")); if (fs == null) return 0;
        int n = 0; for (File f : fs) if (!new File(f.getParentFile(), f.getName().replace(".b2f", ".read")).exists()) n++;
        return n;
    }
    public synchronized boolean isRead(String mid) { return new File(new File(root, INBOX), safe(mid) + ".read").exists(); }
    public synchronized void markRead(String mid) { try { new File(new File(root, INBOX), safe(mid) + ".read").createNewFile(); } catch (IOException ignored) { } }

    public synchronized void save(String folder, B2Message m) throws IOException {
        File f = new File(new File(root, folder), safe(m.mid) + ".b2f"), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(m.toBytes()); o.getFD().sync(); }
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw new IOException("could not save " + f.getName()); }
    }
    public synchronized boolean has(String folder, String mid) { return new File(new File(root, folder), safe(mid) + ".b2f").exists(); }
    /** Outbox -> sent. */
    public synchronized void markSent(String mid) {
        File f = new File(new File(root, OUTBOX), safe(mid) + ".b2f");
        if (f.exists() && !f.renameTo(new File(new File(root, SENT), f.getName()))) f.delete();
    }
    public synchronized void delete(String folder, String mid) {
        new File(new File(root, folder), safe(mid) + ".b2f").delete();
        new File(new File(root, folder), safe(mid) + ".read").delete();
    }

    private static String safe(String mid) { return mid.replaceAll("[^A-Za-z0-9_-]", "_"); }
}
