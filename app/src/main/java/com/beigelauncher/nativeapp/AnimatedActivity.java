package com.beigelauncher.nativeapp;

import android.animation.*;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Native app-identity card, not a screenshot or remote app surface. */
public class AnimatedActivity extends FullscreenActivity {
 private Morph card;
 private ValueAnimator animator;
 private boolean opening=false,waitingReturn=false,returnScheduled=false;
 private String launchedKey="";
 private RectF origin=new RectF();
 @Override void launch(App app,View icon){
  if(opening||waitingReturn)return;
  if(!motion){super.launch(app,icon);return;}
  launchedKey=app.key;origin=localRect(icon);
  Drawable drawable=null;
  if(icon instanceof android.widget.ImageView)drawable=((android.widget.ImageView)icon).getDrawable();
  if(drawable==null){super.launch(app,icon);return;}
  Drawable.ConstantState state=drawable.getConstantState();
  Drawable copy=state==null?drawable:state.newDrawable(getResources()).mutate();
  card=new Morph(copy,app.name);root.addView(card,new FrameLayout.LayoutParams(-1,-1));
  card.setClickable(true);card.setContentDescription("Переход к приложению "+app.name);
  opening=true;
  animateCard(0,1,320,()->{
   opening=false;waitingReturn=true;
   try{
    Bundle options=ActivityOptions.makeCustomAnimation(this,android.R.anim.fade_in,android.R.anim.fade_out).toBundle();
    launcher.startMainActivity(app.info.getComponentName(),app.info.getUser(),null,options);
    card.setVisibility(View.INVISIBLE);
   }catch(Exception e){waitingReturn=false;clearCard();toast("Не удалось открыть приложение");}
  });
 }
 private RectF localRect(View view){
  int[] v=new int[2],r=new int[2];view.getLocationOnScreen(v);root.getLocationOnScreen(r);
  return new RectF(v[0]-r[0],v[1]-r[1],v[0]-r[0]+Math.max(1,view.getWidth()),v[1]-r[1]+Math.max(1,view.getHeight()));
 }
 private RectF returnTarget(){
  if(selectedTab==0){
   for(Item item:items)if(item.type.equals("app")&&item.key.equals(launchedKey)){
    FrameLayout box=itemViews.get(item.id);
    if(box!=null&&box.isShown()){RectF rect=localRect(box);rect.bottom=Math.max(rect.top+1,rect.bottom-dp(labels?22:0));return rect;}
   }
   return localRect(tabs[2]);
  }
  return new RectF(origin);
 }
 private void scheduleReturn(){
  if(!waitingReturn||card==null||returnScheduled)return;
  returnScheduled=true;
  root.post(()->{
   returnScheduled=false;if(!waitingReturn||card==null||isFinishing()||isDestroyed())return;
   waitingReturn=false;origin=returnTarget();card.setVisibility(View.VISIBLE);
   animateCard(1,0,360,()->clearCard());
  });
 }
 @Override protected void onResume(){super.onResume();scheduleReturn();}
 @Override protected void onNewIntent(Intent intent){
  super.onNewIntent(intent);scheduleReturn();
 }
 private void animateCard(float from,float to,int duration,Runnable end){
  if(animator!=null)animator.cancel();
  final Morph active=card;if(active==null)return;
  animator=ValueAnimator.ofFloat(from,to);animator.setDuration(motion?duration:1);
  animator.setInterpolator(new PathInterpolator(.2f,.8f,.2f,1));
  animator.addUpdateListener(a->{if(card==active){active.progress=(float)a.getAnimatedValue();active.invalidate();}});
  animator.addListener(new AnimatorListenerAdapter(){
   boolean cancelled=false;
   @Override public void onAnimationCancel(Animator a){cancelled=true;}
   @Override public void onAnimationEnd(Animator a){if(!cancelled&&card==active)end.run();}
  });animator.start();
 }
 private void clearCard(){
  if(animator!=null){ValueAnimator a=animator;animator=null;a.cancel();}
  if(card!=null){root.removeView(card);card=null;}opening=false;waitingReturn=false;
 }
 @Override protected void onStop(){
  if(opening)clearCard();super.onStop();
 }
 @Override protected void onDestroy(){clearCard();super.onDestroy();}
 @Override public void onBackPressed(){
  if(opening){clearCard();return;}super.onBackPressed();
 }
 private class Morph extends View {
  final Drawable icon;final String name;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  final RectF rect=new RectF();final Path clip=new Path();float progress;
  Morph(Drawable icon,String name){super(AnimatedActivity.this);this.icon=icon;this.name=name;setLayerType(View.LAYER_TYPE_HARDWARE,null);}
  private float mix(float start,float finish){return start+(finish-start)*progress;}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   paint.setColor(Color.BLACK);paint.setAlpha((int)(38*progress));canvas.drawRect(0,0,getWidth(),getHeight(),paint);
   rect.set(mix(origin.left,0),mix(origin.top,0),mix(origin.right,getWidth()),mix(origin.bottom,getHeight()));
   float radius=dp(18)+dp(12)*progress;int save=canvas.save();
   clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);canvas.clipPath(clip);
   paint.setColor(PANEL);paint.setAlpha(255);canvas.drawRect(rect,paint);
   float size=mix(Math.min(origin.width(),origin.height()),dp(74));
   float cx=rect.centerX(),cy=rect.centerY();
   icon.setBounds((int)(cx-size/2),(int)(cy-size/2),(int)(cx+size/2),(int)(cy+size/2));icon.draw(canvas);
   if(progress>.35f){paint.setColor(TEXT);paint.setAlpha((int)(255*(progress-.35f)/.65f));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(16));canvas.drawText(name,cx,cy+size/2+dp(30),paint);}
   canvas.restoreToCount(save);
  }
 }
}
