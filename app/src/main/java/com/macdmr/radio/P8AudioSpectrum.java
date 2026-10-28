/* SignalDeck - GPL-3.0-or-later. Modified P8 received-PCM adapter. */
package com.macdmr.radio;
import com.macdmr.radio.dsp.Spectrum;
/** Feed the existing FFT from framed 8-kHz PCM, not from audio-level or random animation. */
public final class P8AudioSpectrum {
    private final Spectrum fft=new Spectrum();
    private final short[] samples=new short[80];
    private float[] latest;private long next,lastPcm;private boolean cleared=true;
    public float[] feed(byte[] pcm,int offset,int length,long now){
        if(offset<0 || length<0 || (length&1)!=0 || offset+length>pcm.length)throw new IllegalArgumentException("PCM bounds/alignment");
        if(length==0)return null;
        for(int i=0;i<length;){int count=Math.min(samples.length,(length-i)/2);for(int j=0;j<count;j++){samples[j]=(short)((pcm[offset+i]&255)|(pcm[offset+i+1]<<8));i+=2;}float[] bands=fft.feed(samples,count);if(bands!=null)latest=bands;}
        lastPcm=now;
        if(latest==null || now<next)return null;
        next=now+100;cleared=false;return latest.clone();
    }
    public float[] clear(){fft.reset();latest=null;next=0;cleared=true;return new float[Spectrum.BANDS];}
    public float[] expire(long now){return !cleared && now-lastPcm>=400?clear():null;}
}
