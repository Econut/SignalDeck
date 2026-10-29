/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 main radio controls. See COPYRIGHT. */
package com.macdmr.radio.ui;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.media.AudioManager;
import android.widget.*;
import com.macdmr.radio.RadioService;

/** Compact connection strip in the main radio page; audio settings are ordinary UI, not diagnostics. */
public final class P8RadioControls extends LinearLayout {
    private final RadioService service;
    private TextView menu;private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){setKeepScreenOn(service.isTransmitting());menu.setText("P8 MENU");menu.setContentDescription("P8 radio controls. "+(service.txInhibited()?"Receive only":"Analogue PTT available"));ui.postDelayed(this,500);}};
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.post(refresh);}
    @Override protected void onDetachedFromWindow(){ui.removeCallbacks(refresh);super.onDetachedFromWindow();}
    public P8RadioControls(Context c,RadioService s){super(c);service=s;setOrientation(HORIZONTAL);setGravity(android.view.Gravity.END);
        menu=Neo.button(c,"P8 MENU","menu",Neo.CYAN);menu.setTextSize(11);menu.setSingleLine(true);menu.setMinHeight(0);menu.setPadding(Theme.dp(c,8),0,Theme.dp(c,8),0);menu.setCompoundDrawablePadding(Theme.dp(c,5));
        addView(menu,new LayoutParams(LayoutParams.MATCH_PARENT,Theme.dp(c,40)));menu.setOnClickListener(v->controls());
    }
    private void controls(){Context c=getContext();new android.app.AlertDialog.Builder(c).setTitle("P8 radio").setItems(new String[]{"Connect","Hand back to Intercom","Receive volume","Transmit settings","Stop transmitting","Programmable buttons","Connection details","Intercom App info / release","Recover radio connection","Recovery log"},(d,index)->{
        switch(index){
            case 0:com.macdmr.radio.P8MainRadio.takeOver(c,service);break;
            case 1:service.shutDown(true);break;
            case 2:audio();break;
            case 3:transmitSettings();break;
            case 4:service.p8CancelTransmitTest();toast("PTT release requested");break;
            case 5:buttons();break;
            case 6:new android.app.AlertDialog.Builder(c).setTitle("Connection details").setMessage((service.txInhibited()?"Receive only / not connected":"Analogue hold-to-talk")+"\n\n"+service.p8Status()).setPositiveButton("Close",null).show();break;
            case 8:recover(c,service);break;
            case 9:new android.app.AlertDialog.Builder(c).setTitle("Saved radio log").setMessage(service.p8RecoveryLog()).setPositiveButton("Share",(a,b)->{Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"P8 radio recovery log").putExtra(Intent.EXTRA_TEXT,service.p8RecoveryLog());c.startActivity(Intent.createChooser(share,"Share recovery log"));}).setNegativeButton("Close",null).show();break;
            case 7:if(service.isReady()){toast("Hand back first");return;}c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:com.wtwd.ucee.dmr")));break;
        }
    }).show();}
    public static void recover(Context c,RadioService service){
        Toast.makeText(c,"Recovering radio connection…",Toast.LENGTH_SHORT).show();
        service.p8Recover(message->{
            android.app.AlertDialog.Builder result=new android.app.AlertDialog.Builder(c).setTitle("Radio recovery").setMessage(message).setNegativeButton("Close",null);
            if(message.startsWith("Recovered:"))result.setPositiveButton("Open Intercom",(a,b)->com.macdmr.radio.P8MainRadio.restoreIntercom(c));
            else if(message.contains("Intercom is running"))result.setPositiveButton("Intercom App info",(a,b)->c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:com.wtwd.ucee.dmr"))));
            result.show();
        });
    }
    private void transmitSettings(){Context c=getContext();new android.app.AlertDialog.Builder(c).setTitle("Analogue transmit")
            .setItems(new String[]{"Allow transmit on selected channel","Make selected channel receive-only","Transmit timeout ("+(service.p8TransmitTimeout()/1000)+" seconds)"},(d,which)->{
                if(which<2){toast(service.p8ChannelTransmit(which==0));return;}
                new android.app.AlertDialog.Builder(c).setTitle("Maximum continuous transmission").setItems(new String[]{"30 seconds","60 seconds","120 seconds","180 seconds"},(dd,i)->toast(service.p8SaveTransmitTimeout(new int[]{30000,60000,120000,180000}[i]))).show();
            }).setNegativeButton("Close",null).show();
    }
    private void toast(String text){Toast.makeText(getContext(),text,Toast.LENGTH_LONG).show();}
    private void buttons(){service.p8CancelTransmitTest();Context c=getContext();new android.app.AlertDialog.Builder(c).setTitle("Foreground programmable buttons").setItems(new String[]{"Assign button 1","Assign button 2","Clear assignments"},(d,which)->{
        if(which==2){c.getSharedPreferences("p8_buttons",0).edit().clear().commit();toast("Saved: button assignments cleared");return;}
        new android.app.AlertDialog.Builder(c).setTitle("Button "+(which+1)+" action").setItems(new String[]{"None","Next channel","Previous channel","Scan start/stop","Monitor on/off"},(a,action)->{
            if(action==0){c.getSharedPreferences("p8_buttons",0).edit().remove("key"+which).remove("action"+which).commit();toast("Saved: assignment removed");return;}
            final android.app.AlertDialog learn=new android.app.AlertDialog.Builder(c).setTitle("Press the programmable button").setMessage("Use a programmable key, not PTT, SOS or Power. The bottom-left key reports as Camera and is supported. This learns Android Camera/F/gamepad key events while the radio screen is foreground. PTT remains blocked while learning a key.").setNegativeButton("Cancel",null).create();
            com.macdmr.radio.P8ButtonBindings.learning=e->{if(e.getAction()!=android.view.KeyEvent.ACTION_DOWN || e.getRepeatCount()!=0)return;
                if(!com.macdmr.radio.P8ButtonBindings.acceptable(e.getKeyCode())){toast("That key is reserved; choose a programmable function key");return;}
                android.content.SharedPreferences p=c.getSharedPreferences("p8_buttons",0);android.content.SharedPreferences.Editor edit=p.edit();int other=1-which;if(p.getInt("key"+other,-1)==e.getKeyCode())edit.remove("key"+other).remove("action"+other);
                boolean ok=edit.putInt("key"+which,e.getKeyCode()).putInt("action"+which,action).commit();toast(ok?"Saved button "+(which+1):"Save failed");learn.dismiss();};
            learn.setOnKeyListener((d2,key,e)->{if(key==android.view.KeyEvent.KEYCODE_BACK)return false;java.util.function.Consumer<android.view.KeyEvent> capture=com.macdmr.radio.P8ButtonBindings.learning;if(capture!=null)capture.accept(e);return true;});
            learn.setOnDismissListener(x->com.macdmr.radio.P8ButtonBindings.learning=null);learn.show();
        }).show();
    }).show();}
    private void button(LinearLayout row,String title,Runnable run){TextView b=Neo.button(getContext(),title,"",Neo.CYAN);b.setTextSize(11);b.setSingleLine(true);row.addView(b,new LayoutParams(0,Theme.dp(getContext(),44),1));b.setOnClickListener(v->run.run());}
    private void audio(){Context c=getContext();LinearLayout body=new LinearLayout(c);body.setOrientation(VERTICAL);body.setPadding(24,12,24,12);
        TextView notice=new TextView(c);notice.setText("Saved controls apply on Connect; module and gain also apply during RX.");body.addView(notice);
        AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        slider(body,"Module receive level",1,9,service.p8ModuleVolume(),v->notice.setText(service.p8SaveAudio("moduleVolume",v)));
        slider(body,"Android media volume",0,am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),am.getStreamVolume(AudioManager.STREAM_MUSIC),v->{am.setStreamVolume(AudioManager.STREAM_MUSIC,v,0);notice.setText(service.p8SaveAudio("mediaVolume",v));});
        slider(body,"Software gain ×",1,4,service.p8Gain(),v->notice.setText(service.p8SaveAudio("softwareGain",v)));
        TextView meter=new TextView(c);body.addView(meter);
        android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable tick=new Runnable(){public void run(){meter.setText(service.p8AudioMeter());handler.postDelayed(this,500);}};handler.post(tick);
        new android.app.AlertDialog.Builder(c).setTitle("Receive volume").setView(body).setPositiveButton("Done",null).setOnDismissListener(d->handler.removeCallbacks(tick)).show();
    }
    private interface Value{void set(int value);}
    private void slider(LinearLayout body,String title,int min,int max,int value,Value save){TextView label=new TextView(getContext());label.setText(title+": "+value);body.addView(label);SeekBar bar=new SeekBar(getContext());bar.setMax(max-min);bar.setProgress(value-min);body.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean user){label.setText(title+": "+(n+min));}public void onStopTrackingTouch(SeekBar b){save.set(b.getProgress()+min);}});
    }
}
