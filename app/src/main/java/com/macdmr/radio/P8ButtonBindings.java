/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified root-free P8 buttons. */
package com.macdmr.radio;
import android.content.Context;
import android.view.KeyEvent;
/** Foreground programmable keys; no input-device/root reader and no transmit assignment. */
public final class P8ButtonBindings {
    private P8ButtonBindings(){}
    public static java.util.function.Consumer<KeyEvent> learning;
    public static boolean acceptable(int code){return (code>=KeyEvent.KEYCODE_F1 && code<=KeyEvent.KEYCODE_F12) || (code>=KeyEvent.KEYCODE_BUTTON_A && code<=KeyEvent.KEYCODE_BUTTON_MODE);}
    public static boolean handle(Context c,RadioService service,KeyEvent e){
        if(learning!=null){learning.accept(e);return true;}
        android.content.SharedPreferences p=c.getSharedPreferences("p8_buttons",0);
        for(int i=0;i<2;i++)if(p.getInt("key"+i,-1)==e.getKeyCode()){
            if(e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0){switch(p.getInt("action"+i,0)){
                case 1:service.stepChannel(1);break;case 2:service.stepChannel(-1);break;case 3:service.toggleScan();break;case 4:service.setMonitor(!service.monitorOn());break;default:break;
            }}return true;
        }return false;
    }
}
