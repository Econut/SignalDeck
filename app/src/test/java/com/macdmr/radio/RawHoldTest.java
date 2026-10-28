/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** M17 holds the radio's own receive audio back, and only the backends SignalDeck plays can do it. */
public class RawHoldTest {
    @Test public void heldOnAnM17ChannelOrWhileDecoding() {
        assertFalse("plain FM, nothing decoded: the channel is heard", RadioService.rawHeldFor(false, false));
        assertTrue("an M17 channel", RadioService.rawHeldFor(true, false));
        assertTrue("PKT -> M17 receive decoding on an FM channel", RadioService.rawHeldFor(false, true));
        assertTrue(RadioService.rawHeldFor(true, true));
    }

    @Test public void onlyTheRadioDrivenSpeakerCannotHold() {
        assertFalse("W6+: the module drives the speaker", new CamcorderAudio().appPlaysRadio());
        AudioBackend played = new AudioBackend() {     // the interface default: SignalDeck plays the radio's audio
            public void setSink(Sink s) { }
            public void setGate(Gate g) { }
            public String startRx(boolean speaker) { return null; }
            public String stopRx() { return null; }
            public String startTx() { return null; }
            public String stopTx() { return null; }
            public String playPcm(short[] mono) { return null; }
        };
        assertTrue(played.appPlaysRadio());
    }
}
