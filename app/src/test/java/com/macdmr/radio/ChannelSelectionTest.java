/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Several channels at once: the number lists behind delete, add to zone and take out of a zone. */
public class ChannelSelectionTest {
    @Test public void deleteHighestFirst() {
        // deleting 3 first would renumber 7 to 6 and 12 to 11 before they are reached
        assertEquals(Arrays.asList(12, 7, 3), ChannelStore.deleteOrder(Arrays.asList(3, 12, 7, 7)));
        assertTrue(ChannelStore.deleteOrder(Arrays.<Integer>asList()).isEmpty());
    }

    @Test public void zoneNumbers() {
        assertArrayEquals(new int[] { 1, 2, 5, 9 }, ChannelStore.withNumbers(new int[] { 9, 2 }, Arrays.asList(5, 1, 2, 0)));   // sorted, once each, no 0
        assertArrayEquals(new int[] { 9, 5 }, ChannelStore.withoutNumbers(new int[] { 9, 2, 5 }, Arrays.asList(2, 7)));
        List<Integer> none = Arrays.asList();
        assertArrayEquals(new int[] { 4 }, ChannelStore.withoutNumbers(new int[] { 4 }, none));
        assertArrayEquals(new int[0], ChannelStore.withoutNumbers(new int[] { 4 }, Arrays.asList(4)));   // the service refuses to save this (a zone is never left empty)
    }
}
