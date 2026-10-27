/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 receive-only controller. See COPYRIGHT. */
package com.macdmr.radio;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Foreground-only RX controller. One worker owns native descriptors, tuning, scan and speaker playback.
 * No microphone, PTT/GPIO-write, transmit, arbitrary command or module-upgrade API.
 */
public final class P8Receiver {
    public interface Listener {void status(String text);void closed(boolean safe,String reason);}
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private final Context context;private final Listener listener;
    private volatile boolean stop,scanning;private volatile int gain=1;
    private final AtomicReference<P8RxProtocol.Channel> requested=new AtomicReference<>();private volatile P8RxProtocol.Channel[] channels;
    private long frames,bytes,clipped,tunes;private String current="";
    public P8Receiver(Context c,Listener l){context=c.getApplicationContext();listener=l;}
    public static boolean active(){return ACTIVE.get();}
    public void start(P8RxProtocol.Channel[] list,int index,P8RxProtocol.Channel initial){
        if(list.length==0 || index<0 || index>=list.length)throw new IllegalArgumentException("Select an RX channel");
        if(!ACTIVE.compareAndSet(false,true))throw new IllegalStateException("An RX session is already active");
        channels=list.clone();requested.set(initial);stop=false;new Thread(this::run,"P8-receive-only").start();
    }
    public void tune(P8RxProtocol.Channel ch){scanning=false;requested.set(ch);}
    public void scan(boolean enabled){scanning=enabled;}
    public void gain(int n){if(n<1 || n>4)throw new IllegalArgumentException("gain");gain=n;}
    public void stop(){stop=true;}
    private void guard() throws Exception {
        if((context.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&ApplicationInfo.FLAG_STOPPED)==0)throw new Exception("Intercom is running/not force-stopped; close RX first");
        for(String path:new String[]{P8Protocol.PTT_NODE,P8Protocol.POWER_NODE,P8Protocol.AUDIO_CONTROL_NODE})
            if(readState(path)!=1)throw new Exception("Unexpected radio GPIO state; no corrective write: "+path);
    }
    public static int readState(String path) throws Exception {
        try(FileInputStream f=new FileInputStream(path)){byte[] b=new byte[16];int n=f.read(b);return Integer.parseInt(new String(b,0,n,StandardCharsets.US_ASCII).trim());}
    }
    private void run(){
        AudioTrack audio=null;String reason="RX closed";boolean safe=true;
        P8RxProtocol.Scan scan=new P8RxProtocol.Scan();P8RxProtocol.Stream control=new P8RxProtocol.Stream();PcmFramer pcm=new PcmFramer();P8AudioQueue speaker=new P8AudioQueue();
        P8RxProtocol.Sink receiveStatus=f->{if(f.cmd==0x36 && f.body.length==1 && (f.body[0]==1 || f.body[0]==10 || f.body[0]==12))scan.signal(SystemClock.elapsedRealtime());};
        try{
            guard();P8ReceiveNative.load();P8ReceiveNative.open();
            int size=Math.max(3200,AudioTrack.getMinBufferSize(8000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build();
            if(audio.getState()!=AudioTrack.STATE_INITIALIZED)throw new Exception("AudioTrack initialisation failed");
            audio.play();final AudioTrack player=audio;long nextStatus=0;
            while(!stop){
                guard();
                P8RxProtocol.Channel ch=requested.getAndSet(null);
                if(ch!=null){byte[] reply=P8ReceiveNative.tune(ch.packet());P8RxProtocol.requireTuneAck(control,reply,receiveStatus);current=ch.toString();tunes++;
                    int index=0;for(int i=0;i<channels.length;i++)if(channels[i]==ch)index=i;
                    scan.tuned(index,SystemClock.elapsedRealtime());listener.status("TUNED (ACK) · "+current);}
                control.feed(P8ReceiveNative.readControl(),receiveStatus);
                byte[] data=P8ReceiveNative.readPcm();bytes+=data.length;
                pcm.feed(data,data.length,(b,o,n)->{
                    frames++;long magnitude=0;for(int i=o;i<o+n;i+=2)magnitude+=Math.abs((int)(short)((b[i]&255)|(b[i+1]<<8)));
                    if(magnitude/(n/2)>300)scan.signal(SystemClock.elapsedRealtime());
                    clipped+=P8RxProtocol.amplify(b,o,n,gain);
                    speaker.add(b,o,n);
                });
                speaker.drain((b,o,n)->player.write(b,o,n,AudioTrack.WRITE_NON_BLOCKING));
                long now=SystemClock.elapsedRealtime();
                if(scanning){int i=scan.advance(now,channels.length);if(i>=0)requested.compareAndSet(null,channels[i]);}
                if(now>=nextStatus){listener.status((scanning?"SCANNING · ":"RX · ")+current+"\nPCM bytes "+bytes+" · frames "+frames+" · clipped "+clipped+" · queued audio bytes "+speaker.pending()+" · gain ×"+gain+" · tunes "+tunes);nextStatus=now+500;}
            }
        }catch(Exception | LinkageError e){reason="STOP: "+e.getMessage();if(reason.contains("restoration") || reason.contains("close error"))safe=false;}
        finally{
            if(audio!=null){try{audio.pause();audio.flush();audio.release();}catch(Exception ignored){}}
            try{P8ReceiveNative.close();}catch(Exception | LinkageError e){safe=false;reason+="; close/restore failed: "+e.getMessage();}
            ACTIVE.set(false);listener.closed(safe,reason+"; PCM bytes="+bytes+", frames="+frames+", clips="+clipped+", pendingAudioAtClose="+speaker.pending()+", tunes="+tunes);
        }
    }
}
