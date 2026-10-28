/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import java.util.Locale;

/**
 * Agold phones whose radio sits behind a USB bridge MCU and a vendor HAL (Unihertz Atom XL, measured 2026-10-28). The
 * bridge is a USB device on the phone's only USB controller, so while the radio is powered the controller is a host
 * and a computer on the cable loses the phone (adb, file transfer). A force-stop of the stock app leaves the bridge
 * powered (the HAL keeps /dev/ttyACM0 open). The stock app's own power-off ({@link #STOP_DEVICE} → HAL stopMcu) switches
 * it off but leaves the controller in mode "none"; toggling the MediaTek controller's cable mode (cmode 0, then 1)
 * puts it back in device mode, and adb over the cable answers about 2 s later.
 */
final class AgoldUsb {
    private AgoldUsb() { }

    /** The stock app's power-off broadcast (protected: the shell user is refused, root may send it). */
    static final String STOP_DEVICE = "agui.intercom.intent.action.STOP_DEVICE";
    static final String BRIDGE_PORT = "/dev/ttyACM0";
    /** The bridge driver's power node: "power on = 1" while the radio is powered. */
    static final String MCU_POWER = "/sys/bus/platform/drivers/mcu/mcu_power";

    static final String WARNING = "this phone's radio is a USB device on the phone's only USB port: while the radio is on, "
            + "a computer on the USB cable loses the phone (adb, file transfer). The checks give the port back when they "
            + "close the stock app; for bench work use Wi-Fi ADB";

    /** True for a getprop value of {@code ro.agold.extmodule.hal} that marks the bridge HAL. */
    static boolean bridged(String extmoduleHalProp) { return "yes".equals(extmoduleHalProp == null ? "" : extmoduleHalProp.trim()); }

    /** True when this phone has the bridge HAL (root). */
    static boolean bridged() { return bridged(RootSetup.run("getprop ro.agold.extmodule.hal")); }

    /**
     * The USB controller directory holding {@code cmode}, from the resolved path of a /sys/class/udc entry
     * ({@code /sys/devices/platform/11201000.usb3/udc/musb-hdrc} → {@code /sys/devices/platform/11201000.usb3}), or null.
     */
    static String controllerDir(String udcPath) {
        String p = udcPath == null ? "" : udcPath.trim();
        int i = p.lastIndexOf("/udc/");
        return i > 0 && p.startsWith("/sys/devices/") ? p.substring(0, i) : null;
    }

