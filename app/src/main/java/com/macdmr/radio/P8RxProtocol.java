/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 receive-only adapter. See COPYRIGHT. */
package com.macdmr.radio;
import java.util.Arrays;
public final class P8RxProtocol {
    private P8RxProtocol() { }
    public static final class Channel {
        public final String name;public final int hz,bandwidth,squelch,monitor,volume;
        public int txHz,power,rxToneType,rxToneIndex,txToneType,txToneIndex;public boolean txAllowed;
        private Frame digital;
        public static Channel digital(String name,int hz,int localId,int[] groups,int target,int callType,int cc,int inSlot,int outSlot,int volume){Channel ch=new Channel(name,hz,0,1,2,volume);ch.digital=P8DigitalProtocol.receive(hz,localId,groups,target,callType,cc,inSlot,outSlot,volume);return ch;}
        public int command(){return digital==null?0x23:0x22;}
        public Channel(String name,int hz,int bandwidth,int squelch,int monitor,int volume) {
            if(!((hz>=136000000 && hz<=174000000)||(hz>=400000000 && hz<=480000000)))throw new IllegalArgumentException("RX frequency must be 136–174 or 400–480 MHz");
            if(bandwidth<0 || bandwidth>1 || squelch<0 || squelch>9 || monitor<1 || monitor>2 || volume<1 || volume>9)throw new IllegalArgumentException("Invalid P8 wire settings");
            this.name=name==null?"RX":name.trim();this.hz=hz;this.txHz=hz;this.bandwidth=bandwidth;this.squelch=squelch;this.monitor=monitor;this.volume=volume;
        }
        public byte[] packet() {return packet(volume);}
        public byte[] packet(int moduleLevel) {P8AudioSettings.module(moduleLevel);if(digital!=null){byte[] body=digital.body.clone();body[160]=(byte)moduleLevel;return Frame.request(0x22,body).encode();}return P8Protocol.analog(hz,txHz,bandwidth,power,squelch,rxToneType,rxToneIndex,txToneType,txToneIndex,2,moduleLevel,monitor).encode();}
        @Override public String toString(){return name+" · "+String.format(java.util.Locale.US,"%.5f MHz",hz/1e6);}
    }
    public interface Sink {void frame(Frame f);}
    /** Bounded fragmented control stream. No automatic ACK, retries or outbound side effects. */
    public static final class Stream {
        private byte[] pending=new byte[0];
        private final boolean txSession;
        public Stream(){this(false);}
        public Stream(boolean txSession){this.txSession=txSession;}
        public void feed(byte[] data,Sink sink) {
            if(pending.length+data.length>4096)throw new IllegalArgumentException("Control stream overflow");
            byte[] all=Arrays.copyOf(pending,pending.length+data.length);System.arraycopy(data,0,all,pending.length,data.length);
            int off=0;
            while(off<all.length){
                Frame[] f=new Frame[1];int n=Frame.tryDecode(all,off,all.length-off,f);
                if(n==0)break;
                if(n<0)throw new IllegalArgumentException("Invalid control checksum/framing");
                if(!txSession && f[0].cmd==0x36 && f[0].body.length==1 && (f[0].body[0]==3 || f[0].body[0]==4))throw new IllegalArgumentException("Unexpected TX status; stop");
                sink.frame(f[0]);off+=n;
            }
            pending=Arrays.copyOfRange(all,off,all.length);
        }
    }
    public static void requireTuneAck(byte[] data) {
        requireTuneAck(new Stream(),data,f->{});
    }
    public static void requireTuneAck(Stream stream,byte[] data,Sink unsolicited) {
        requireAck(stream,data,unsolicited,0x23);
    }
    public static void requireAck(Stream stream,byte[] data,Sink unsolicited,int command) {
        final boolean[] ack={false};
        stream.feed(data,f->{if(f.cmd==command){if(f.rw!=0 || f.sr!=0)throw new IllegalArgumentException("Command rejected by module");ack[0]=true;}else unsolicited.frame(f);});
        if(!ack[0])throw new IllegalArgumentException("No successful acknowledgement for command "+Integer.toHexString(command));
    }
    /** Gain modifies local speaker PCM only, never a module register. */
    public static int amplify(byte[] bytes,int off,int len,int gain) {
        if(gain<1 || gain>P8AudioSettings.DSP_GAIN_MAX || (len&1)!=0)throw new IllegalArgumentException("PCM gain/alignment");
        int clips=0;
        for(int i=off;i<off+len;i+=2){int s=(short)((bytes[i]&255)|(bytes[i+1]<<8));int v=s*gain;
            if(v>32767){v=32767;clips++;}if(v< -32768){v=-32768;clips++;}bytes[i]=(byte)v;bytes[i+1]=(byte)(v>>8);}
        return clips;
    }
    public static final class Scan {
        private int index;private long next;
        public void tuned(int index,long now){this.index=index;next=now+1200;}
        public void signal(long now){next=Math.max(next,now+3000);}
        public int advance(long now,int size){if(size<2 || now<next)return -1;index=(index+1)%size;next=now+1200;return index;}
    }
}
