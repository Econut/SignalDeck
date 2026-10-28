package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8AnaloguePolicyTest {
    @Test public void timeoutsAreBoundedAndDiscrete(){for(int ms:new int[]{30000,60000,120000,180000})P8AnaloguePolicy.timeout(ms);for(int ms:new int[]{-1,0,10000,31000,180001,Integer.MAX_VALUE}){try{P8AnaloguePolicy.timeout(ms);fail();}catch(IllegalArgumentException expected){}}}
    @Test public void repeatedOrTimedOutPressRequiresRelease(){P8PttEdge edge=new P8PttEdge();assertTrue(edge.press());for(int i=0;i<20;i++)assertFalse(edge.press());edge.release();assertTrue(edge.press());edge.release();edge.release();assertTrue(edge.press());}
    @Test public void toneBoundariesAndNoToneAreStrict(){P8AnaloguePolicy.tone(0,0);P8AnaloguePolicy.tone(1,50);P8AnaloguePolicy.tone(2,82);P8AnaloguePolicy.tone(3,82);for(int[] pair:new int[][]{{0,1},{1,51},{2,83},{3,83},{4,0},{-1,0},{1,-1}}){try{P8AnaloguePolicy.tone(pair[0],pair[1]);fail();}catch(IllegalArgumentException expected){}}}
    @Test public void crossBandSplitAndPowerValidate(){P8AnaloguePolicy.validate(145500000,438500000,1,1,13,2,0);for(int[] pair:new int[][]{{135999999,446106250},{446106250,480000001}}){try{P8AnaloguePolicy.validate(pair[0],pair[1],0,0,0,0,0);fail();}catch(IllegalArgumentException expected){}}}
    @Test public void speakerBufferDoesNotReplayPreTxAudio(){P8AudioQueue queue=new P8AudioQueue();queue.add(new byte[]{1,2,3,4},0,4);queue.drain((b,o,n)->2);queue.clear();assertEquals(0,queue.pending());queue.drain((b,o,n)->{fail("Old receive audio was retained");return 0;});queue.add(new byte[]{5,6},0,2);queue.drain((b,o,n)->{assertEquals(5,b[o]);return n;});}
}
