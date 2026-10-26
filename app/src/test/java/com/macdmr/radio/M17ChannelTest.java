/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio;

import com.macdmr.radio.ham.M17;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** M17 as a channel type: destination, channel access number, and the backup's type cell. */
public class M17ChannelTest {
    @Test public void destination_is_a_callsign_or_everyone() {
        assertEquals("@ALL", ChannelStore.Channel.cleanM17Dest(null));
        assertEquals("@ALL", ChannelStore.Channel.cleanM17Dest(""));
        assertEquals("@ALL", ChannelStore.Channel.cleanM17Dest("all"));
        assertEquals("W9XYZ", ChannelStore.Channel.cleanM17Dest("w9xyz"));
        ChannelStore.Channel ch = new ChannelStore.Channel();
        ch.type = ChannelStore.Channel.TYPE_M17;
        ch.m17dest = "n0call";
        ch.cc = 4;
        assertEquals("N0CALL", ch.m17Dest());
        assertEquals(4, ch.can());
    }

    @Test public void channel_access_number_is_in_the_voice_type_word() {
        assertEquals(0x0005, M17.streamVoice(0));
        assertEquals(0x0305, M17.streamVoice(3));
        assertEquals(0x0F05, M17.streamVoice(99));
    }

    @Test public void backup_type_cell_round_trips() {
        ChannelStore.Channel m = new ChannelStore.Channel();
        m.type = ChannelStore.Channel.TYPE_M17;
        m.cc = 2;
        m.m17dest = "W9XYZ";
        assertEquals("M17", Backup.typeCell(m));
        assertEquals(ChannelStore.Channel.TYPE_M17, Backup.channelKind(Backup.typeCell(m)));
        assertEquals(ChannelStore.Channel.TYPE_DMR, Backup.channelKind("Digital"));
        assertEquals(ChannelStore.Channel.TYPE_FM, Backup.channelKind("Analogue"));
        ChannelStore.Channel back = new ChannelStore.Channel();
        back.type = Backup.channelKind("M17");
        back.cc = m.can();
        back.m17dest = ChannelStore.Channel.cleanM17Dest(m.m17Dest());
        assertEquals(m.type, back.type);
        assertEquals(m.can(), back.can());
        assertEquals(m.m17Dest(), back.m17Dest());
        assertEquals(M17.streamVoice(back.can()), 0x0205);
    }
}
