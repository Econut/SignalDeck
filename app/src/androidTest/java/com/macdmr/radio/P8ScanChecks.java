/* SignalDeck - GPL-3.0-or-later. Main-looper scan simulation; no radio transport. */
package com.macdmr.radio;
import android.app.Instrumentation;
import android.os.SystemClock;
import java.util.*;
final class P8ScanChecks {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static final class Host implements Scanner.Host {
        volatile boolean rx;volatile int tunes,number;final List<ChannelStore.Channel> channels=new ArrayList<>();
        Host(){for(int i=1;i<=3;i++){ChannelStore.Channel c=new ChannelStore.Channel();c.number=i;c.type=1;channels.add(c);}}
        public List<ChannelStore.Channel> candidates(){return channels;}
        public void tune(ChannelStore.Channel c){number=c.number;tunes++;}
        public boolean receiving(){return rx;}public boolean softGated(){return false;}
        public ChannelStore.Channel priority(){return null;}public long dwellMs(){return 800;}
        public boolean waitForTuneAck(){return true;}
        public void status(boolean scanning,boolean holding,ChannelStore.Channel on){}
    }
    static void run(Instrumentation test){
        Host host=new Host();Scanner[] scanner=new Scanner[1];test.runOnMainSync(()->{scanner[0]=new Scanner(host);check(scanner[0].start()==null,"Fake scan did not start");});
        try{
            SystemClock.sleep(1100);check(host.tunes==1,"Advanced before tune ACK");host.rx=true;
            test.runOnMainSync(()->scanner[0].acknowledged(99));SystemClock.sleep(350);check(host.tunes==1 && !scanner[0].isHolding(),"Wrong ACK released pending channel");
            test.runOnMainSync(()->scanner[0].acknowledged(1));SystemClock.sleep(350);check(!scanner[0].isHolding(),"Settling guard was bypassed");
            SystemClock.sleep(800);check(scanner[0].isHolding() && host.tunes==1,"Fast scan missed sustained receive activity");
            host.rx=false;SystemClock.sleep(2800);check(host.tunes==2 && !scanner[0].isHolding(),"Scan did not resume after receive hang");
            SystemClock.sleep(1000);check(host.tunes==2,"Second channel advanced without ACK");
            test.runOnMainSync(()->{scanner[0].stop();scanner[0].acknowledged(2);});SystemClock.sleep(200);check(host.tunes==2 && !scanner[0].isScanning(),"Late ACK restarted stopped scan");
        }finally{test.runOnMainSync(()->scanner[0].stop());}
    }
}
