/* SignalDeck - GPL-3.0-or-later. Modified P8 scan-speed trial settings. */
package com.macdmr.radio;
/** Conservative modes retain 700 ms settling; extra-fast FM trials use shorter, explicit guards. */
public final class P8ScanTiming {
    private P8ScanTiming(){}
    public static final int STANDARD=1500;
    public static final int[] OPTIONS={1500,1000,800,600,400};
    public static int saved(int ms){for(int n:OPTIONS)if(n==ms)return ms;return STANDARD;}
    public static int dwell(int ms,boolean digital){return digital?STANDARD:saved(ms);}
    public static int settle(int ms,boolean digital){int n=dwell(ms,digital);return !digital && n<800?n/2:700;}
    public static String label(int ms){switch(saved(ms)){case 1000:return "Balanced · 1.0 s";case 800:return "Fast trial · 0.8 s";case 600:return "Very fast trial · 0.6 s";case 400:return "Extreme trial · 0.4 s";default:return "Standard · 1.5 s";}}
}
