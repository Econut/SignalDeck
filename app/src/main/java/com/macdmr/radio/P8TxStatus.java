/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 status guard. */
package com.macdmr.radio;
/** Status notifications never grant permission to key. Delayed replies are bounded to our own release. */
public final class P8TxStatus {
    private boolean owned;private long released=-1;
    public void keyed(long now){owned=true;released=-1;}
    public void released(long now){if(owned){owned=false;released=now;}}
    private boolean recentRelease(long now){return released>=0 && now>=released && now-released<=1000;}
    public boolean status(int code,long now){
        if(code==3){if(owned)return true;if(recentRelease(now))return false;throw new IllegalArgumentException("Unexpected TX start status; stop");}
        if(code==4){if(!owned && !recentRelease(now))throw new IllegalArgumentException("Unexpected TX end status; stop");return false;}
        throw new IllegalArgumentException("Not a TX status");
    }
    public static byte[] acknowledgement(){return Frame.request(0x36,(byte)1).encode();}
}
