"""HackRF test signal for the W6: FM, a 1 kHz tone at +-2.5 kHz plus 62.5 Hz CTCSS at +-0.5 kHz, at +200 kHz from the
HackRF's centre (tune the HackRF 200 kHz below the channel). Signed 8-bit IQ at 2 MS/s.
The whole signal repeats every 16 ms (32000 samples: 3200 cycles of the offset, 16 of the tone, 1 of the CTCSS), so one
period is computed and written repeatedly. Plain Python, no numpy.
usage: python fm_tone_iq.py out.iq seconds [ctcss_hz (0 = none, must divide 1000 evenly)]"""
import math
import sys

out, secs = sys.argv[1], float(sys.argv[2])
ctcss = float(sys.argv[3]) if len(sys.argv) > 3 else 62.5
fs, offset, tone = 2_000_000, 200_000.0, 1000.0
dev_tone, dev_ctcss, amp = 2500.0, 500.0, 0.7 * 127
period = 32000                                  # 16 ms
buf = bytearray(2 * period)
phase = 0.0
for n in range(period):
    t = n / fs
    inst = offset + dev_tone * math.sin(2 * math.pi * tone * t) + (dev_ctcss * math.sin(2 * math.pi * ctcss * t) if ctcss else 0.0)
    phase += 2 * math.pi * inst / fs
    buf[2 * n] = int(round(amp * math.cos(phase))) & 0xFF
    buf[2 * n + 1] = int(round(amp * math.sin(phase))) & 0xFF
reps = max(1, int(round(secs * fs / period)))
with open(out, 'wb') as f:
    for _ in range(reps):
        f.write(buf)
print("wrote", out, reps * period, "samples, closing phase error", round(math.remainder(phase, 2 * math.pi), 4), "rad")
