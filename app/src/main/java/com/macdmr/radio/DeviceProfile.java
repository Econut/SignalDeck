/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 */
package com.macdmr.radio;

import android.os.Build;

import java.io.File;

/**
 * Everything that differs between phones that carry this DMR module family. The protocol itself
 * (RadioEngine) is shared. Detection: build fingerprint first, then which sysfs nodes exist.
 */
public final class DeviceProfile {
    public enum Audio { TINY_SERVICE, UART_PCM }

    public final String name;
    public final String controlPort;     // MCU command UART
    public final int baud;
    public final Audio audio;
    public final String pcmPort;         // UART_PCM only
    public final int pcmBaud;
    public final String powerNode;       // pwd / power enable
    public final String pttNode;         // hardware PTT line (module side)
    public final String powerOnToken;    // read() value when powered
    public final String[] pttActions;    // framework broadcasts for the hardware PTT key: down/up pairs (even index = down, odd = up)
    public final String stockApp;        // package to disable while we own the UART
    public final String[] sepolicy;      // Magisk sepolicy.rule lines an untrusted app needs
    public final String serialLib;       // the stock app's serial JNI library on /system (configures the line); null = stty fallback
    public final boolean supported;      // false = a phone we have no profile for: discovery mode only
    public final boolean verified;       // false = written from an APK and dumps, never run on the phone: no automatic take-over, no transmit
    public String id = "unknown";      // stable profile id (the profile list matches on it)
    public int revision;                 // higher = newer; the profile list only ever replaces a built-in profile with a higher one
    public String source = "built-in";  // or "updated" (from the signed profile list)
    public boolean txBlocked;            // an advisory switched transmit off (the owner can override it)
    public String restrictedBy;          // the advisory id that restricted this copy, or null
    public boolean globalSquelch;        // the module follows its global squelch (0x30) rather than the channel's sq byte: send it with every analog channel
    /** How long the scanner listens on each channel before moving on (the module's "receiving" report must arrive in time). */
    public long scanDwellMs = 1500;
    /** Least time between two channel writes (0 = none); quicker channel changes collapse into the last one. */
    public long minWriteGapMs;
    public String[][] lineSetup;         // {node, value} written in this order before the power line, every bring-up (null = none)
    /** TINY_SERVICE phones: how to reach the radio audio straight on the sound hardware when the ROM lacks the vendor service (null = not known). */
    public DirectAudio directAudio;

    /**
     * The radio's audio as plain ALSA streams on the phone's sound card, for a ROM without the vendor audio service
     * (a custom ROM or GSI). SignalDeck's root helper (sdalsa) sets the mixer controls and moves the PCM. Each control
     * is "NAME=VALUE" exactly as the kernel names it; the on lists are set before a stream opens, the off lists after it.
     */
    public static final class DirectAudio {
        public final int card, captureDevice, playbackDevice;
        public final String[] rxOn, rxOff, txOn, txOff;
        public DirectAudio(int card, int captureDevice, int playbackDevice, String[] rxOn, String[] rxOff, String[] txOn, String[] txOff) {
            this.card = card; this.captureDevice = captureDevice; this.playbackDevice = playbackDevice;
            this.rxOn = rxOn; this.rxOff = rxOff; this.txOn = txOn; this.txOff = txOff;
        }
        /** Letters, digits, space and _ - . , = only: the controls end up on a root command line. */
        static boolean safeControl(String c) { return c != null && c.matches("[A-Za-z0-9 _.,-]{1,80}=[A-Za-z0-9 _.,-]{1,40}"); }
        boolean valid() {
            if (card < 0 || card > 31 || captureDevice < 0 || captureDevice > 63 || playbackDevice < 0 || playbackDevice > 63) return false;
            for (String[] l : new String[][] { rxOn, rxOff, txOn, txOff }) { if (l == null) return false; for (String c : l) if (!safeControl(c)) return false; }
            return true;
        }
        static DirectAudio fromJson(org.json.JSONObject o) throws org.json.JSONException {
            DirectAudio d = new DirectAudio(o.getInt("card"), o.getInt("captureDevice"), o.getInt("playbackDevice"),
                    strings(o.optJSONArray("rxOn")), strings(o.optJSONArray("rxOff")), strings(o.optJSONArray("txOn")), strings(o.optJSONArray("txOff")));
            if (!d.valid()) throw new org.json.JSONException("directAudio: bad device number or control");
            return d;
        }
    }

