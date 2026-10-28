/* SignalDeck - GPL-3.0-or-later. Modified P8 scan-speed trial settings. */
package com.macdmr.radio;
/** Retains the scanner's 700 ms settling guard. Faster settings apply only to FM. */
public final class P8ScanTiming {
    private P8ScanTiming(){}
    public static final int STANDARD=1500;
    public static final int[] OPTIONS={1500,1000,800};
    public static int saved(int ms){for(int n:OPTIONS)if(n==ms)return ms;return STANDARD;}
    public static int dwell(int ms,boolean digital){return digital?STANDARD:saved(ms);}
    public static String label(int ms){switch(saved(ms)){case 1000:return "Balanced · 1.0 s";case 800:return "Fast trial · 0.8 s";default:return "Standard · 1.5 s";}}
}
