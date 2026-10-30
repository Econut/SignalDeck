/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio.ui;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import com.macdmr.radio.*;
/** Reopenable setup; checks current permissions rather than trusting an old completion flag. */
public final class P8SetupWizard {
 private P8SetupWizard(){}
 private static java.lang.ref.WeakReference<AlertDialog> current=new java.lang.ref.WeakReference<>(null);
 public static void dismiss(){AlertDialog d=current.get();if(d!=null)d.dismiss();current.clear();}
 public static boolean microphone(Context c){return c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
 public static boolean notifications(Context c){return Build.VERSION.SDK_INT<33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
 public static boolean stock(Context c){try{c.getPackageManager().getApplicationInfo("com.wtwd.ucee.dmr",0);return true;}catch(Exception e){return false;}}
 public static void show(Context c,RadioService s){
  dismiss();
  Context owner=c;while(owner instanceof ContextWrapper && !(owner instanceof Activity))owner=((ContextWrapper)owner).getBaseContext();
  if(!(owner instanceof Activity))return;Activity a=(Activity)owner;
  LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);int pad=Theme.dp(a,20);body.setPadding(pad,pad,pad,pad);
  TextView status=new TextView(a);body.addView(status);
  CheckBox automatic=new CheckBox(a);automatic.setText("Connect automatically when I open P8 Radio");automatic.setChecked(a.getSharedPreferences("p8_radio",0).getBoolean("autoStartup",true));body.addView(automatic);
  CheckBox cold=new CheckBox(a);cold.setText("Allow bounded cold-start recovery (release PTT once, then SignalDeck initialisation)");cold.setChecked(a.getSharedPreferences("p8_radio",0).getBoolean("coldRecovery",false));body.addView(cold);
  CheckBox phone=new CheckBox(a);phone.setText("Phone-only after reboot: keep radio off until opened");phone.setChecked(P8ModulePower.preferences(a).getBoolean("phoneOnlyAtBoot",false));body.addView(phone);
  phone.setOnCheckedChangeListener((b,on)->P8ModulePower.preferences(a).edit().putBoolean("phoneOnlyAtBoot",on).apply());
  Button skip=new Button(a);skip.setText("Skip optional notifications");skip.setVisibility(notifications(a)?android.view.View.GONE:android.view.View.VISIBLE);skip.setOnClickListener(v->{a.getSharedPreferences("p8_radio",0).edit().putBoolean("notificationSkipped",true).apply();skip.setVisibility(android.view.View.GONE);});body.addView(skip);
  TextView disclosure=new TextView(a);disclosure.setText("The handover helper is bundled in this APK. Android still requires you to enable it in Accessibility. Its code acts only on Intercom’s Android Settings controls during a requested handover. No other apps, screen recordings or data uploads.\n\nCold recovery only writes the PTT release value1 with UARTs closed and power/audio1/1, verifies it, then SignalDeck initialises the module directly. It never asserts PTT or changes calibration. Module startup uses stock power/audio enable values. Phone-only uses stock audio4/power0 only after UART closure and confirmed ownership; boot power-off skips if Intercom is not stopped. The driver reports constant1 for both power/audio reads; these are not battery/current measurements. No failed operation is retried.");body.addView(disclosure);
  automatic.setOnCheckedChangeListener((b,on)->a.getSharedPreferences("p8_radio",0).edit().putBoolean("autoStartup",on).apply());
  cold.setOnCheckedChangeListener((b,on)->a.getSharedPreferences("p8_radio",0).edit().putBoolean("coldRecovery",on).apply());
  ScrollView scroll=new ScrollView(a);scroll.addView(body);scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Theme.dp(a,320)));
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("P8 setup").setView(scroll).setPositiveButton("Next step",null).setNegativeButton("Close",null).create();
  Handler ui=new Handler(Looper.getMainLooper());
  Runnable refresh=new Runnable(){public void run(){if(!dialog.isShowing())return;status.setText("Stock Intercom: "+(stock(a)?"installed":"MISSING")+"\nMicrophone: "+(microphone(a)?"allowed":"needed for PTT")+"\nNotifications: "+(notifications(a)?"allowed":"optional; not allowed")+"\nHandover helper: "+(P8TakeoverAccessibility.available()?"enabled and connected":"enable in Accessibility")+"\nRadio: "+(s!=null && s.isReady()?"ready (tune acknowledged)":"not yet verified")+"\nBoot: "+P8ModulePower.preferences(a).getString("bootResult","not yet tested")+"\n\n");ui.postDelayed(this,500);}};
  dialog.setOnShowListener(d->{ui.post(refresh);
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
    P8SetupPolicy.Step step=P8SetupPolicy.next(stock(a),microphone(a),notifications(a),a.getSharedPreferences("p8_radio",0).getBoolean("notificationSkipped",false),P8TakeoverAccessibility.available());
    if(step==P8SetupPolicy.Step.STOCK_MISSING){Toast.makeText(a,"Keep original Intercom installed; it is required for fallback and ownership checks",Toast.LENGTH_LONG).show();return;}
    if(step==P8SetupPolicy.Step.MICROPHONE){a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},88);return;}
    if(step==P8SetupPolicy.Step.NOTIFICATIONS){if(Build.VERSION.SDK_INT>=33)a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},89);return;}
    if(step==P8SetupPolicy.Step.HELPER){a.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
    dialog.dismiss();if(a instanceof MainActivity)((MainActivity)a).startP8FromSetup();
   });});
  dialog.setOnDismissListener(d->ui.removeCallbacksAndMessages(null));current=new java.lang.ref.WeakReference<>(dialog);dialog.show();
 }
}
