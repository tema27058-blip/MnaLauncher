package com.beigelauncher.nativeapp;

import android.os.Bundle;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/** Adds visible transitions around the existing cached native screen host. */
public class MotionMonoActivity extends FastMonoActivity {
 private FrameLayout screens;
 private View previous;
 private int generation=0;
 private final LinearInterpolator linear=new LinearInterpolator();
 private final ViewTreeObserver.OnPreDrawListener observer=()->{syncTransition();return true;};
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  FrameLayout container=findViewById(android.R.id.content);
  if(container.getChildCount()>0&&container.getChildAt(0) instanceof FrameLayout){
   FrameLayout root=(FrameLayout)container.getChildAt(0);
   if(root.getChildCount()>0&&root.getChildAt(0) instanceof FrameLayout){
    screens=(FrameLayout)root.getChildAt(0);
    screens.getViewTreeObserver().addOnPreDrawListener(observer);
   }
  }
 }
 private void syncTransition(){
  if(screens==null)return;
  View next=null;
  for(int n=0;n<screens.getChildCount();n++){
   View child=screens.getChildAt(n);
   if(child.getVisibility()==View.VISIBLE&&child.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)next=child;
  }
  if(next==null||next==previous)return;
  final View old=previous,incoming=next;previous=next;final int token=++generation;
  for(int n=0;n<screens.getChildCount();n++){
   View child=screens.getChildAt(n);child.animate().cancel();child.setLayerType(View.LAYER_TYPE_NONE,null);
   child.setAlpha(1);child.setTranslationY(0);
   if(child!=next)child.setVisibility(View.GONE);
  }
  next.setVisibility(View.VISIBLE);next.setClickable(true);next.bringToFront();
  boolean enabled=getSharedPreferences("mna-mono",MODE_PRIVATE).getBoolean("motion",true);
  if(old==null||old.getParent()!=screens||!enabled)return;
  int duration=getSharedPreferences("mna-mono",MODE_PRIVATE).getInt("speed",100);
  duration=Math.max(100,Math.min(260,duration));
  int direction=next==screens.getChildAt(0)?-1:1;
  // The home screen is identified by its centered FrameLayout, not child order after bringToFront.
  if(next instanceof FrameLayout&&!(old instanceof FrameLayout))direction=-1;
  else if(!(next instanceof FrameLayout)&&old instanceof FrameLayout)direction=1;
  old.setVisibility(View.VISIBLE);old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  old.animate().alpha(0).translationY(-direction*dp(12)).setDuration(duration).setInterpolator(linear).withLayer().withEndAction(()->{
   if(token==generation&&previous!=old){old.setVisibility(View.GONE);old.setAlpha(1);old.setTranslationY(0);}
  }).start();
  incoming.setAlpha(0);incoming.setTranslationY(direction*dp(24));
  incoming.animate().alpha(1).translationY(0).setDuration(duration).setInterpolator(linear).withLayer().withEndAction(()->{
   if(token==generation){incoming.setAlpha(1);incoming.setTranslationY(0);}
  }).start();
 }
 @Override protected void onDestroy(){
  generation++;
  if(screens!=null){if(screens.getViewTreeObserver().isAlive())screens.getViewTreeObserver().removeOnPreDrawListener(observer);for(int n=0;n<screens.getChildCount();n++)screens.getChildAt(n).animate().cancel();}
  super.onDestroy();
 }
}