    private DeviceProfile(String name, String controlPort, int baud, Audio audio, String pcmPort, int pcmBaud,
                          String powerNode, String pttNode, String powerOnToken, String[] pttActions,
                          String stockApp, String[] sepolicy, String serialLib, boolean supported, boolean verified) {
        this.name = name; this.controlPort = controlPort; this.baud = baud; this.audio = audio;
        this.pcmPort = pcmPort; this.pcmBaud = pcmBaud; this.powerNode = powerNode; this.pttNode = pttNode;
        this.powerOnToken = powerOnToken; this.pttActions = pttActions; this.stockApp = stockApp; this.sepolicy = sepolicy; this.serialLib = serialLib; this.supported = supported; this.verified = verified;
    }

    /** Ulefone Armor 26 Ultra (walkie-talkie model) — measured 2026-10-17. */
    public static final DeviceProfile ARMOR_26_ULTRA = new DeviceProfile(
            "Ulefone Armor 26 Ultra", "/dev/ttyS0", 57600, Audio.TINY_SERVICE, null, 0,
            "/sys/devices/platform/dmr009/pwd", "/sys/devices/platform/dmr009/ptt", "pwd :1",
            // the framework ("smartlinkkey", KEY_SAT keycode 381) sends android.intent.action.PTT.down/up for the side key;
            // com.interphone.ptt.down/up is what the stock app listens for when the key is assigned to it (measured 2026-10-18)
            new String[] { "android.intent.action.PTT.down", "android.intent.action.PTT.up", "com.interphone.ptt.down", "com.interphone.ptt.up" },
            "com.pri.prizeinterphone",
            new String[] {
                    "allow untrusted_app ttyS_device chr_file { open read write ioctl getattr lock }",
                    "typeattribute ttyS_device mlstrustedobject",
                    "allow untrusted_app sysfs_prizefactory file { open read write getattr }",
                    "typeattribute sysfs_prizefactory mlstrustedobject" },
            "/system/lib64/libinterphone_serial_port.so", true, true);

    /**
     * Ulefone Armor 20WT — from an owner's device report (beta.18, 2026-10-24) and its stock app (the same PriInterPhone
     * family as the Armor 26 Ultra: dmr009 pwd/ptt nodes, PrizeTinyService audio, the same command table). The
     * differences: the module is on /dev/ttyS1 (the stock app opens only that port, at 57600; ttyS0 here is another
     * device), the serial library is libserial_port_app.so in the stock app's own folder, bound to
     * com.serial.port.SerialPort, and the side key arrives as keycode 301 with com.interphone.ptt.down/up. The report's
     * probe got no answer because the module was powered off at the time. Not run on the phone yet: unverified.
     */
    public static final DeviceProfile ARMOR_20WT = new DeviceProfile(
            "Ulefone Armor 20WT", "/dev/ttyS1", 57600, Audio.TINY_SERVICE, null, 0,
            "/sys/devices/platform/dmr009/pwd", "/sys/devices/platform/dmr009/ptt", "pwd :1",
            new String[] { "com.interphone.ptt.down", "com.interphone.ptt.up", "android.intent.action.PTT.down", "android.intent.action.PTT.up" },
            "com.pri.prizeinterphone",
            new String[] {
                    "allow untrusted_app ttyS_device chr_file { open read write ioctl getattr lock }",
                    "typeattribute ttyS_device mlstrustedobject",
                    "allow untrusted_app sysfs_prizefactory file { open read write getattr }",
                    "typeattribute sysfs_prizefactory mlstrustedobject" },
            "/system/priv-app/PriInterPhone/lib/arm64/libserial_port_app.so", true, false);

