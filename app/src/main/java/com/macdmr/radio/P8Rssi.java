/* SignalDeck - GPL-3.0-or-later. Modified P8 receive telemetry. */
package com.macdmr.radio;
/** Existing family read-RSSI command; P8 support is established by a bounded first reply. */
public final class P8Rssi {
    public static final double UNKNOWN=-999;
    private P8Rssi(){}
    public static byte[] query(){return Frame.request(0x32,(byte)1).encode();}
    public static double dbm(int raw){return raw>0 && raw<=200?-120+raw/2.0:UNKNOWN;}
    public static final class Poll {
        private boolean waiting,disabled,supported;
        private long due,deadline,lastReading=-1;
        public void tuned(long now){waiting=false;lastReading=-1;due=now+800;}
        public void pause(long now){waiting=false;lastReading=-1;due=now+800;}
        /** Initial probe only while receiving. No retry after a rejected, invalid or unanswered probe. */
        public boolean queryDue(long now,boolean receiving,boolean busy){
            if(disabled || waiting || busy || now<due || (!supported && !receiving))return false;
            waiting=true;deadline=now+1500;due=now+1000;return true;
        }
        public boolean timedOut(long now){if(waiting && now>=deadline){waiting=false;disabled=true;return true;}return false;}
        public boolean waiting(){return waiting;}
        public boolean disabled(){return disabled;}
        public double reply(Frame frame,long now){
            if(!waiting || disabled)return UNKNOWN;
            if(now>=deadline){waiting=false;disabled=true;lastReading=-1;return UNKNOWN;}
            waiting=false;
            if(frame.cmd!=0x32 || frame.rw!=0 || frame.sr!=0 || frame.body.length!=1){disabled=true;lastReading=-1;return UNKNOWN;}
            double value=dbm(frame.body[0]&255);
            if(value==UNKNOWN){if(!supported)disabled=true;lastReading=-1;return UNKNOWN;}
            supported=true;lastReading=now;return value;
        }
        public boolean stale(long now){if(lastReading>=0 && now-lastReading>=3000){lastReading=-1;return true;}return false;}
    }
}
