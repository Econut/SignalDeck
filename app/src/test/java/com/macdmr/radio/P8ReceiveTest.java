package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class P8ReceiveTest {
    @Test public void safeTuneHasNoTonesAndStockMonitorOrder(){
        byte[] b=new P8RxProtocol.Channel("RX",446006250,0,1,1,1).packet();
        assertEquals(28,b.length);assertEquals(0x23,b[1]);assertArrayEquals(Arrays.copyOfRange(b,8,12),Arrays.copyOfRange(b,12,16));
        assertEquals(0,b[17]);for(int i=19;i<=22;i++)assertEquals(0,b[i]);assertEquals(1,b[25]);assertEquals(1,b[26]);
    }
    @Test public void outOfBandAndInvalidSettingsCannotTune(){
        for(int hz:new int[]{0,135999999,174000001,399999999,480000001})try{new P8RxProtocol.Channel("x",hz,0,1,2,1);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void fragmentedAckDecodedExactlyOnce(){
        byte[] ack=new Frame(0x23,0,0,new byte[0]).encode();
        for(int cut=0;cut<=ack.length;cut++){P8RxProtocol.Stream s=new P8RxProtocol.Stream();int[] count={0};
            s.feed(Arrays.copyOfRange(ack,0,cut),f->count[0]++);s.feed(Arrays.copyOfRange(ack,cut,ack.length),f->count[0]++);assertEquals(1,count[0]);}
        P8RxProtocol.requireTuneAck(ack);
    }
    @Test public void rejectBadAckAndTxStatus(){
        for(byte[] b:new byte[][]{new byte[0],new Frame(0x23,0,1,new byte[0]).encode(),new Frame(0x36,2,0,new byte[]{3}).encode()})
            try{P8RxProtocol.requireTuneAck(b);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void scanDwellsHoldsAndWraps(){P8RxProtocol.Scan s=new P8RxProtocol.Scan();s.tuned(0,0);assertEquals(-1,s.advance(1199,2));assertEquals(1,s.advance(1200,2));s.signal(1300);assertEquals(-1,s.advance(4200,2));assertEquals(0,s.advance(4300,2));assertEquals(-1,s.advance(9999,1));}
    @Test public void gainSaturatesInsteadOfWrapping(){byte[] b={(byte)0xff,0x7f,0,(byte)0x80,1,0};assertEquals(2,P8RxProtocol.amplify(b,0,6,4));assertArrayEquals(new byte[]{(byte)0xff,0x7f,0,(byte)0x80,4,0},b);}
    @Test public void speakerQueueRetainsPartialAndBlockedWrites(){P8AudioQueue q=new P8AudioQueue();q.add(new byte[]{1,2,3,4},0,4);int[] calls={0};java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        q.drain((b,o,n)->{if(calls[0]++==0){out.write(b,o,2);return 2;}return 0;});assertEquals(2,q.pending());
        q.drain((b,o,n)->{out.write(b,o,n);return n;});assertEquals(0,q.pending());assertArrayEquals(new byte[]{1,2,3,4},out.toByteArray());}
    @Test public void speakerBacklogStopsInsteadOfDroppingSilently(){P8AudioQueue q=new P8AudioQueue();q.add(new byte[32000],0,32000);try{q.add(new byte[1],0,1);fail();}catch(IllegalStateException expected){}assertEquals(32000,q.pending());}
    @Test public void statusFragmentAfterTuneAckSurvivesIntoNextRead(){byte[] ack=new Frame(0x23,0,0,new byte[0]).encode();byte[] status=new Frame(0x36,2,0,new byte[]{1}).encode();
        for(int cut=1;cut<status.length;cut++){P8RxProtocol.Stream s=new P8RxProtocol.Stream();byte[] first=Arrays.copyOf(ack,ack.length+cut);System.arraycopy(status,0,first,ack.length,cut);int[] received={0};
            P8RxProtocol.requireTuneAck(s,first,f->received[0]++);s.feed(Arrays.copyOfRange(status,cut,status.length),f->received[0]++);assertEquals(1,received[0]);}}
}
