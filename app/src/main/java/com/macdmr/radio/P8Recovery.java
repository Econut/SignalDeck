package com.macdmr.radio;
import java.io.IOException;
/** Explicit user recovery; never clears a fault until closed ownership and released GPIO are verified. */
public final class P8Recovery {
    private P8Recovery(){}
    public interface Backend {boolean active(); boolean stockStopped() throws Exception; void release() throws Exception; int state(String path) throws Exception;}
    public static void recover(Backend b,boolean restorationFailed) throws Exception {
        if(b.active())throw new IOException("Radio session has not closed; recovery stopped");
        if(restorationFailed)throw new IOException("UART restoration failed; restart Android before retrying recovery");
        if(!b.stockStopped())throw new IOException("Intercom is running. Force stop it in App info, then choose Recover radio connection again");
        if(b.state(P8Protocol.POWER_NODE)!=1 || b.state(P8Protocol.AUDIO_CONTROL_NODE)!=1)throw new IOException("Power/audio state is unexpected; recovery did not change it");
        b.release();
        if(b.state(P8Protocol.PTT_NODE)!=1)throw new IOException("PTT release could not be verified");
        if(b.state(P8Protocol.POWER_NODE)!=1 || b.state(P8Protocol.AUDIO_CONTROL_NODE)!=1)throw new IOException("Power/audio state changed during recovery");
    }
}
