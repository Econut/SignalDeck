/* SignalDeck - Copyright (C) 2026 IIMacGyverII. SPDX-License-Identifier: GPL-3.0-or-later.
 * Modified P8 development utility, 2026-10-27. See COPYRIGHT. */
import com.macdmr.radio.Frame;
import com.macdmr.radio.P8Protocol;
import com.macdmr.radio.PcmFramer;
import java.nio.file.*;
import java.nio.*;
import java.io.*;

/** Host-only saved-data utility. No serial transport. Run tools/p8-offline.ps1. */
public class P8Offline {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("decode")) {
            System.out.println(P8Protocol.describe(P8Protocol.decodeHex(args[1]))); return;
        }
        if (args.length == 2 && args[0].equals("analog")) {
            int hz = Integer.parseInt(args[1]);
            Frame f = P8Protocol.analog(hz,hz,0,0,1,0,0,0,0,2,1,2);
            byte[] b = f.encode(); System.out.println("OFFLINE ONLY: " + Frame.hex(b,b.length)); return;
        }
        if ((args.length == 3 || args.length == 4) && args[0].equals("pcm-wav")) {
            Path input = Paths.get(args[1]), output = Paths.get(args[2]);
            if (!Files.isRegularFile(input) || Files.size(input)>64*1024*1024) throw new IllegalArgumentException("use a saved PCM capture file <=64 MiB");
            byte[] raw=Files.readAllBytes(input); PcmFramer framer=new PcmFramer();
            ByteArrayOutputStream pcm=new ByteArrayOutputStream();framer.feed(raw,raw.length,(b,o,n)->pcm.write(b,o,n));
            if(framer.frames==0) throw new IllegalArgumentException("no complete PCM packets; no WAV written");
            byte[] samples=pcm.toByteArray();
            double db=args.length==4 ? Double.parseDouble(args[3]) : 0;
            if(!Double.isFinite(db) || db < -18 || db > 18) throw new IllegalArgumentException("offline gain must be -18..18 dB");
            double gain=Math.pow(10,db/20), squares=0;int peak=0,clipped=0;
            for(int n=0;n<samples.length;n+=2){
                int original=(short)((samples[n]&255)|((samples[n+1]&255)<<8));
                squares+=(double)original*original;peak=Math.max(peak,Math.abs(original));
                long scaled=Math.round(original*gain);if(scaled>32767 || scaled< -32768)clipped++;
                int s=(int)Math.max(-32768,Math.min(32767,scaled));samples[n]=(byte)s;samples[n+1]=(byte)(s>>8);
            }
            double rms=Math.sqrt(squares/(samples.length/2));
            ByteBuffer h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes("US-ASCII")).putInt(36+samples.length).put("WAVEfmt ".getBytes("US-ASCII"));
            h.putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16);
            h.put("data".getBytes("US-ASCII")).putInt(samples.length);
            try(OutputStream out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){out.write(h.array());out.write(samples);}
            System.out.println(framer.frames+" PCM frames, "+framer.skipped+" bytes skipped; source peak="+peak+", RMS="+rms+"; offline gain="+db+" dB, clipped="+clipped+" samples; 8 kHz mono WAV: "+output);return;
        }
        throw new IllegalArgumentException("decode HEX | analog HZ | pcm-wav SAVED_CAPTURE NEW_WAV [GAIN_DB]");
    }
}
