/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

/**
 * Which conversation a text belongs to. M17 texts key by the source callsign, and anything addressed
 * to {@code @ALL} shares one thread. A page keys by the contact that owns the RIC, else by a signed
 * callsign, else by the RIC itself.
 */
public final class MessageThreads {
    private MessageThreads() { }

    public enum Kind { CONTACT, CALLSIGN, RIC, EVERYONE }

    /** One conversation. {@code contactId} is set only for {@link Kind#CONTACT}. */
    public static final class Key {
        public final Kind kind;
        public final long contactId;
        public final String label;
        /** Where a reply goes: a callsign, {@code @ALL}, or a RIC. */
        public final String address;
        public Key(Kind kind, long contactId, String label, String address) {
            this.kind = kind; this.contactId = contactId;
            this.label = label == null ? "" : label;
            this.address = address == null ? "" : address;
        }
    }

    public interface Contacts {
        /** The contact that carries this callsign, or 0. */
        long byCallsign(String callsign);
        /** The contact that carries this RIC, or 0. */
        long byRic(int ric);
        String name(long contactId);
    }

    /** An M17 text. {@code dst} of {@code @ALL} is the shared everyone thread; otherwise the source callsign. */
    public static Key m17(String src, String dst, Contacts contacts) {
        if (dst != null && dst.equalsIgnoreCase("@ALL")) return new Key(Kind.EVERYONE, 0, "M17 everyone", "@ALL");
        String call = src == null ? "" : src.trim().toUpperCase(java.util.Locale.US);
        long id = contacts == null || call.isEmpty() ? 0 : contacts.byCallsign(call);
        if (id > 0) return new Key(Kind.CONTACT, id, contacts.name(id), call);
        return new Key(Kind.CALLSIGN, 0, call.isEmpty() ? "M17" : call, call);
    }

    /** Where a reply in that thread is addressed. */
    public static String m17Reply(Key key) { return key == null ? "" : key.address; }

    /** A received page. */
    public static Key page(int ric, String text, Contacts contacts) {
        long id = contacts == null ? 0 : contacts.byRic(ric);
        if (id > 0) return new Key(Kind.CONTACT, id, contacts.name(id), Integer.toString(ric));
        String signed = Pocsag.sender(text);
        if (signed != null) return new Key(Kind.CALLSIGN, 0, signed, signed);
        String ricText = Integer.toString(ric);
        return new Key(Kind.RIC, 0, ricText, ricText);
    }
}
