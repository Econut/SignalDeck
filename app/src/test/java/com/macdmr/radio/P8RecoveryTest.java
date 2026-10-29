package com.macdmr.radio;
import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class P8RecoveryTest {
    static class Fake implements P8Recovery.Backend {
        boolean active,stopped=true,failRelease;int ptt=0,power=1,audio=1,writes;
        public boolean active(){return active;}public boolean stockStopped(){return stopped;}
        public void release() throws Exception{writes++;if(failRelease)throw new IOException("failed");ptt=1;}
        public int state(String path){return path.equals(P8Protocol.PTT_NODE)?ptt:path.equals(P8Protocol.POWER_NODE)?power:audio;}
    }
    private void blocked(Fake b,boolean restore) throws Exception{try{P8Recovery.recover(b,restore);fail("Expected recovery refusal");}catch(IOException expected){}}
    @Test public void releasesOnceThenVerifies() throws Exception{Fake b=new Fake();P8Recovery.recover(b,false);assertEquals(1,b.writes);assertEquals(1,b.ptt);assertEquals(1,b.power);assertEquals(1,b.audio);}
    @Test public void activeSessionCannotBeTouched() throws Exception{Fake b=new Fake();b.active=true;blocked(b,false);assertEquals(0,b.writes);}
    @Test public void restorationFailureCannotBeCleared() throws Exception{Fake b=new Fake();blocked(b,true);assertEquals(0,b.writes);}
    @Test public void stockOwnershipCannotBeStolen() throws Exception{Fake b=new Fake();b.stopped=false;blocked(b,false);assertEquals(0,b.writes);}
    @Test public void powerAndAudioAreNeverCorrected() throws Exception{for(int i=0;i<2;i++){Fake b=new Fake();if(i==0)b.power=0;else b.audio=0;blocked(b,false);assertEquals(0,b.writes);}}
    @Test public void releaseFailurePropagatesWithoutRetry() throws Exception{Fake b=new Fake();b.failRelease=true;blocked(b,false);assertEquals(1,b.writes);assertEquals(0,b.ptt);}
    @Test public void failedReadbackDoesNotSucceed() throws Exception{Fake b=new Fake(){public void release(){writes++;}};blocked(b,false);assertEquals(1,b.writes);}
    @Test public void journalSurvivesReopenAndRotates() throws Exception{
        File dir=Files.createTempDirectory("p8-journal-").toFile();
        try{P8RecoveryJournal.append(dir,"STOP failure\ncontinued");assertTrue(P8RecoveryJournal.tail(dir).contains("STOP failure continued"));
            try(RandomAccessFile out=new RandomAccessFile(new File(dir,"radio.log"),"rw")){out.setLength(P8RecoveryJournal.LIMIT);}
            P8RecoveryJournal.append(dir,"RECOVERY complete");assertTrue(new File(dir,"radio-previous.log").exists());assertTrue(P8RecoveryJournal.tail(dir).contains("RECOVERY complete"));assertFalse(P8RecoveryJournal.tail(dir).contains("STOP failure"));
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
    }
}
