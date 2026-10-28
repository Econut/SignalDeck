package com.macdmr.radio.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/** The channel editor names the field instead of showing Java's "empty String". */
public class EditorNumberTest {
    @Test public void plainMessages() {
        assertEquals(446.00625, ChannelEditorView.number(" 446.00625 ", "x"), 1e-9);
        try { ChannelEditorView.number("  ", "the receive frequency (MHz)"); fail(); } catch (IllegalArgumentException e) { assertEquals("Enter the receive frequency (MHz)", e.getMessage()); }
        try { ChannelEditorView.number("44o.1", "the send frequency (MHz)"); fail(); } catch (IllegalArgumentException e) { assertEquals("\"44o.1\" is not a number: check the send frequency (MHz)", e.getMessage()); }
    }
}
