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
 * Everything that differs between phones that carry this DMR module family, and phones with an SA818-class analog
 * module ({@link #protocol} "sa818", driven by {@link AtModuleEngine}). Detection: build fingerprint first, then which
 * sysfs nodes exist.
 */
public final class DeviceProfile {
    /** TINY_SERVICE: PrizeTinyService; UART_PCM: a second UART; CAMCORDER: Android's camcorder input carries the radio (W6). */
    public enum Audio { TINY_SERVICE, UART_PCM, CAMCORDER }

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
    /** "mcu68" (the DMR module's 68 … 10 frames, RadioEngine) or "sa818" (AT+DMO… text commands, AtModuleEngine). */
    public String protocol = "mcu68";
    /** sa818: the module's receive line, a GPIO input that reads 1 while the squelch is open (null = none). */
    public String rxDetectNode;
    /** sa818: {node, value} written in order when reception starts / ends and when the transmitter keys / unkeys (the stock app's audio and PTT lines). */
    public String[][] rxLinesOn, rxLinesOff, txLinesOn, txLinesOff;
    /**
     * Set when the module drives the speaker itself, outside Android's audio (the W6: module to amplifier through switches):
     * written after {@link #rxLinesOn} while Android's media volume is 0, so the speaker is silent and the recorded audio
     * stays. The module's own volume then follows the media volume.
     */
    public String[][] muteLines;

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

    /**
     * ANYSECU W6+ (UNISOC, analog UHF 400–470 MHz, 32-bit Android 12): an SA818-class module (PB202EU05) on /dev/ttyS2 at
     * 9600, driven with AT+DMO… commands, from the device report's serial sniff and the stock app's code
     * (docs/w6/ENGINE_DESIGN.md). Power is gpio322; reception is gpio189 (an input); the receive audio reaches Android on
     * the camcorder input (docs/w6/RX_AUDIO_TEST.md). Transmit is not tested on air: unverified, so it never transmits.
     * Never read the flyscale_misc attributes on this phone: two of them panic the kernel.
     */
    public static final DeviceProfile ANYSECU_W6 = new DeviceProfile(
            "ANYSECU W6+", "/dev/ttyS2", 9600, Audio.CAMCORDER, null, 0,
            "/sys/class/gpio/gpio322/value", "/sys/class/gpio/gpio188/value", "1",
            new String[] { "fly.privkey.poc.down", "fly.privkey.poc.up" },
            "com.flyscale.analogpoc",
            new String[] {
                    "allow untrusted_app console_device chr_file { open read write ioctl getattr lock }",
                    "typeattribute console_device mlstrustedobject" },
            null, true, false);
    static {
        ANYSECU_W6.id = "anysecu-w6plus"; ANYSECU_W6.revision = 1; ANYSECU_W6.protocol = "sa818";
        ANYSECU_W6.rxDetectNode = "/sys/class/gpio/gpio189/value";
        // the stock app's receStart / receEnd (speaker path; the camcorder input carries the audio while these are set)
        ANYSECU_W6.rxLinesOn = new String[][] { { "/sys/kernel/dmr_state/dmr_state", "1" }, { "/sys/kernel/ext_pa_en/ext_pa_en", "0" }, { "/sys/class/gpio/gpio183/value", "1" }, { "/sys/kernel/ext_pa_en/ext_pa_en", "1" } };
        ANYSECU_W6.muteLines = new String[][] { { "/sys/kernel/ext_pa_en/ext_pa_en", "0" } };   // the speaker amplifier (the stock app skips the speaker route at volume 0)
        ANYSECU_W6.rxLinesOff = new String[][] { { "/sys/kernel/dmr_state/dmr_state", "0" }, { "/sys/kernel/ext_pa_en/ext_pa_en", "0" }, { "/sys/class/gpio/gpio183/value", "0" } };
        // the stock app's startSpeaker / stopSpeaker: mic to the module, built-in mic, then PTT (used only once verified)
        ANYSECU_W6.txLinesOn = new String[][] { { "/sys/class/gpio/gpio222/value", "1" }, { "/sys/class/gpio/gpio179/value", "0" }, { "/sys/class/gpio/gpio188/value", "1" } };
        ANYSECU_W6.txLinesOff = new String[][] { { "/sys/class/gpio/gpio188/value", "0" }, { "/sys/class/gpio/gpio222/value", "0" } };
        ANYSECU_W6.scanDwellMs = 1500;
    }

    /** Any other phone: nothing is touched until a device report has been made and a profile written. */
    public static final DeviceProfile UNKNOWN = new DeviceProfile(
            Build.MANUFACTURER + " " + Build.MODEL + " (not supported yet)", "/dev/ttyS0", 57600, Audio.TINY_SERVICE, null, 0,
            null, null, "1", null, "", new String[0], null, false, false);

    /** The profile in use: a built-in one, or a newer one from the signed profile list, with any advisories applied. */
    public static DeviceProfile detect() { return ProfileRegistry.active(); }

    /** The profile built into this release for this phone (no profile list, no advisories). */
    public static DeviceProfile detectBuiltIn() {
        String m = (Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DEVICE).toLowerCase();
        return detectBuiltIn(m, new File("/sys/devices/platform/dmr009").exists(), new File("/sys/devices/platform/intercom").exists());
    }

    /** Model text first (the Armor 20WT has the same dmr009 node as the Armor 26 Ultra, on another port), then the nodes. */
    static DeviceProfile detectBuiltIn(String m, boolean dmr009, boolean intercom) {
        if (m.contains("armor 20wt") || m.contains("armor_20wt")) return ARMOR_20WT;
        if (m.contains("w6+") && m.contains("sl8541e")) return ANYSECU_W6;          // "UNISOC W6+ sl8541e_3h10_32b"
        if (dmr009 || m.contains("armor 26")) return ARMOR_26_ULTRA;
        if (intercom || m.contains("xplore")) return XPLORE_1;
        return UNKNOWN;
    }

    public SysfsGpio power() { return new SysfsGpio(powerNode, powerOnToken); }

    /** False on a phone whose module has no DMR (an SA818-class analog module): the DMR pages and choices are hidden. */
    public boolean dmr() { return !"sa818".equals(protocol); }
    /**
     * Whether the Messages page has anything to carry on this phone. Today it holds DMR texts, so it follows {@link #dmr};
     * once APRS or another text mode works on an analog-only radio (owner, 2026-10-27: expected on the W6), return true
     * here for it and the page comes back.
     */
    public boolean hasMessages() { return dmr(); }
    /** Whether the Contacts page is useful here: its entries are DMR IDs and talkgroups today; same rule as {@link #hasMessages}. */
    public boolean hasContacts() { return dmr(); }
    /**
     * Whether this phone's module firmware can be read and catalogued (its stock app carries the image, our loader can
     * dump it). False for an SA818-class module: no firmware file in the stock app, no read command in the module, so
     * there is nothing the firmware project could take from a submission and the variant check is skipped.
     */
    public boolean firmwareCatalogued() { return dmr(); }

    /** The same profile with other support flags (an advisory's restricted copy). */
    DeviceProfile copy(boolean supported, boolean verified) {
        DeviceProfile p = new DeviceProfile(name, controlPort, baud, audio, pcmPort, pcmBaud, powerNode, pttNode, powerOnToken, pttActions, stockApp, sepolicy, serialLib, supported, verified);
        p.id = id; p.revision = revision; p.source = source; p.txBlocked = txBlocked; p.restrictedBy = restrictedBy; p.globalSquelch = globalSquelch; p.lineSetup = lineSetup; p.scanDwellMs = scanDwellMs; p.protocol = protocol; p.rxDetectNode = rxDetectNode; p.rxLinesOn = rxLinesOn; p.muteLines = muteLines; p.rxLinesOff = rxLinesOff; p.txLinesOn = txLinesOn; p.txLinesOff = txLinesOff; p.minWriteGapMs = minWriteGapMs; p.directAudio = directAudio;
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
        p.protocol = o.optString("protocol", "mcu68"); p.rxDetectNode = o.optString("rxDetectNode", null);
        p.rxLinesOn = pairs(o.optJSONArray("rxLinesOn")); p.rxLinesOff = pairs(o.optJSONArray("rxLinesOff")); p.muteLines = pairs(o.optJSONArray("muteLines"));
        p.txLinesOn = pairs(o.optJSONArray("txLinesOn")); p.txLinesOff = pairs(o.optJSONArray("txLinesOff"));
        org.json.JSONObject da = o.optJSONObject("directAudio");
        if (da != null) p.directAudio = DirectAudio.fromJson(da);
        return p;
    }
    /** [[node, value], …] from the profile list; only /sys paths and short digit values (they end up on a root command line). */
    private static String[][] pairs(org.json.JSONArray a) throws org.json.JSONException {
        if (a == null) return null;
        String[][] out = new String[a.length()][];
        for (int i = 0; i < out.length; i++) {
            String[] p = strings(a.getJSONArray(i));
            if (p.length != 2 || !p[0].matches("/sys/[A-Za-z0-9_./-]{1,120}") || !p[1].matches("[0-9]{1,3}")) throw new org.json.JSONException("bad line " + i);
            out[i] = p;
        }
        return out;
    }
    private static String[] strings(org.json.JSONArray a) {
        if (a == null) return new String[0];
        String[] s = new String[a.length()]; for (int i = 0; i < s.length; i++) s[i] = a.optString(i);
        return s;
    }
}
