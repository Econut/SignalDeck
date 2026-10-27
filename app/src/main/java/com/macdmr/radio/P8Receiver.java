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
import java.util.concurrent.atomic.AtomicInteger;

/** Single worker owns UARTs, tuning and audio. Default mode is the original receive-only controller.
 * Main radio may explicitly enable a separately build-gated, one-use analogue TX test.
 * No arbitrary command, module-upgrade, power-control or calibration API.
 */
public final class P8Receiver {
    public interface Listener {void status(String text);void closed(boolean safe,String reason);
        default void levels(double peak,double rms,long clips,long recent){} default void moduleApplied(int level){}
        default void tuned(P8RxProtocol.Channel channel){} default void receiving(boolean on){} default void transmitting(boolean on){} }
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private final Context context;private final Listener listener;
    private volatile boolean stop,scanning;private volatile int gain=1;
    private volatile int moduleLevel=1;private final AtomicInteger pendingVolume=new AtomicInteger(-1);
    private boolean mutedTestPlayback;
    private boolean txCapable,txPrepared;private volatile boolean pttRequested,txOwned,txEnding;private long txDeadline;
    private final P8TxPolicy txPolicy=new P8TxPolicy();private android.media.AudioRecord microphone;private final byte[] micBytes=new byte[160];private int micFill;
    public void enableTransmitTest(){txCapable=BuildConfig.P8_TX_TEST_REVIEWED;}
    public void armTransmitTest(){if(!txCapable)throw new IllegalStateException("RX-only controller");txPolicy.arm(SystemClock.elapsedRealtime());}
    public void cancelTransmitTest(){txPolicy.cancel();pttRequested=false;}
    public void ptt(boolean down){pttRequested=down;}
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
    public void gain(int n){P8AudioSettings.gain(n);gain=n;}
    public void moduleVolume(int n){P8AudioSettings.module(n);moduleLevel=n;pendingVolume.set(n);}
    public void muteTestPlayback(boolean mute){mutedTestPlayback=mute;}
    public void stop(){stop=true;}
    private void guard() throws Exception {
        if((context.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&ApplicationInfo.FLAG_STOPPED)==0)throw new Exception("Intercom is running/not force-stopped; close RX first");
        for(String path:new String[]{P8Protocol.PTT_NODE,P8Protocol.POWER_NODE,P8Protocol.AUDIO_CONTROL_NODE})
            if(readState(path)!=(path.equals(P8Protocol.PTT_NODE) && txOwned?0:1))throw new Exception("Unexpected radio GPIO state; no corrective write: "+path);
    }
    public static int readState(String path) throws Exception {
        try(FileInputStream f=new FileInputStream(path)){byte[] b=new byte[16];int n=f.read(b);return Integer.parseInt(new String(b,0,n,StandardCharsets.US_ASCII).trim());}
    }
    private void run(){
        AudioTrack audio=null;String reason="RX closed";boolean safe=true,nativeLoaded=false;
        P8RxProtocol.Scan scan=new P8RxProtocol.Scan();P8RxProtocol.Stream control=new P8RxProtocol.Stream(txCapable);PcmFramer pcm=new PcmFramer();P8AudioQueue speaker=new P8AudioQueue();
        P8AudioSettings.Meter meter=new P8AudioSettings.Meter();long previousClips=0;
        P8RxProtocol.Sink receiveStatus=f->{if(f.cmd==0x36 && f.body.length==1){int s=f.body[0]&255;if(s==3 || s==4){if(!txOwned && !txEnding)throw new IllegalArgumentException("Unexpected TX status; stop");if(s==4)txEnding=false;listener.transmitting(s==3);}else if(s==1 || s==10 || s==12){scan.signal(SystemClock.elapsedRealtime());listener.receiving(true);}else if(s==2 || s==11 || s==13)listener.receiving(false);}};
        try{
            guard();P8ReceiveNative.load();nativeLoaded=true;P8ReceiveNative.open();
            int size=Math.max(3200,AudioTrack.getMinBufferSize(8000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build();
            if(audio.getState()!=AudioTrack.STATE_INITIALIZED)throw new Exception("AudioTrack initialisation failed");
            audio.setVolume(mutedTestPlayback?0:1);audio.play();final AudioTrack player=audio;long nextStatus=0;
            P8RxProtocol.Channel tunedChannel=null;
            while(!stop){
                if(txOwned && (!pttRequested || SystemClock.elapsedRealtime()>=txDeadline)){endTransmit();audio.play();}
                guard();
                if(pttRequested && !txOwned){
                    if(tunedChannel!=null && txPolicy.consume(SystemClock.elapsedRealtime(),tunedChannel.hz,tunedChannel.command())){
                        if(!txPrepared){P8ReceiveNative.prepareTransmit();txPrepared=true;}
                        int buffer=Math.max(3200,android.media.AudioRecord.getMinBufferSize(8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
                        microphone=new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC,8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
                        if(microphone.getState()!=android.media.AudioRecord.STATE_INITIALIZED)throw new Exception("Microphone initialisation failed");
                        microphone.startRecording();if(microphone.getRecordingState()!=android.media.AudioRecord.RECORDSTATE_RECORDING)throw new Exception("Microphone capture failed");
                        if(!pttRequested){microphone.stop();microphone.release();microphone=null;continue;}
                        audio.pause();audio.flush();txOwned=true;txEnding=true;txDeadline=SystemClock.elapsedRealtime()+P8TxPolicy.MAX_KEY_MS;
                        P8ReceiveNative.key(true);listener.transmitting(true);listener.status("TX TEST keyed; native watchdog maximum 2 seconds");
                    }else {pttRequested=false;listener.status("TX locked: arm one Channel 9 analogue test first");}
                }
                if(txOwned){control.feed(P8ReceiveNative.readControl(),receiveStatus);int n=microphone.read(micBytes,micFill,160-micFill,android.media.AudioRecord.READ_NON_BLOCKING);if(n<0)throw new Exception("Microphone read failure");micFill+=n;if(micFill==160){P8ReceiveNative.writeMicrophone(micBytes,160);micFill=0;}SystemClock.sleep(2);continue;}
                P8RxProtocol.Channel ch=requested.getAndSet(null);
                if(ch!=null){byte[] reply=P8ReceiveNative.tune(ch.packet(moduleLevel));P8RxProtocol.requireAck(control,reply,receiveStatus,ch.command());current=ch.toString();tunes++;
                    int index=0;for(int i=0;i<channels.length;i++)if(channels[i]==ch)index=i;
                    tunedChannel=ch;scan.tuned(index,SystemClock.elapsedRealtime());listener.tuned(ch);listener.status("TUNED (ACK) · "+current);}
                int level=pendingVolume.getAndSet(-1);
                if(level>=0){byte[] reply=P8ReceiveNative.volume(level);P8RxProtocol.requireAck(control,reply,receiveStatus,0x2e);listener.moduleApplied(level);listener.status("Module receive level ACK: "+level);}
                control.feed(P8ReceiveNative.readControl(),receiveStatus);
                byte[] data=P8ReceiveNative.readPcm();bytes+=data.length;
                pcm.feed(data,data.length,(b,o,n)->{
                    frames++;long magnitude=0;for(int i=o;i<o+n;i+=2)magnitude+=Math.abs((int)(short)((b[i]&255)|(b[i+1]<<8)));
                    if(magnitude/(n/2)>300)scan.signal(SystemClock.elapsedRealtime());
                    int appliedGain=gain;meter.feed(b,o,n,appliedGain);clipped+=P8RxProtocol.amplify(b,o,n,appliedGain);
                    speaker.add(b,o,n);
                });
                speaker.drain((b,o,n)->player.write(b,o,n,AudioTrack.WRITE_NON_BLOCKING));
                long now=SystemClock.elapsedRealtime();
                if(scanning){int i=scan.advance(now,channels.length);if(i>=0)requested.compareAndSet(null,channels[i]);}
                if(now>=nextStatus){listener.status((scanning?"SCANNING · ":"RX · ")+current+"\nPCM bytes "+bytes+" · frames "+frames+" · clipped "+clipped+" · queued audio bytes "+speaker.pending()+" · gain ×"+gain+" · module "+moduleLevel+" · tunes "+tunes);
                    listener.levels(meter.peakDb(),meter.rmsDb(),meter.clips(),meter.clips()-previousClips);previousClips=meter.clips();meter.resetWindow();nextStatus=now+500;}
            }
        }catch(Exception | LinkageError e){reason="STOP: "+e.getMessage();if(reason.contains("restoration") || reason.contains("close error") || reason.contains("GPIO state") || reason.contains("TX status"))safe=false;}
        finally{
            txPolicy.cancel();if(nativeLoaded && txCapable)try{endTransmit();}catch(Exception | LinkageError e){safe=false;reason+="; PTT release failure: "+e.getMessage();}
            if(audio!=null){try{audio.pause();audio.flush();audio.release();}catch(Exception ignored){}}
            if(nativeLoaded)try{P8ReceiveNative.close();}catch(Exception | LinkageError e){safe=false;reason+="; close/restore failed: "+e.getMessage();}
            ACTIVE.set(false);listener.closed(safe,reason+"; PCM bytes="+bytes+", frames="+frames+", clips="+clipped+", pendingAudioAtClose="+speaker.pending()+", tunes="+tunes);
        }
    }
    private void endTransmit() throws Exception {
        boolean released=false;
        try{P8ReceiveNative.key(false);txOwned=false;released=true;}finally{
            pttRequested=false;if(microphone!=null){try{microphone.stop();}finally{microphone.release();microphone=null;}}micFill=0;if(released)listener.transmitting(false);
        }
    }
}
