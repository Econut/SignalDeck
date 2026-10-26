/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.macdmr.radio;

import com.macdmr.radio.ham.M17;
import com.macdmr.radio.web.WebHost;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        assertEquals(0x0185, M17.streamVoice(3));
        assertEquals(0x0785, M17.streamVoice(99));
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
        assertEquals(0x0105, M17.streamVoice(back.can()));
        // Colour code through power is 11 header columns. A short row shifts the destination off "M17 Dest".
        m.sq = 0;
        String cells = Backup.m17ToneCells(m) + "P1";
        String[] f = cells.split(",", -1);
        assertEquals(11, f.length);
        assertEquals("2", f[0]);
        assertEquals("", f[6]);
        assertEquals("Disabled", f[7]);
        assertEquals("Disabled", f[8]);
        assertEquals("Disabled", f[9]);
        assertEquals("P1", f[10]);
    }

    @Test public void web_save_keeps_an_m17_channel() throws Exception {
        ChannelStore.Channel ch = new ChannelStore.Channel();
        JSONObject b = new JSONObject();
        b.put("name", "Bench M17");
        b.put("type", 2);
        b.put("txMHz", 467.625);
        b.put("rxMHz", 467.625);
        b.put("m17dest", "w9xyz");
        b.put("cc", 4);
        assertNull(WebHost.applyChannel(ch, b));
        assertTrue(ch.isM17());
        assertEquals("W9XYZ", ch.m17Dest());
        assertEquals(4, ch.can());
        assertEquals(1, ch.band);
        String js = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/assets/web/editor.js")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(js.contains("[2, 'M17']"));
        assertTrue(js.contains("fM17"));
    }
}
