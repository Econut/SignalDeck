/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 startup; see COPYRIGHT. */
package com.macdmr.radio;
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.widget.Toast;
import com.macdmr.radio.ui.P8SetupWizard;

/** One foreground startup attempt, optional release-only cold recovery and ACK-verified ready. */
public final class P8Startup {
 private final Activity activity;private final RadioService service;private final Handler ui=new Handler(Looper.getMainLooper());
 private long deadline;private volatile boolean closed;private boolean waitingSetup,working;private AlertDialog notice;
 public P8Startup(Activity a,RadioService s){activity=a;service=s;}
 private static boolean stopped(Context c)throws Exception{return (c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&ApplicationInfo.FLAG_STOPPED)!=0;}
 public static void connect(Context c,RadioService service,java.util.function.Consumer<String> done){
  if(service.isReady()){done.accept(null);return;}
  if(service.isTransmitting() || P8Receiver.active()){done.accept("Radio session is already active or connecting");return;}
  try{if(stopped(c)){service.bringUp();done.accept(null);return;}
   P8TakeoverAccessibility.begin(c,error->{if(error==null)service.bringUp();done.accept(error);});
  }catch(Exception e){done.accept("Intercom ownership check failed: "+e.getMessage());}
 }
 public void open(){if(activity.getSharedPreferences("p8_radio",0).getBoolean("autoStartup",true))openExplicit();}
 public void openExplicit(){
  if(closed || working || !service.p8Main())return;
  P8SetupPolicy.Step step=P8SetupPolicy.next(P8SetupWizard.stock(activity),P8SetupWizard.microphone(activity),P8SetupWizard.notifications(activity),activity.getSharedPreferences("p8_radio",0).getBoolean("notificationSkipped",false),P8TakeoverAccessibility.available());
  if(step!=P8SetupPolicy.Step.CONNECT){waitingSetup=true;P8SetupWizard.show(activity,service);return;}
  waitingSetup=false;P8SetupWizard.dismiss();
  if(service.isReady()){ready();return;}
  if(P8TakeoverAccessibility.active())return;
  working=true;
  if(P8Receiver.active()){deadline=SystemClock.elapsedRealtime()+15000;ui.post(poll);return;}
  service.p8StartupMessage("Foreground automatic handover requested");Toast.makeText(activity,"Preparing radio…",Toast.LENGTH_SHORT).show();
  try{
   int ptt=P8Receiver.readState(P8Protocol.PTT_NODE);
   if(ptt==0){coldStart();return;}
   if(ptt!=1)throw new Exception("Unknown PTT state; no operation");
   handover();
  }catch(Exception e){error(e.getMessage());}
 }
 private void coldStart(){
  if(!activity.getSharedPreferences("p8_radio",0).getBoolean("coldRecovery",false)){error("Cold-start recovery is disabled. Enable the release-only option in P8 setup, or use manual recovery.");return;}
  new Thread(()->{
   try{
    P8ColdRecovery.release(new P8ColdRecovery.Backend(){
     public boolean closed(){return !closed && service.p8StartupClosed();}
     public int state(String path)throws Exception{return P8Receiver.readState(path);}
     public void release()throws Exception{P8ReceiveNative.load();P8ReceiveNative.recoverReleased();}
    },true);
    service.p8StartupMessage("Cold recovery: one release verified; opening stock initialisation");
    ui.post(()->{if(closed)return;P8ColdInitPolicy init=new P8ColdInitPolicy(SystemClock.elapsedRealtime());
     try{P8MainRadio.restoreIntercom(activity);}catch(Exception e){error("Stock initialisation launch failed: "+e.getMessage());return;}
     ui.post(new Runnable(){public void run(){
      if(closed)return;
      try{
       int marker=android.provider.Settings.Global.getInt(activity.getContentResolver(),"dmr_init",-1);
       boolean released=P8Receiver.readState(P8Protocol.PTT_NODE)==1 && P8Receiver.readState(P8Protocol.POWER_NODE)==1 && P8Receiver.readState(P8Protocol.AUDIO_CONTROL_NODE)==1;
       P8ColdInitPolicy.Result result=init.observe(SystemClock.elapsedRealtime(),marker,released);
       if(result==P8ColdInitPolicy.Result.READY){service.p8StartupMessage("Fresh stock initialisation marker 0->1 verified");ui.postDelayed(()->{if(!closed)handover();},500);}
       else if(result==P8ColdInitPolicy.Result.FAILED){activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));error("Stock initialisation did not complete with a fresh safe state; no retry");}
       else ui.postDelayed(this,50);
      }catch(Exception e){error("Stock initialisation observation failed: "+e.getMessage());}
     }});
    });
   }catch(Exception e){ui.post(()->error("Cold recovery stopped: "+e.getMessage()));}
  },"P8-startup-release").start();
 }
 private void handover(){connect(activity,service,result->{if(closed)return;if(result!=null){error(result);return;}deadline=SystemClock.elapsedRealtime()+15000;ui.post(poll);});}
 private final Runnable poll=new Runnable(){public void run(){
  if(closed || activity.isFinishing())return;
  if(service.isReady()){working=false;ready();return;}
  if(SystemClock.elapsedRealtime()>=deadline || (!P8Receiver.active() && !P8TakeoverAccessibility.active())){error(service.p8Status());return;}
  ui.postDelayed(this,250);
 }};
 private void ready(){
  if(closed || activity.isFinishing())return;
  if(P8SetupPolicy.complete(P8SetupWizard.microphone(activity),P8TakeoverAccessibility.available(),service.isReady()))activity.getSharedPreferences("p8_radio",0).edit().putInt("setupVerified",1).apply();
  service.p8StartupMessage("Radio ready; tune acknowledged; notice 5 seconds");
  notice=new AlertDialog.Builder(activity).setTitle("Radio ready").setMessage("Connected and listening. Your saved channels and audio settings are loaded.").setPositiveButton("OK",null).create();notice.show();ui.postDelayed(()->{if(notice!=null)notice.dismiss();},5000);
 }
 private void error(String message){working=false;service.p8StartupMessage("Stopped: "+message);if(!closed && !activity.isFinishing())new AlertDialog.Builder(activity).setTitle("Radio not ready").setMessage(message+"\n\nNo automatic retry. Channels and audio settings are kept.").setPositiveButton("P8 setup",(d,w)->P8SetupWizard.show(activity,service)).setNegativeButton("Close",null).show();}
 public static void setup(Context c){P8SetupWizard.show(c,c instanceof MainActivity?((MainActivity)c).service():null);}
 public void resume(){if(waitingSetup)ui.postDelayed(()->{if(!closed && waitingSetup && P8TakeoverAccessibility.available())openExplicit();},1000);}
 public void close(){closed=true;ui.removeCallbacksAndMessages(null);if(notice!=null)notice.dismiss();}
}
