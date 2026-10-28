/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later.
 * Modified P8 main-screen presentation; inherits the existing hold/release safeguards. */
package com.macdmr.radio.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Presentation only. Colour follows service TX callbacks, never the touch-down gesture. */
public final class P8PttBar extends PttButton {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    public P8PttBar(Context c){super(c);setAlpha(1f);}
    @Override public void setState(State state){
        super.setState(state);setAlpha(1f);
        setContentDescription(state==State.TX?"Transmitting. Release to receive":state==State.BUSY?"Receive only. Transmit unavailable":"Receive. Hold to transmit");
        invalidate();
    }
    @Override protected void onMeasure(int width,int height){setMeasuredDimension(MeasureSpec.getSize(width),resolveSize(Theme.dp(getContext(),56),height));}
    @Override protected void onDraw(Canvas canvas){
        float d=Theme.dp(getContext()),sp=getResources().getDisplayMetrics().scaledDensity;
        boolean tx=getState()==State.TX;int colour=tx?Neo.RED:Neo.CYAN;
        box.set(3*d,3*d,getWidth()-3*d,getHeight()-3*d);
        paint.setStyle(Paint.Style.FILL);paint.setColor(tx?0xFF30101B:0xFF08233A);canvas.drawRoundRect(box,12*d,12*d,paint);
        paint.setStyle(Paint.Style.STROKE);
        for(int i=3;i>=1;i--){paint.setStrokeWidth((1.5f+i)*d);paint.setColor(Neo.alpha(colour,0x18));canvas.drawRoundRect(box,12*d,12*d,paint);}
        paint.setStrokeWidth(1.5f*d);paint.setColor(colour);canvas.drawRoundRect(box,12*d,12*d,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(Neo.display(getContext()));paint.setTextSize(21*sp);
        String main=tx?"TRANSMITTING":"RECEIVE";
        float textWidth=paint.measureText(main),iconWidth=30*d,gap=12*d,left=(getWidth()-textWidth-iconWidth-gap)/2;
        Neo.IconDrawable icon=new Neo.IconDrawable("ptt",colour);int top=(int)((getHeight()-34*d)/2);
        icon.setBounds((int)left,top,(int)(left+iconWidth),top+(int)(34*d));icon.draw(canvas);
        float cx=left+iconWidth+gap+textWidth/2;paint.setTextAlign(Paint.Align.CENTER);paint.setColor(colour);
        canvas.drawText(main,cx,getHeight()/2f+1*d,paint);
        paint.setTypeface(Neo.mono(getContext()));paint.setTextSize(10.5f*sp);
        canvas.drawText(tx?"RELEASE TO RECEIVE":getState()==State.BUSY?"RECEIVE ONLY":"HOLD TO TRANSMIT",cx,getHeight()/2f+16*d,paint);
    }
}
