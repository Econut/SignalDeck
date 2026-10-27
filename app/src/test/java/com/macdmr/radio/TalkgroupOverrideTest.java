/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TalkgroupOverrideTest {
    private static ChannelStore.Channel dmr(long id, int tg, int... groups) {
        ChannelStore.Channel c = new ChannelStore.Channel(); c.id = id; c.number = (int) id; c.type = 0; c.contactType = 1; c.txContact = tg; c.groups = groups; c.name = "Ch" + id; return c;
    }
    private static ChannelStore.Contact contact(String name, int number, int type) { ChannelStore.Contact c = new ChannelStore.Contact(); c.name = name; c.number = number; c.type = type; return c; }

    @Test public void theProgrammedCallComesFirstThenTheGroupContacts() {
        List<ChannelStore.Contact> cs = new ArrayList<>(Arrays.asList(contact("Local", 9, 1), contact("Worldwide", 91, 1), contact("A friend", 3100001, 0), contact("State", 3100, 1)));
        List<TalkgroupOverride.Option> o = TalkgroupOverride.options(dmr(4, 9, 9), cs);
        assertEquals(3, o.size());
        assertTrue(o.get(0).programmed); assertEquals(9, o.get(0).number); assertEquals("TG 9  Local  (programmed)", o.get(0).label);
        assertEquals("TG 91  Worldwide", o.get(1).label);                       // the private contact is not offered; the channel's own TG not twice
        assertEquals(3100, o.get(2).number);
        ChannelStore.Channel priv = dmr(5, 3100001); priv.contactType = 0;
        o = TalkgroupOverride.options(priv, cs);
        assertEquals("ID 3100001  A friend  (programmed)", o.get(0).label);
        assertEquals(4, o.size());                                                // every group contact, TG 9 included
        assertEquals(1, TalkgroupOverride.options(dmr(6, 9), null).size());
    }

    @Test public void aPickedTalkgroupIsWrittenAndHeardUntilTheChannelChanges() {
        TalkgroupOverride t = new TalkgroupOverride();
        ChannelStore.Channel a = dmr(1, 9, 9, 91), b = dmr(2, 91, 91);
        assertTrue(t.set(a, 3100));
        t.onApply(a);                                                             // the same channel rewritten keeps it
        assertEquals(3100, t.talkgroup(a));
        assertArrayEquals(new int[] { 3100, 1 }, t.call(a));
        assertArrayEquals(new int[] { 3100, 9, 91 }, t.groups(a));
        assertEquals(0, t.talkgroup(b));
        assertArrayEquals(new int[] { 91, 1 }, t.call(b));
        t.onApply(b);                                                             // another channel: gone, also when coming back
        t.onApply(a);
        assertEquals(0, t.talkgroup(a));
        assertArrayEquals(new int[] { 9, 1 }, t.call(a));
        assertArrayEquals(new int[] { 9, 91 }, t.groups(a));
    }

    @Test public void theVfoEndsItAndCannotHaveOne() {
        TalkgroupOverride t = new TalkgroupOverride();
        ChannelStore.Channel a = dmr(1, 9, 9), vfo = dmr(-1, 9, 9);
        t.set(a, 91);
        t.onApply(vfo);
        assertEquals(0, t.talkgroup(a));
        assertFalse(t.set(vfo, 91));
        ChannelStore.Channel fm = dmr(3, 0); fm.type = 1;
        assertFalse(t.set(fm, 91));
    }

    @Test public void pickingTheOwnTalkgroupOrZeroGoesBack() {
        TalkgroupOverride t = new TalkgroupOverride();
        ChannelStore.Channel a = dmr(1, 9, 9);
        t.set(a, 91); t.set(a, 9); assertEquals(0, t.talkgroup(a));
        t.set(a, 91); t.set(a, 0); assertEquals(0, t.talkgroup(a));
        ChannelStore.Channel priv = dmr(2, 9); priv.contactType = 0;              // a private call to 9 is not the group 9: picking TG 9 is a change
        t.set(priv, 9); assertArrayEquals(new int[] { 9, 1 }, t.call(priv));
    }

    @Test public void aFullReceiveListGivesUpItsLastEntry() {
        int[] full = new int[32]; for (int i = 0; i < 32; i++) full[i] = 100 + i;
        int[] g = TalkgroupOverride.withTalkgroup(full, 7);
        assertEquals(32, g.length); assertEquals(7, g[0]); assertEquals(100, g[1]); assertEquals(130, g[31]);
        assertSame(full, TalkgroupOverride.withTalkgroup(full, 105));            // already listed: unchanged
        assertSame(full, TalkgroupOverride.withTalkgroup(full, 0));
    }
}