    /**
     * Blackview Xplore 1 — from its QuickChat APK and an owner's dumps (docs/multi-device-xplore1-findings.md): ttyS0/ttyS1
     * are ttyS_device, the intercom_* nodes plain sysfs (mode 0777, so SELinux is the only gate; SysfsGpio writes them
     * through root rather than opening all of sysfs to apps). Not run on the phone yet: unverified.
     */
    public static final DeviceProfile XPLORE_1 = new DeviceProfile(
            "Blackview Xplore 1", "/dev/ttyS0", 57600, Audio.UART_PCM, "/dev/ttyS1", 230400,
            "/sys/devices/platform/intercom/intercom_pwd_control", "/sys/devices/platform/intercom/intercom_ptt_control", "1",
            new String[] { "com.android.intent.ptt.down", "com.android.intent.ptt.up" },
            "com.chamsion.quickchat",
            new String[] {
                    "allow untrusted_app ttyS_device chr_file { open read write ioctl getattr lock }",
                    "typeattribute ttyS_device mlstrustedobject" },
            null, true, false);
    static {
        // QuickChat's power-up (j0.a.run): power_control, pwd_control and ptt_control all 1; its power-off writes all 0, and
        // ptt_control 0 is its transmit state (setPttLaunch). So the PTT line goes to receive first, before the module is
        // powered, or a module left by QuickChat's power-off would come up with its PTT line asserted.
        ARMOR_26_ULTRA.id = "ulefone-armor26ultra"; ARMOR_26_ULTRA.revision = 1;
        // Measured on the owner's Armor 2026-10-24 (stock ROM, mixer read while tinyserver received and while it sent a
        // low-power test tone): receive is capture device 11 (8 kHz stereo) with I2S2 routed to UL2; transmit is playback
        // device 8 with DL8 routed to I2S1, the ES8311's DAC turned up and its ADC muted. Used only when the ROM has no
        // PrizeTinyService (custom ROMs / GSIs).
        ARMOR_26_ULTRA.directAudio = new DirectAudio(0, 11, 8,
                new String[] { "UL2_CH1 I2S2_CH1=1", "UL2_CH2 I2S2_CH2=1", "ADC SDP MUTE=0", "DAC VOLUME=0", "DAC SDP SRC MUX=SELECT SDP RIGHT DATA" },
                new String[] { "UL2_CH1 I2S2_CH1=0", "UL2_CH2 I2S2_CH2=0" },
                new String[] { "I2S1_HD_Mux=Low_Jitter", "I2S1_CH1 DL8_CH1=1", "I2S1_Out_Mux=Output_Widget", "ADC SDP MUTE=1", "DAC VOLUME=163", "DAC SDP SRC MUX=SELECT SDP LEFT DATA" },
                new String[] { "I2S1_CH1 DL8_CH1=0", "I2S1_Out_Mux=Normal", "I2S1_HD_Mux=Normal", "DAC VOLUME=0", "ADC SDP MUTE=0", "DAC SDP SRC MUX=SELECT SDP RIGHT DATA" });
        ARMOR_20WT.id = "ulefone-armor20wt"; ARMOR_20WT.revision = 1;
        XPLORE_1.id = "blackview-xplore1"; XPLORE_1.revision = 2;
        XPLORE_1.scanDwellMs = 2500;
        XPLORE_1.minWriteGapMs = 1000;        // QuickChat never writes channels less than 1 s apart; a burst of writes made the module refuse MON (2026-10-22)          // owner's tester 2026-10-22: the first scan stepped past an active channel; its receive report comes later than the Armor's
        XPLORE_1.globalSquelch = true;        // QuickChat sends 0x30 after each channel; the beta.3/5 trials showed the channel's sq 0 alone did not open it
        XPLORE_1.lineSetup = new String[][] {
                { "/sys/devices/platform/intercom/intercom_ptt_control", "1" },
                { "/sys/devices/platform/intercom/intercom_power_control", "1" } };
    }

    /** Recognised P8 wiring, deliberately offline until transport/GPIO ownership is verified on hardware. */
    public static final DeviceProfile UNIWA_P8 = new DeviceProfile(
            "UNIWA P8 (offline development)", P8Protocol.CONTROL_PORT, P8Protocol.CONTROL_BAUD,
            Audio.UART_PCM, P8Protocol.PCM_PORT, P8Protocol.PCM_BAUD,
            P8Protocol.POWER_NODE, P8Protocol.PTT_NODE, "1",
            new String[] { "dmr.play.SEND_START", "dmr.play.SEND_END" },
            "com.wtwd.ucee.dmr", new String[0], null, false, false);
    static { UNIWA_P8.id = "uniwa-p8"; UNIWA_P8.revision = 1; }

