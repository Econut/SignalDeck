/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio.pkt;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Connected-mode AX.25 through the app's own modem: the control frames of a direct link (SABM, UA, RR, DISC) are
 * only 15 bytes + FCS. The decoder used to drop anything under 18 bytes (an APRS-era minimum), so a terminal or
 * Winlink connection without digipeaters could never complete (found on the HackRF gateway bench, 2026-10-22).
 */
public class ConnectedFramesTest {
    /** Two sessions wired back to back through AFSK audio (8 kHz, upsampled like the app). */
    @Test public void directLinkConnectsAndCarriesData() {
        final List<byte[]> toB = new ArrayList<>(), toA = new ArrayList<>();
        final StringBuilder gotB = new StringBuilder(); final String[] stateA = { "" };
        Ax25Session a = new Ax25Session("N0AAA", 0, new Ax25Session.Link() {
            @Override public void send(List<byte[]> frames) { for (byte[] f : frames) toB.addAll(over(f)); }
            @Override public void received(String text) { }
            @Override public void state(String s) { stateA[0] = s; }
        });
        Ax25Session b = new Ax25Session("N0BBB", 10, new Ax25Session.Link() {
            @Override public void send(List<byte[]> frames) { for (byte[] f : frames) toA.addAll(over(f)); }
            @Override public void received(String text) { gotB.append(text); }
            @Override public void state(String s) { }
        });
        b.setAcceptIncoming(true);
        long now = 0;
        a.connect("N0BBB-10", new String[0], now);
        for (int round = 0; round < 10; round++) {
            List<byte[]> x = new ArrayList<>(toB); toB.clear(); for (byte[] f : x) b.receive(f, now);
            List<byte[]> y = new ArrayList<>(toA); toA.clear(); for (byte[] f : y) a.receive(f, now);
            if (round == 1) a.sendText("Hello over a direct link\r", now);
            now += 2500; a.tick(now); b.tick(now);
        }
        assertTrue(stateA[0], stateA[0].startsWith("connected"));
        assertTrue(gotB.toString(), gotB.toString().contains("Hello over a direct link"));
    }

    @Test public void shortControlFramesDecode() {
        Ax25Session[] holder = new Ax25Session[1];
        List<byte[]> sent = new ArrayList<>();
        holder[0] = new Ax25Session("N0AAA", 0, new Ax25Session.Link() {
            @Override public void send(List<byte[]> frames) { sent.addAll(frames); }
            @Override public void received(String text) { }
            @Override public void state(String s) { }
        });
        holder[0].connect("N0BBB-10", new String[0], 0);
        assertEquals(1, sent.size());
        assertEquals("SABM is 15 bytes on a direct link", 15, sent.get(0).length);
        List<byte[]> back = over(sent.get(0));
        assertEquals(1, back.size());
        assertArrayEquals(sent.get(0), back.get(0));
    }

    /** One frame (no FCS) out through the modem and back. */
    static List<byte[]> over(byte[] frame) {
        short[] audio = AFSKGenerator.generateAFSKWithNRZI(Ax25.appendFcs(frame), 30);
        short[] padded = new short[audio.length + 4000]; System.arraycopy(audio, 0, padded, 2000, audio.length);
        return AFSKDecoder.decode(AfskBenchTest.up6(padded));
    }
}
