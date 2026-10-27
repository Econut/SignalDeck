/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 audio controls. */
package com.macdmr.radio;
/** Explicit policy limits, separate from DSP capability. Wider gain needs a later reviewed policy change. */
public final class P8AudioSettings {
    public static final int MODULE_MIN=1,MODULE_MAX=9,INITIAL_GAIN_MAX=4,DSP_GAIN_MAX=16;
    private P8AudioSettings(){}
    public static void module(int n){if(n<MODULE_MIN || n>MODULE_MAX)throw new IllegalArgumentException("Module level outside development range 1–9");}
    public static void gain(int n){if(n<1 || n>INITIAL_GAIN_MAX)throw new IllegalArgumentException("Software gain currently limited to ×4");}
    public static final class Meter {
        private long count;private double sumSquares;private int peak;private long clips,lastClips;
        public void feed(byte[] b,int off,int n,int gain){
            if(gain<1 || gain>DSP_GAIN_MAX || n%2!=0)throw new IllegalArgumentException("DSP range/alignment");
            lastClips=0;
            for(int i=off;i<off+n;i+=2){int sample=(short)((b[i]&255)|(b[i+1]<<8));int out=sample*gain;
                if(out>32767 || out< -32768){lastClips++;clips++;}
                peak=Math.max(peak,Math.abs(sample));sumSquares+=(double)sample*sample;count++;}
        }
        public double rmsDb(){return count==0 || sumSquares==0?-96:Math.max(-96,20*Math.log10(Math.sqrt(sumSquares/count)/32768));}
        public double peakDb(){return peak==0?-96:Math.max(-96,20*Math.log10(peak/32768.0));}
        public long clips(){return clips;}
        public long lastClips(){return lastClips;}
        public void resetWindow(){count=0;sumSquares=0;peak=0;}
    }
}
