package com.macdmr.radio;
import com.macdmr.radio.dsp.Spectrum;
import org.junit.Test;
import static org.junit.Assert.*;
public class P8AudioSpectrumTest {
    private byte[] block(double hz,int first){byte[] data=new byte[172];for(int i=0;i<80;i++){short s=(short)(16000*Math.sin(2*Math.PI*hz*(first+i)/8000));data[6+2*i]=(byte)s;data[7+2*i]=(byte)(s>>8);}return data;}
    @Test public void framedPayloadOffsetFeedsRealFrequencyBand(){P8AudioSpectrum fft=new P8AudioSpectrum();float[] bands=null;for(int n=0;n<4;n++)bands=fft.feed(block(1000,n*80),6,160,n*10);assertNotNull(bands);int peak=0;for(int n=1;n<bands.length;n++)if(bands[n]>bands[peak])peak=n;assertEquals(Spectrum.bandOf(1000),peak);assertTrue(bands[peak]>0.8);}
    @Test public void dataIsThrottledAndResetDoesNotInventAudio(){P8AudioSpectrum fft=new P8AudioSpectrum();float[] first=null;for(int n=0;n<4;n++)first=fft.feed(block(1000,n*80),6,160,n*10);assertNotNull(first);assertNull(fft.feed(block(1000,320),6,160,40));float old=first[Spectrum.bandOf(1000)];fft.clear();assertEquals(old,first[Spectrum.bandOf(1000)],0);for(float v:fft.clear())assertEquals(0,v,0);assertNull(fft.expire(999));}
    @Test public void noPcmExpiresOnceInsteadOfLeavingStaleBars(){P8AudioSpectrum fft=new P8AudioSpectrum();for(int n=0;n<4;n++)fft.feed(block(1000,n*80),6,160,n*10);assertNull(fft.expire(429));float[] zero=fft.expire(430);assertNotNull(zero);for(float v:zero)assertEquals(0,v,0);assertNull(fft.expire(999));}
}