    /** From the {@link #MCU_POWER} text: true while powered, false when off, null when it says neither (node missing). */
    static Boolean powered(String mcuPower) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("power on\\s*=\\s*([01])").matcher(mcuPower == null ? "" : mcuPower);
        return m.find() ? "1".equals(m.group(1)) : null;
    }

    /**
     * Power the radio off the way the stock app does and give the USB port back to the socket. Run before the app is
     * force-stopped (the broadcast needs it running). The cable mode is toggled only once the bridge driver says the
     * radio is off, so a radio that is still powered is never cut off underneath its HAL. (Not {@link #BRIDGE_PORT}: its
     * node outlives the power-off while the HAL still has it open, measured 2026-10-28.)
     */
    static String release(String pkg) throws InterruptedException {
        StringBuilder r = new StringBuilder();
        boolean running = !RootSetup.run("pidof " + pkg + " || true").replaceAll("[^0-9]", "").isEmpty();
        if (running) r.append("power-off (").append(STOP_DEVICE).append("): ").append(RootSetup.run("am broadcast -a " + STOP_DEVICE + " 2>&1 | tail -1")).append('\n');
        else r.append("stock app not running: no power-off sent\n");
        int waited = 0;
        Boolean on = powered(RootSetup.run("cat " + MCU_POWER + " 2>/dev/null"));
        while (Boolean.TRUE.equals(on) && waited < 6000) { Thread.sleep(500); waited += 500; on = powered(RootSetup.run("cat " + MCU_POWER + " 2>/dev/null")); }
        if (on == null) return r.append("no ").append(MCU_POWER).append(": the radio's power state is unknown, the USB port was left as it is (a restart gives it back)\n").toString();
        if (on) return r.append("the radio is still powered after 6 s (").append(MCU_POWER).append("): the USB port was left as it is (a restart gives it back)\n").toString();
        r.append(String.format(Locale.US, "the radio is off after %.1f s (%s = 0)\n", waited / 1000.0, MCU_POWER));
        String dir = null;
        for (String u : RootSetup.run("for u in /sys/class/udc/*; do readlink -f $u; done").split("\n")) if ((dir = controllerDir(u)) != null) break;
        if (dir == null || RootSetup.run("[ -f " + dir + "/cmode ] && echo yes").trim().isEmpty())
            return r.append("no USB controller cable-mode node found: a computer on the USB cable may need a restart of the phone to see it again\n").toString();
        RootSetup.run("echo 0 > " + dir + "/cmode; sleep 1; echo 1 > " + dir + "/cmode");
        return r.append("USB port given back to the socket (").append(dir).append("/cmode 0, then 1)\n").toString();
    }

    static final String USB_PATH = "/sys/bus/platform/drivers/mcu/usb_path", RSTMCU = "/sys/bus/platform/drivers/mcu/rstmcu", HAL_SERVICE = "aguiextmodule";

    /**
     * Ask the bridge and the module who they are, with nobody else on the line (Atom XL, measured by hand 2026-10-28):
     * stop the HAL, switch the USB port to host ({@link #USB_PATH} 1: {@link #MCU_POWER} alone powers the bridge
     * without it), power the bridge and release its reset ({@link #RSTMCU} 0, as the HAL does), wait for {@link #BRIDGE_PORT}, send the stock app's own two version queries (route
     * 00 cmd 01, route 01 cmd 0x34), then power off, give the port back to the socket and start the HAL again, in one
     * root script so the clean-up runs whatever happened. Nothing tunes, keys or changes a setting. Only while the radio
     * is off and the stock app is not running. The stock app updates the bridge's firmware when the bridge does not
     * answer its version query at start-up, so the result says whether the bridge answered.
     */
    static String identityProbe(java.io.File dir, String stockApp) {
        if (!RootSetup.run("pidof " + stockApp + " || true").replaceAll("[^0-9]", "").isEmpty()) return "not run: " + stockApp + " is running\n";
        if (!Boolean.FALSE.equals(powered(RootSetup.run("cat " + MCU_POWER + " 2>/dev/null")))) return "not run: the radio is not off (" + MCU_POWER + ")\n";
        java.io.File q1 = new java.io.File(dir, "agold_q_bridge.bin"), q2 = new java.io.File(dir, "agold_q_module.bin");
        try {
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(q1)) { o.write(AgoldFrames.request(0x00, 0x01, 0x01)); }
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(q2)) { o.write(AgoldFrames.request(0x01, 0x34, 0x01)); }
        } catch (java.io.IOException e) { return "not run: " + e + "\n"; }
        String ask = "rm -f $R; ( timeout 3 cat " + BRIDGE_PORT + " > $R ) & sleep 0.3; cat $Q > " + BRIDGE_PORT + "; wait; echo \"$N: $(od -An -tx1 $R | tr -d '\\n')\"; ";
        // the HAL's own start-up (traced): usb_path 1, mcu_power 1, 0.75 s, rstmcu 0 (after a fresh boot the bridge sits in
        // reset and never enumerates without it); when the controller refuses device -> host, go through "none" and retry
        String powerUp = "echo 1 > " + USB_PATH + "; echo 1 > " + MCU_POWER + "; sleep 0.75; echo 0 > " + RSTMCU + "; ";
        String out = RootSetup.run("S=stty; [ -x /data/adb/magisk/busybox ] && S='/data/adb/magisk/busybox stty'; R=/data/local/tmp/signaldeck_agold_reply; "
                + "stop " + HAL_SERVICE + "; sleep 1; " + powerUp
                + "i=0; while [ ! -e " + BRIDGE_PORT + " ] && [ $i -lt 6 ]; do sleep 0.5; i=$((i+1)); done; "
                + "if [ ! -e " + BRIDGE_PORT + " ]; then echo 'retry: through none'; echo 0 > " + MCU_POWER + "; sleep 1; echo 0 > " + USB_PATH + "; sleep 0.5; " + powerUp
                + "i=0; while [ ! -e " + BRIDGE_PORT + " ] && [ $i -lt 20 ]; do sleep 0.5; i=$((i+1)); done; fi; "
                + "if [ -e " + BRIDGE_PORT + " ]; then echo \"port: after $((i*5)) tenths of a second\"; sleep 2; $S -F " + BRIDGE_PORT + " 115200 raw -echo 2>&1; "
                + "Q=" + q1.getAbsolutePath() + "; N=bridge; " + ask + "Q=" + q2.getAbsolutePath() + "; N=module; " + ask
                // what the draft profile needs, while the bridge is up: its port and label, the radio's USB sound card
                + "echo \"" + PORT_LINE + BRIDGE_PORT + " $(ls -Z " + BRIDGE_PORT + " | cut -d' ' -f1) held by /vendor/bin/hw/vendor.mediatek.hardware." + HAL_SERVICE + "@1.0-service (stopped for the probe)\"; "
                + "for s in /proc/asound/card[1-9]/stream*; do [ -f $s ] && { echo \"-- $s\"; cat $s; }; done; "
                + "else echo 'port: none after 10 s'; fi; "
                + "echo 0 > " + MCU_POWER + "; sleep 1; echo 0 > " + USB_PATH + "; sleep 0.5; "
                + "for u in /sys/class/udc/*; do d=$(readlink -f $u); d=${d%/udc/*}; [ -f $d/cmode ] && { echo 0 > $d/cmode; sleep 1; echo 1 > $d/cmode; }; done; "
                + "start " + HAL_SERVICE + "; sleep 1; rm -f $R; echo \"after: $(cat " + MCU_POWER + "), HAL $(pidof vendor.mediatek.hardware." + HAL_SERVICE + "@1.0-service >/dev/null && echo running || echo NOT running)\"");
        q1.delete(); q2.delete();
        return describe(out);
    }

    static final String MODULE_ANSWERED = "the module answered through the bridge: ", BRIDGE_SILENT = "the bridge did not answer";

    /** The probe's raw lines in words: each reply decoded and its checksum checked. */
    static String describe(String out) {
        StringBuilder r = new StringBuilder();
        String port = null, bridge = null, module = null, after = null; StringBuilder extra = new StringBuilder();
        for (String l : out.split("\n")) {
            String t = l.trim();
            if (t.startsWith("port: ")) port = t.substring(6);
            else if (t.startsWith("bridge: ")) bridge = t.substring(8);
            else if (t.startsWith("module: ")) module = t.substring(8);
            else if (t.startsWith("after: ")) after = t.substring(7).replace("power on = 0", "off").replace("power on = 1", "STILL ON");
            else if (t.startsWith(PORT_LINE) || t.startsWith("-- /proc/asound/") || t.matches("(Playback|Capture):|(Format|Channels|Rates): .*")) extra.append(l).append('\n');
        }
        r.append(BRIDGE_PORT).append(": ").append(port == null ? "?" : port).append('\n');
        r.append(reply("bridge version (route 00, cmd 0x01)", bridge, 0x00, 0x01));
        r.append(reply("module version (route 01, cmd 0x34)", module, 0x01, 0x34));
        String b = text(bridge, 0x00, 0x01), m = text(module, 0x01, 0x34);
        if (m != null) r.append(MODULE_ANSWERED).append(m).append(b != null ? " (bridge " + b + ")" : "").append('\n');
        if (b == null) r.append(BRIDGE_SILENT).append(": the stock app updates the bridge's firmware when that happens at its start-up; restart the phone before opening it\n");
        r.append(extra);
        r.append("clean-up: radio ").append(after == null ? "?" : after).append('\n');
        return r.toString();
    }

    private static String reply(String what, String hex, int route, int cmd) {
        if (hex == null) return "  " + what + ": not asked\n";
        if (hex.trim().isEmpty()) return "  " + what + ": no reply in 3 s\n";
        for (AgoldFrames.Frame f : AgoldFrames.find(hex.trim().toUpperCase(java.util.Locale.US).replaceAll("\\s+", " ")))
            if (f.route() == route && f.cmd() == cmd && f.reply())
                return "  " + what + ": \"" + AgoldFrames.bodyText(f) + "\", checksum " + (AgoldFrames.checksumOk(f) ? "ok" : "WRONG") + " (" + hex.trim() + ")\n";
        return "  " + what + ": no reply frame in (" + hex.trim() + ")\n";
    }

    /** The decoded body of a checksum-valid reply, or null. */
    static String text(String hex, int route, int cmd) {
        if (hex == null) return null;
        for (AgoldFrames.Frame f : AgoldFrames.find(hex.trim().toUpperCase(java.util.Locale.US).replaceAll("\\s+", " ")))
            if (f.route() == route && f.cmd() == cmd && f.reply() && AgoldFrames.checksumOk(f)) return AgoldFrames.bodyText(f);
        return null;
    }

    /** USB devices and USB sound card streams as root, taken while the radio is on (the bridge exists only then). */
    static String whileOn() {
        return RootSetup.run("for d in /sys/bus/usb/devices/*; do n=${d##*/}; "
                + "if [ -f $d/idVendor ]; then case $n in usb*) ;; *) echo \"  $n $(cat $d/idVendor):$(cat $d/idProduct)  $(cat $d/manufacturer 2>/dev/null) $(cat $d/product 2>/dev/null)\";; esac; "
                + "elif [ -f $d/bInterfaceClass ]; then echo \"           $n: class $(cat $d/bInterfaceClass) $(basename $(readlink $d/driver) 2>/dev/null)\"; fi; done; "
                + "cat /proc/asound/cards; for s in /proc/asound/card[1-9]/stream*; do [ -f $s ] && { echo \"-- $s\"; cat $s; }; done; "
                + "for t in /dev/ttyACM*; do [ -e $t ] || continue; h=''; for p in /proc/[0-9]*; do ls -l $p/fd 2>/dev/null | grep -q \" $t\\$\" && h=\"$h $(tr '\\0' ' ' < $p/cmdline | cut -c1-80)\"; done; "
                + "echo \"" + PORT_LINE + "$t $(ls -Z $t | cut -d' ' -f1) held by${h:- nobody}\"; done") + "\n";
    }

    static final String PORT_LINE = "bridge port: ";

    /** {port, SELinux label, holder} from the {@link #PORT_LINE} of a watch, or null. */
    static String[] port(String watch) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(PORT_LINE) + "(/dev/\\S+) (u:object_r:[A-Za-z0-9_]+:s0)\\S* held by ([^\\n]+)").matcher(watch == null ? "" : watch);
        return m.find() ? new String[] { m.group(1), m.group(2), m.group(3).trim() } : null;
    }
}
