/* SignalDeck - Copyright (C) 2026 IIMacGyverII. GPL-3.0-or-later. See COPYRIGHT. */
package com.macdmr.radio;
public final class P8SetupPolicy {
 private P8SetupPolicy(){}
 public enum Step { STOCK_MISSING, MICROPHONE, NOTIFICATIONS, HELPER, CONNECT }
 public static Step next(boolean stock,boolean microphone,boolean notifications,boolean notificationSkipped,boolean helper){
  if(!stock)return Step.STOCK_MISSING;if(!microphone)return Step.MICROPHONE;
  if(!notifications && !notificationSkipped)return Step.NOTIFICATIONS;
  if(!helper)return Step.HELPER;return Step.CONNECT;
 }
 public static boolean complete(boolean microphone,boolean helper,boolean ready){return microphone && helper && ready;}
}
