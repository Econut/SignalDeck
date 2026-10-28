/* SignalDeck - GPL-3.0-or-later. Modified P8 UI regression checks.
 * Isolated views only: no RadioService, radio transport, broadcasts or GPIO writes. */
package com.macdmr.radio;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import com.macdmr.radio.ui.P8PttBar;
import com.macdmr.radio.ui.PttButton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs touch safety on a detached bar with a recording listener, never the service's PTT handler. */
public final class P8UiInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void touch(P8PttBar bar,int action){long t=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(t,t,action,100,40,0);try{check(bar.onTouchEvent(event),"Touch not consumed");}finally{event.recycle();}}
    private static int redPixels(Bitmap bitmap){int count=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++){int p=bitmap.getPixel(x,y),r=(p>>16)&255,g=(p>>8)&255;if(r>90 && r>g*1.4)count++;}return count;}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            runOnMainSync(()->{
                Context context=new ContextThemeWrapper(getTargetContext(),R.style.AppTheme);
                List<Boolean> events=new ArrayList<>();P8PttBar bar=new P8PttBar(context);bar.setListener(events::add);
                bar.measure(View.MeasureSpec.makeMeasureSpec(608,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(112,View.MeasureSpec.EXACTLY));bar.layout(0,0,608,112);
                touch(bar,MotionEvent.ACTION_DOWN);check(bar.getState()!=PttButton.State.TX,"Touch fabricated a TX state");touch(bar,MotionEvent.ACTION_UP);check(events.equals(Arrays.asList(true,false)),"Hold/release events wrong");events.clear();
                touch(bar,MotionEvent.ACTION_DOWN);touch(bar,MotionEvent.ACTION_CANCEL);check(events.equals(Arrays.asList(true,false)),"Cancel did not release");events.clear();
                touch(bar,MotionEvent.ACTION_DOWN);bar.onWindowFocusChanged(false);check(events.equals(Arrays.asList(true,false)),"Focus loss did not release");touch(bar,MotionEvent.ACTION_UP);check(events.size()==2,"Duplicate release after focus loss");events.clear();
                touch(bar,MotionEvent.ACTION_DOWN);
                try{java.lang.reflect.Method method=PttButton.class.getDeclaredMethod("onDetachedFromWindow");method.setAccessible(true);method.invoke(bar);}catch(Exception e){throw new AssertionError(e);}
                check(events.equals(Arrays.asList(true,false)),"Detach did not release");events.clear();
                Bitmap bitmap=Bitmap.createBitmap(608,112,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
                bar.setState(PttButton.State.RX);bar.draw(canvas);int receiveRed=redPixels(bitmap);
                bitmap.eraseColor(0);bar.setState(PttButton.State.TX);bar.draw(canvas);check(redPixels(bitmap)>receiveRed+100,"TX is not visibly red");check(bar.getContentDescription().toString().startsWith("Transmitting"),"TX accessibility state wrong");
                bitmap.eraseColor(0);bar.setState(PttButton.State.RX);bar.draw(canvas);check(bar.getContentDescription().toString().startsWith("Receive"),"RX state not restored");check(redPixels(bitmap)==receiveRed,"RX did not return to cyan");check(events.isEmpty(),"Rendering invoked PTT listener");bitmap.recycle();
            });
            result.putString("stream","PASS: hold/release, no touch-fabricated TX, cancel, focus loss, detach, red TX/cyan RX rendering. Isolated listener; no radio operations.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
    }
}
