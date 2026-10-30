# P8 phone-only power management

Main.9c adds P8 MENU → Radio off / Phone only and respects Device → Exit → Turn radio off. This closes the receiver/scan/audio first, releases its wake lock, then performs the stock fixed audio4 → power0 sequence with both UARTs closed and PTT released. It does not launch Intercom on the off path. Connect/open reverses this through stock power1 → audio1, a hardware-startup wait, own module initialization and acknowledged tune. Channels and audio preferences are unchanged.

Automatic startup setup includes an explicitly saved Phone-only after reboot checkbox. It defaults off on a new install. The owner requested it for this unit. The small power preference is in device-protected storage; the receiver handles LOCKED_BOOT_COMPLETED and BOOT_COMPLETED without launching a foreground radio service. A persisted boot_count attempt marker prevents a second operation/retry in the same boot. Failure is logged and shown in setup.

## Ownership and limitations

Boot power-off requires Intercom's stopped flag and no SignalDeck UART session. With the previously approved closed-session/power-node checks, PTT0 receives only the fixed release1 once. It then uses the stock power-off sequence. If Intercom is not stopped, an operation is active, a node is unexpected or a write fails, it skips/stops without trying to stop Intercom behind the lockscreen. It never disables the stock package/boot receiver, changes its auto-start setting, or invokes a generic root flow.

Therefore this is not a guarantee of default-off on every possible P8 boot: opening Intercom clears its stopped flag, and its own enabled BootReceiver may launch it according to its saved auto-start preference. The helper can hand over after unlocking, but it does not fight another owner at boot. A fresh installation needs setup consent first. Boot and ordinary off/on behavior must be tested on the actual firmware. App power-off requests do not constitute an independent current measurement; battery savings are unquantified.

## Important kernel finding

The factory Android boot image (SHA2562f0feb60633afc3e054303927c422054b51b7d022925831c67483ada6e61f100) contains the matching driver. Host-only read/decompression left factory firmware unchanged. The decompressed kernel SHA256 isca5841ac2b5ab401c2499a54cde77cd144fedfc75a7134ed981b7ffaea2f6e14; Image link base0xffffff8008080000.

These are offsets in the decompressed host Image, not flash/physical addresses:

- driver attribute dmr_pwr at0x183a080 points to show0x985bd0 and store0x985c10. Show passes constant1 to a %d format; it does not read the stored power state.
- auctusctl attribute0x183a120 points to show0x98617c and store0x9861b8. Show likewise formats constant1 (%x).
- power store parses the request, handles enable1 and disable0, and updates the driver's internal state. Its disable branch uses the existing GPIO/pinctrl operations, not firmware/calibration writes.
- auctusctl store dispatches byte4 to the pulse/delay sequence at0x986224, including4100ms delay. This explains why shutdown takes several seconds. Stock t2.d.h uses audio4 then power0.

Consequently neither cat dmr_pwr nor cat auctusctl proves that the module is powered on/off or that the last written value was echoed. The corrected code verifies exact-node write completion, released PTT and expected constant driver reads. An earlier implementation incorrectly expected0/4 readback and stopped; it was not retried. Native checks independently refuse power writes with UART descriptors open, TX/watchdog active or PTT not released. Partial failure stops without reversal/retry.

Kernel/APK binaries, disassembly working files, private logs and credentials remain outside this repository. No firmware/root/calibration operation or new RF transmission is included in these tests.

## Verification

151 focused host tests passed, including power ownership/closed-UART guards, partial-state refusal, write/read failure without retry, fixed enable/off sequence and separate OFF presentation. Native ABIs/release/lintVital build are checked separately. Live main.9c: manual close/off passed15:08:39–43 (zero microphone bytes); a normal reboot then completed the off sequence15:10:17 before unlocking, with no Intercom/RadioService running. Unlock and open at15:10:58 led to own enable, fresh boot banner/init/version and tune ACK42ms at15:11:04. No stock process start observed since boot, no duplicate shutdown after unlock, PTT1. The phone-only boot setting is enabled for the owner’s test unit. See [full startup evidence](p8-independent-initialization.md). No RF/voice performance or battery-current measurement was performed.

Track [Issue12](https://github.com/Econut/SignalDeck/issues/12) and [startup Issue3](https://github.com/Econut/SignalDeck/issues/3).
