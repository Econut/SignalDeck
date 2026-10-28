/* SignalDeck - GPL-3.0-or-later. Modified P8 normal analogue operation. */
package com.macdmr.radio;

/** Wire limits from the stock analogue command, shared by channel mapping and normal PTT. */
public final class P8AnaloguePolicy {
    private P8AnaloguePolicy(){}
    public static final int DEFAULT_TIMEOUT_MS=60000;
    public static boolean frequency(int hz){return (hz>=136000000 && hz<=174000000)||(hz>=400000000 && hz<=480000000);}
    public static void timeout(int ms){if(ms!=30000 && ms!=60000 && ms!=120000 && ms!=180000)throw new IllegalArgumentException("Choose a 30, 60, 120 or 180 second TX timeout");}
    public static void tone(int type,int code){
        if(type<0 || type>3 || code<0 || (type==0 && code!=0) || (type==1 && code>50) || (type>=2 && code>82))throw new IllegalArgumentException("Invalid P8 tone type/index");
    }
    public static void validate(int rx,int tx,int power,int rxType,int rxCode,int txType,int txCode){
        if(!frequency(rx)||!frequency(tx))throw new IllegalArgumentException("P8 frequencies must be 136–174 or 400–480 MHz");
        if(power<0 || power>1)throw new IllegalArgumentException("P8 power must be Low or High");
        tone(rxType,rxCode);tone(txType,txCode);
    }
}