    /** Hardware integration is intentionally unavailable in the P8 development milestone. */
    public boolean offlineOnly() { return BuildConfig.P8_OFFLINE || "uniwa-p8".equals(id); }

    /** Any other phone: nothing is touched until a device report has been made and a profile written. */
    public static final DeviceProfile UNKNOWN = new DeviceProfile(
            Build.MANUFACTURER + " " + Build.MODEL + " (not supported yet)", "/dev/ttyS0", 57600, Audio.TINY_SERVICE, null, 0,
            null, null, "1", null, "", new String[0], null, false, false);

    /** The profile in use: a built-in one, or a newer one from the signed profile list, with any advisories applied. */
    public static DeviceProfile detect() { return ProfileRegistry.active(); }

    /** The profile built into this release for this phone (no profile list, no advisories). */
    public static DeviceProfile detectBuiltIn() {
        if (BuildConfig.P8_OFFLINE) return UNIWA_P8;
        String m = (Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DEVICE).toLowerCase();
        return detectBuiltIn(m, new File("/sys/devices/platform/dmr009").exists(), new File("/sys/devices/platform/intercom").exists());
    }

    /** Model text first (the Armor 20WT has the same dmr009 node as the Armor 26 Ultra, on another port), then the nodes. */
    static DeviceProfile detectBuiltIn(String m, boolean dmr009, boolean intercom) {
        if (m.matches(".*\\buniwa\\b.*") && m.matches(".*\\bp8\\b.*")) return UNIWA_P8;
        if (m.contains("armor 20wt") || m.contains("armor_20wt")) return ARMOR_20WT;
        if (dmr009 || m.contains("armor 26")) return ARMOR_26_ULTRA;
        if (intercom || m.contains("xplore")) return XPLORE_1;
        return UNKNOWN;
    }

    public SysfsGpio power() { return new SysfsGpio(powerNode, powerOnToken); }

    /** The same profile with other support flags (an advisory's restricted copy). */
    DeviceProfile copy(boolean supported, boolean verified) {
        DeviceProfile p = new DeviceProfile(name, controlPort, baud, audio, pcmPort, pcmBaud, powerNode, pttNode, powerOnToken, pttActions, stockApp, sepolicy, serialLib, supported, verified);
        p.id = id; p.revision = revision; p.source = source; p.txBlocked = txBlocked; p.restrictedBy = restrictedBy; p.globalSquelch = globalSquelch; p.lineSetup = lineSetup; p.scanDwellMs = scanDwellMs; p.minWriteGapMs = minWriteGapMs; p.directAudio = directAudio;
        return p;
    }

    /** A profile from the signed profile list (same fields as the constants above). */
    static DeviceProfile fromJson(org.json.JSONObject o) throws org.json.JSONException {
        DeviceProfile p = new DeviceProfile(o.getString("name"), o.getString("controlPort"), o.getInt("baud"), Audio.valueOf(o.getString("audio")),
                o.optString("pcmPort", null), o.optInt("pcmBaud", 0), o.optString("powerNode", null), o.optString("pttNode", null), o.optString("powerOnToken", "1"),
                strings(o.optJSONArray("pttActions")), o.optString("stockApp", ""), strings(o.optJSONArray("sepolicy")), o.optString("serialLib", null),
                o.optBoolean("supported", true), o.optBoolean("verified", false));
        p.id = o.getString("id"); p.revision = o.getInt("revision"); p.globalSquelch = o.optBoolean("globalSquelch", false); p.scanDwellMs = o.optLong("scanDwellMs", 1500); p.minWriteGapMs = o.optLong("minWriteGapMs", 0);
        org.json.JSONArray ls = o.optJSONArray("lineSetup");
        if (ls != null) { p.lineSetup = new String[ls.length()][]; for (int i = 0; i < ls.length(); i++) p.lineSetup[i] = strings(ls.getJSONArray(i)); }
        org.json.JSONObject da = o.optJSONObject("directAudio");
        if (da != null) p.directAudio = DirectAudio.fromJson(da);
        return p;
    }
    private static String[] strings(org.json.JSONArray a) {
        if (a == null) return new String[0];
        String[] s = new String[a.length()]; for (int i = 0; i < s.length; i++) s[i] = a.optString(i);
        return s;
    }
}
