package com.macdmr.radio;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8ScanTimingTest {
    @Test public void fmSpeedsAreBoundedAndInvalidSavedValuesFallBack(){for(int n:new int[]{800,1000,1500})assertEquals(n,P8ScanTiming.dwell(n,false));for(int n:new int[]{0,-1,100,700,999,5000})assertEquals(1500,P8ScanTiming.saved(n));}
    @Test public void digitalRetainsConservativeDwell(){for(int n:P8ScanTiming.OPTIONS)assertEquals(1500,P8ScanTiming.dwell(n,true));}
    @Test public void experimentalSettingIsLabelled(){assertTrue(P8ScanTiming.label(800).contains("trial"));assertTrue(P8ScanTiming.label(1000).contains("1.0"));}
}
