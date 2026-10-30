/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. Modified P8 handover; see COPYRIGHT. */
package com.macdmr.radio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.accessibility.*;
import java.util.List;
import java.util.function.Consumer;

/** Opt-in, Settings-only, 12-second Intercom Force stop assistant. Never keys or opens a UART. */
public final class P8TakeoverAccessibility extends AccessibilityService {
    private static final String STOCK="com.wtwd.ucee.dmr", SETTINGS="com.android.settings";
    private static P8TakeoverAccessibility instance;
    private P8TakeoverPolicy policy; private Consumer<String> callback; private String label;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(policy==null)return;observe();if(policy!=null)ui.postDelayed(this,150);}};
    @Override protected void onServiceConnected(){if(BuildConfig.P8_OFFLINE)instance=this;}
    public static boolean available(){return instance!=null;}
    public static boolean active(){return instance!=null && instance.policy!=null;}
    public static void begin(Context c,Consumer<String> done){
        if(instance==null){done.accept("Enable the P8 Intercom handover helper in Accessibility settings first");return;}
        instance.beginSession(done);
    }
    private void beginSession(Consumer<String> done){
        if(policy!=null){done.accept("An Intercom handover is already in progress");return;}
        if(!BuildConfig.P8_OFFLINE || P8Receiver.active()){done.accept("Close the existing radio session before handover");return;}
        try{
            if(!released()){done.accept("Radio state is not released/ready; use Recover radio connection first");return;}
            ApplicationInfo info=getPackageManager().getApplicationInfo(STOCK,0);
            if((info.flags&ApplicationInfo.FLAG_STOPPED)!=0){done.accept(null);return;}
            label=getPackageManager().getApplicationLabel(info).toString();
            callback=done;policy=new P8TakeoverPolicy(SystemClock.elapsedRealtime());
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+STOCK)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui.post(tick);
        }catch(Exception e){finish("Cannot prepare Intercom handover: "+e.getMessage());}
    }
    private boolean released(){
        if(P8Receiver.active())return false;
        try{return P8Receiver.readState(P8Protocol.PTT_NODE)==1 && P8Receiver.readState(P8Protocol.POWER_NODE)==1 && P8Receiver.readState(P8Protocol.AUDIO_CONTROL_NODE)==1;}catch(Exception e){return false;}
    }
    private boolean stopped(){try{return (getPackageManager().getApplicationInfo(STOCK,0).flags&ApplicationInfo.FLAG_STOPPED)!=0;}catch(Exception e){return false;}}
    private static AccessibilityNodeInfo exact(AccessibilityNodeInfo root,String text){
        if(root==null)return null;
        if(text.contentEquals(root.getText()==null?"":root.getText()) && root.isVisibleToUser())return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo child=root.getChild(i);AccessibilityNodeInfo match=exact(child,text);if(match!=null)return match;}
        return null;
    }
    private static AccessibilityNodeInfo button(AccessibilityNodeInfo node){
        for(int i=0;i<4 && node!=null;i++,node=node.getParent())if(node.isVisibleToUser() && node.isEnabled() && node.isClickable())return node;
        return null;
    }
    private void observe(){
        if(policy==null)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        boolean settings=root!=null && SETTINGS.contentEquals(root.getPackageName()==null?"":root.getPackageName());
        AccessibilityNodeInfo stop=settings?button(exact(root,"Force stop")):null;
        AccessibilityNodeInfo yes=settings?button(exact(root,"OK")):null;
        boolean target=settings && exact(root,label)!=null && exact(root,"App info")!=null;
        boolean confirm=settings && yes!=null && exact(root,"If you force stop an app, it may misbehave.")!=null;
        P8TakeoverPolicy.Action action=policy.next(SystemClock.elapsedRealtime(),settings,target,stop!=null,confirm,released(),stopped());
        if(action==P8TakeoverPolicy.Action.STOP || action==P8TakeoverPolicy.Action.CONFIRM){
            AccessibilityNodeInfo node=action==P8TakeoverPolicy.Action.STOP?stop:yes;
            if(node==null || !released() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK))finish("Intercom handover control was unavailable; stopped without retry");
        }else if(action==P8TakeoverPolicy.Action.FINISH)finish(null);
        else if(action==P8TakeoverPolicy.Action.ABORT)finish("Intercom handover timed out or radio state changed; no automatic retry");
    }
    private void finish(String error){
        Consumer<String> done=callback;callback=null;policy=null;ui.removeCallbacks(tick);
        if(done!=null){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));done.accept(error);}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){if(policy!=null && SETTINGS.contentEquals(event.getPackageName()==null?"":event.getPackageName()))observe();}
    @Override public void onInterrupt(){finish("Intercom handover interrupted");}
    @Override public void onDestroy(){finish("Intercom handover helper stopped");if(instance==this)instance=null;super.onDestroy();}
}
