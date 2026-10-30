/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 power control; see COPYRIGHT. */
package com.macdmr.radio;
import android.content.Context;import android.content.pm.ApplicationInfo;
/** Fixed-node power operations; no root or UART command and no package disable. */
public final class P8ModulePower {
 private P8ModulePower(){}
 public static android.content.SharedPreferences preferences(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("p8_power",0);}
 public static void change(Context c,boolean on)throws Exception{
  P8ReceiveNative.load();P8PowerPolicy.change(new P8PowerPolicy.Backend(){
   public boolean closed(){return !P8Receiver.active();}
   public boolean stockStopped()throws Exception{return (c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&ApplicationInfo.FLAG_STOPPED)!=0;}
   public int state(String path)throws Exception{return P8Receiver.readState(path);}
   public void power(boolean enabled)throws Exception{P8ReceiveNative.power(enabled);}
  },on);
 }
 public static void bootOff(Context c){
  int boot=android.provider.Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);
  if(boot<0){android.util.Log.i("P8Power","Boot count unavailable; no boot power operation");return;}
  if(preferences(c).getInt("bootAttempt",-2)==boot)return;
  if(!preferences(c).edit().putInt("bootAttempt",boot).commit())return;
  String result;
  try{
   if((c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0).flags&ApplicationInfo.FLAG_STOPPED)==0)throw new Exception("Intercom is not stopped; boot power-off skipped to avoid competing owners");
   if(P8Receiver.readState(P8Protocol.PTT_NODE)==0){P8ReceiveNative.load();P8ColdRecovery.release(new P8ColdRecovery.Backend(){
    public boolean closed(){return !P8Receiver.active();}
    public int state(String path)throws Exception{return P8Receiver.readState(path);}
    public void release()throws Exception{P8ReceiveNative.recoverReleased();}
   },true);}
   change(c,false);result="Phone-only: stock power-off sequence completed";
  }catch(Exception | LinkageError e){result="Boot phone-only stopped: "+e.getMessage();}
  preferences(c).edit().putString("bootResult",result).apply();android.util.Log.i("P8Power",result);
 }
}
