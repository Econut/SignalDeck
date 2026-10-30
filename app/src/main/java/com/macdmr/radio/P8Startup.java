/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 startup; see COPYRIGHT. */
package com.macdmr.radio;
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.widget.Toast;

/** Foreground launch coordinator. At most one connection attempt; success requires service ready ACK. */
public final class P8Startup {
    private final Activity activity;private final RadioService service;private final Handler ui=new Handler(Looper.getMainLooper());
    private long deadline;private boolean closed,waitingHelper;private AlertDialog notice;
    public P8Startup(Activity a,RadioService s){activity=a;service=s;}
    private static boolean stopped(Context c)throws Exception{return (c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&ApplicationInfo.FLAG_STOPPED)!=0;}
    public static void connect(Context c,RadioService service,java.util.function.Consumer<String> done){
        if(service.isReady()){done.accept(null);return;}
        if(service.isTransmitting() || P8Receiver.active()){done.accept("Radio session is already active or connecting");return;}
        try{
            if(stopped(c)){service.bringUp();done.accept(null);return;}
            P8TakeoverAccessibility.begin(c,error->{if(error==null)service.bringUp();done.accept(error);});
        }catch(Exception e){done.accept("Intercom ownership check failed: "+e.getMessage());}
    }
    public void open(){
        if(!service.p8Main() || !activity.getSharedPreferences("p8_radio",0).getBoolean("autoStartup",true))return;
        if(service.isReady()){ready();return;}
        if(P8TakeoverAccessibility.active())return;
        if(P8Receiver.active()){deadline=SystemClock.elapsedRealtime()+15000;ui.post(poll);return;}
        try{if(!stopped(activity) && !P8TakeoverAccessibility.available()){waitingHelper=true;setup(activity);return;}}catch(Exception e){error(e.getMessage());return;}
        service.p8StartupMessage("Foreground automatic handover requested");
        Toast.makeText(activity,"Preparing radio…",Toast.LENGTH_SHORT).show();
        connect(activity,service,result->{if(closed)return;if(result!=null){error(result);return;}deadline=SystemClock.elapsedRealtime()+15000;ui.post(poll);});
    }
    private final Runnable poll=new Runnable(){public void run(){
        if(closed || activity.isFinishing())return;
        if(service.isReady()){ready();return;}
        if(SystemClock.elapsedRealtime()>=deadline || (!P8Receiver.active() && !P8TakeoverAccessibility.active())){error(service.p8Status());return;}
        ui.postDelayed(this,250);
    }};
    private void ready(){
        if(closed || activity.isFinishing())return;
        service.p8StartupMessage("Radio ready; tune acknowledged; notice 5 seconds");
        notice=new AlertDialog.Builder(activity).setTitle("Radio ready").setMessage("Connected and listening. Your saved channels and audio settings are loaded.").setPositiveButton("OK",null).create();notice.show();
        ui.postDelayed(()->{if(notice!=null)notice.dismiss();},5000);
    }
    private void error(String message){service.p8StartupMessage("Stopped: "+message);if(!closed && !activity.isFinishing())new AlertDialog.Builder(activity).setTitle("Radio not ready").setMessage(message+"\n\nNo automatic retry. Use P8 MENU → Recover radio connection or open Intercom to initialise it, then Connect.").setPositiveButton("OK",null).show();}
    public static void setup(Context c){
        new AlertDialog.Builder(c).setTitle("Automatic P8 startup").setMessage("Optional root-free handover: enable P8 Intercom handover in Android Accessibility settings once. When you open P8 Radio it can press Intercom’s Force stop and return here to connect.\n\nThe helper reads only Android Settings during a bounded Intercom handover, not other apps. It cannot fix an uninitialised module. You may disable automatic startup or the helper at any time.")
            .setPositiveButton("Enable helper",(d,w)->c.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNeutralButton("Automatic startup: "+(c.getSharedPreferences("p8_radio",0).getBoolean("autoStartup",true)?"ON":"OFF"),(d,w)->{boolean next=!c.getSharedPreferences("p8_radio",0).getBoolean("autoStartup",true);c.getSharedPreferences("p8_radio",0).edit().putBoolean("autoStartup",next).apply();Toast.makeText(c,"Automatic startup "+(next?"enabled":"disabled"),Toast.LENGTH_SHORT).show();})
            .setNegativeButton("Close",null).show();
    }
    public void resume(){if(waitingHelper)ui.postDelayed(()->{if(!closed && waitingHelper && P8TakeoverAccessibility.available()){waitingHelper=false;open();}},1000);}
    public void close(){closed=true;ui.removeCallbacksAndMessages(null);if(notice!=null)notice.dismiss();}
}
