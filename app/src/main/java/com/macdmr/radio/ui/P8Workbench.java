/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Modified for UNIWA P8 compatibility, 2026-10-27. See COPYRIGHT.
 */
package com.macdmr.radio.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.macdmr.radio.Frame;
import com.macdmr.radio.P8Protocol;

/** Offline-only adapter workbench. Deliberately has no RadioService/RootSetup/serial dependency. */
public final class P8Workbench {
    private P8Workbench() { }
    public static void show(Context c) {
        LinearLayout body = new LinearLayout(c); body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * Theme.dp(c)); body.setPadding(pad, pad, pad, pad);
        TextView intro = Neo.text(c, "Preview an analogue channel or decode a saved module reply. Nothing here opens a port, changes the stock app, tunes the radio or transmits. Wire values are experimental; this is not live radio support.", Neo.MUTED, 14, Neo.ui(c));
        body.addView(intro);
        if(com.macdmr.radio.BuildConfig.P8_OPEN_PROBE_REVIEWED){TextView rxScreen=Neo.button(c,"P8 receive-only radio", "radio",Neo.GREEN);body.addView(rxScreen);
            rxScreen.setOnClickListener(v->c.startActivity(new android.content.Intent(c,com.macdmr.radio.P8ReceiveActivity.class)));}
        String last=c.getSharedPreferences("p8_diagnostic",Context.MODE_PRIVATE).getString("lastResult","");
        TextView liveResult=Neo.text(c,last.isEmpty()?"Root-free development transport available to the authorised host test. No stored result yet.":
                "LAST ROOT-FREE TEST (historical, not current ownership):\n"+last,Neo.GREEN_LIGHT,13,Neo.mono(c));
        liveResult.setTextIsSelectable(true);body.addView(liveResult);
        TextView ownership = Neo.button(c, "Check stock-app ownership (passive)", "report", Neo.AMBER); body.addView(ownership);
        TextView ownershipText = Neo.text(c, "Reports visible state only. Does not stop Intercom or open a port.", Neo.MUTED, 13, Neo.mono(c));
        ownershipText.setTextIsSelectable(true); body.addView(ownershipText);
        ownership.setOnClickListener(v -> ownershipText.setText(com.macdmr.radio.P8OwnershipAudit.collect(c)));
        EditText rx = field(c, body, "Receive frequency (Hz)", "446006250", true);
        EditText tx = field(c, body, "Transmit frequency field (Hz; preview only)", "446006250", true);
        EditText bandwidth = field(c, body, "Bandwidth wire value (0 narrow, 1 wide)", "0", true);
        EditText volume = field(c, body, "Receive volume wire value", "1", true);
        EditText monitor = field(c, body, "Monitor wire value (also repeated in final byte)", "2", true);
        TextView preview = Neo.button(c, "Preview channel packet", "channels", Neo.CYAN); body.addView(preview);
        TextView result = Neo.text(c, "", Neo.CYAN_LIGHT, 13, Neo.mono(c)); result.setTextIsSelectable(true); body.addView(result);
        preview.setOnClickListener(v -> {
            try {
                Frame f = P8Protocol.analog(Integer.parseInt(rx.getText().toString().trim()), Integer.parseInt(tx.getText().toString().trim()),
                        Integer.parseInt(bandwidth.getText().toString().trim()), 0, 1, 0, 0, 0, 0, 2,
                        Integer.parseInt(volume.getText().toString().trim()), Integer.parseInt(monitor.getText().toString().trim()));
                byte[] packet = f.encode();
                result.setText("OFFLINE PREVIEW\n" + Frame.hex(packet, packet.length) + "\n19-byte body; power=0, SQ=1, no tones, power-save=2. Final byte follows stock monitor field. No packet sent.");
            } catch (IllegalArgumentException e) { result.setText("Check fields: " + e.getMessage()); }
        });
        EditText reply = field(c, body, "Saved reply hex (sample is the APK's SMS status fixture)", "6836020090B800010510", false);
        TextView decode = Neo.button(c, "Decode saved reply", "report", Neo.GREEN); body.addView(decode);
        TextView decoded = Neo.text(c, "", Neo.GREEN_LIGHT, 13, Neo.mono(c)); decoded.setTextIsSelectable(true); body.addView(decoded);
        decode.setOnClickListener(v -> {
            try { decoded.setText(P8Protocol.describe(P8Protocol.decodeHex(reply.getText().toString()))); }
            catch (IllegalArgumentException e) { decoded.setText("Rejected: " + e.getMessage()); }
        });
        ScrollView scroll = new ScrollView(c); scroll.addView(body);
        new AlertDialog.Builder(c).setTitle("P8 offline workbench").setView(scroll).setPositiveButton("Close", null).show();
    }

    private static EditText field(Context c, LinearLayout body, String label, String value, boolean number) {
        TextView title = Neo.text(c, label, Neo.MUTED, 12, Neo.ui(c)); body.addView(title);
        EditText e = new EditText(c); e.setText(value); e.setTextColor(Neo.CYAN_LIGHT);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.addView(e); return e;
    }
}
