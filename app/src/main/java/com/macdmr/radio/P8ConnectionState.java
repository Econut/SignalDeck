package com.macdmr.radio;
/** Connection status only; never a claim about RF output or reception quality. */
public enum P8ConnectionState {
    READY, CONNECTING, DISCONNECTED, OFF, FAULT;
    public static P8ConnectionState of(boolean ready,String link,boolean recovering){
        if(recovering || (link!=null && link.startsWith("connecting")))return CONNECTING;
        if(ready)return READY;
        if("phone-only".equals(link))return OFF;
        if(link==null || link.isEmpty() || link.equals("stopped") || link.equals("idle"))return DISCONNECTED;
        return FAULT;
    }
}
