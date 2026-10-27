/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.ham;

import static org.junit.Assert.*;

import org.junit.Test;

/** Thread keys for M17 texts and pages. Contacts are a stand-in: callsign W1AW is 7, RIC 1000 is 9. */
public class MessageThreadsTest {
    private final MessageThreads.Contacts contacts = new MessageThreads.Contacts() {
        public long byCallsign(String c) { return "W1AW".equals(c) ? 7 : 0; }
        public long byRic(int ric) { return ric == 1000 ? 9 : 0; }
        public String name(long id) { return id == 7 ? "Club" : id == 9 ? "Pager" : ""; }
    };

    @Test public void m17KeysBySourceAndSharesEveryone() {
        MessageThreads.Key one = MessageThreads.m17("W1AW", "N0CALL", contacts);
        assertEquals(MessageThreads.Kind.CONTACT, one.kind);
        assertEquals(7, one.contactId);
        assertEquals("W1AW", MessageThreads.m17Reply(one));
        MessageThreads.Key unknown = MessageThreads.m17("N0CALL", "W1AW", contacts);
        assertEquals(MessageThreads.Kind.CALLSIGN, unknown.kind);
        assertEquals("N0CALL", unknown.label);
        assertEquals("N0CALL", MessageThreads.m17Reply(unknown));
        MessageThreads.Key all = MessageThreads.m17("N0CALL", "@ALL", contacts);
        assertEquals(MessageThreads.Kind.EVERYONE, all.kind);
        assertEquals("@ALL", MessageThreads.m17Reply(all));
    }

    @Test public void pagesKeyByContactThenSignatureThenRic() {
        MessageThreads.Key owned = MessageThreads.page(1000, "hello", contacts);
        assertEquals(MessageThreads.Kind.CONTACT, owned.kind);
        assertEquals(9, owned.contactId);
        MessageThreads.Key signed = MessageThreads.page(2000, "N0CALL: hello", contacts);
        assertEquals(MessageThreads.Kind.CALLSIGN, signed.kind);
        assertEquals("N0CALL", signed.label);
        MessageThreads.Key bare = MessageThreads.page(2000, "hello", contacts);
        assertEquals(MessageThreads.Kind.RIC, bare.kind);
        assertEquals("2000", bare.label);
    }
}
