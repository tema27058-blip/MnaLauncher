package com.beigelauncher.nativeapp;

import android.animation.*;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** Paged native workspace and explicit animation-before-launch sequencing. */
public class WorkspaceActivity extends FullscreenActivity {
 private final HashMap<String,Integer> placement=new HashMap<>();
 private boolean pagesLoaded=false,rendering=false;
 private int desktopPage=0,totalPages=1;
 private TextView pageLabel;
 private Morph card;
 private ValueAnimator morphAnimation;
 private boolean opening=false,launchPending=false,returnQueued=false;
 private String appKey="";
 private RectF origin=new RectF();
 @Override public void onCreate(Bundle state){super.onCreate(state);installPager();updateStatusClock();canvas.post(()->render());}
 private void installPager(){
  FrameLayout old=canvas;android.view.ViewGroup.LayoutParams lp=old.getLayoutParams();int index=desk.indexOfChild(old);
  desk.removeView(old);canvas=new SwipeCanvas();desk.addView(canvas,index,lp);
  for(int n=desk.getChildCount()-1;n>=0;n--){View v=desk.getChildAt(n);if(v instanceof TextView)desk.removeViewAt(n);}
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
  TextView previous=text("‹",22,TEXT),next=text("›",22,TEXT);pageLabel=text("",11,TEXT);pageLabel.setGravity(Gravity.CENTER);
  previous.setGravity(Gravity.CENTER);next.setGravity(Gravity.CENTER);previous.setContentDescription("Предыдущая страница");next.setContentDescription("Следующая страница");
  controls.addView(previous,new LinearLayout.LayoutParams(dp(44),-1));controls.addView(pageLabel,new LinearLayout.LayoutParams(dp(96),-1));controls.addView(next,new LinearLayout.LayoutParams(dp(44),-1));
  previous.setOnClickListener(v->turnPage(-1));next.setOnClickListener(v->turnPage(1));
  desk.addView(controls,new FrameLayout.LayoutParams(-1,dp(25),Gravity.BOTTOM));
 }
 private void updateStatusClock(){
  if(root==null)return;
  for(int n=0;n<root.getChildCount();n++){View v=root.getChildAt(n);
   if(v instanceof LinearLayout){LinearLayout row=(LinearLayout)v;
    if(row.getChildCount()==3&&row.getChildAt(0) instanceof TextView&&row.getChildAt(1) instanceof TextView&&row.getChildAt(2) instanceof TextView)
     row.getChildAt(0).setVisibility(selectedTab==0?View.INVISIBLE:View.VISIBLE);
   }
  }
 }
 @Override void switchPage(int n,boolean animate){super.switchPage(n,animate);updateStatusClock();}
 private void loadPages(){if(pagesLoaded)return;pagesLoaded=true;
  try{JSONObject o=new JSONObject(prefs.getString("workspace-pages","{}"));Iterator<String> keys=o.keys();while(keys.hasNext()){String key=keys.next();placement.put(key,Math.max(0,o.optInt(key,0)));}}catch(Exception ignored){}
 }
 private int pageOf(Item i){Integer page=placement.get(i.id);return page==null?0:page;}
 private void savePages(){JSONObject object=new JSONObject();try{for(Item i:items)object.put(i.id,pageOf(i));}catch(Exception ignored){}prefs.edit().putString("workspace-pages",object.toString()).apply();}
 private void addMissingApps(){
  HashSet<String> existing=new HashSet<>();for(Item i:items)if(i.type.equals("app"))existing.add(i.key);
  float cw=canvas.getWidth(),ch=canvas.getHeight();if(cw<1||ch<1)return;
  int rows=Math.max(1,(int)(ch/dp(102)));float cellW=cw/4,cellH=ch/rows;
  for(App a:apps){if(existing.contains(a.key))continue;
   Item item=new Item();item.key=a.key;item.w=Math.min(64,(int)(cellW/getResources().getDisplayMetrics().density)-6);item.w=Math.max(42,item.w);item.h=86;
   boolean found=false;
   for(int page=0;!found;page++)for(int slot=0;slot<rows*4&&!found;slot++){
    float x=(slot%4)*cellW+(cellW-dp(item.w))/2,y=(slot/4)*cellH+dp(4);
    RectF candidate=new RectF(x,y,x+dp(item.w),y+dp(item.h));boolean free=true;
    for(Item old:items)if(pageOf(old)==page){RectF occupied=new RectF(old.x*cw,old.y*ch,old.x*cw+dp(old.w),old.y*ch+dp(old.h));occupied.inset(-dp(3),-dp(3));if(RectF.intersects(candidate,occupied)){free=false;break;}}
    if(free){item.x=x/cw;item.y=y/ch;placement.put(item.id,page);items.add(item);existing.add(item.key);found=true;}
   }
  }
 }
 @Override void render(){
  if(rendering||canvas==null||canvas.getWidth()==0||canvas.getHeight()==0)return;
  rendering=true;
  try{loadPages();addMissingApps();totalPages=1;for(Item i:items)totalPages=Math.max(totalPages,pageOf(i)+1);desktopPage=Math.max(0,Math.min(desktopPage,totalPages-1));
   ArrayList<Item> all=new ArrayList<>(items),visible=new ArrayList<>();for(Item i:all)if(pageOf(i)==desktopPage)visible.add(i);
   items.clear();items.addAll(visible);
   try{super.render();}finally{items.clear();items.addAll(all);}
   if(pageLabel!=null)pageLabel.setText((desktopPage+1)+" / "+totalPages);
   savePages();save();
  }finally{rendering=false;}
 }
 private void turnPage(int direction){if(edit||opening)return;int target=Math.max(0,Math.min(totalPages-1,desktopPage+direction));if(target==desktopPage)return;
  desktopPage=target;selected=null;render();if(motion){canvas.setAlpha(.3f);canvas.setTranslationX(dp(22)*direction);canvas.animate().alpha(1).translationX(0).setDuration(170).start();}}
 private class SwipeCanvas extends FrameLayout {
  float sx,sy;boolean swiping=false;
  SwipeCanvas(){super(WorkspaceActivity.this);}
  @Override public boolean onInterceptTouchEvent(MotionEvent e){
   if(edit)return false;
   if(e.getActionMasked()==MotionEvent.ACTION_DOWN){sx=e.getX();sy=e.getY();swiping=false;}
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-sx)>dp(18)&&Math.abs(e.getX()-sx)>Math.abs(e.getY()-sy)*1.3f){swiping=true;return true;}
   return false;
  }
  @Override public boolean onTouchEvent(MotionEvent e){
   if(edit)return false;
   if(e.getActionMasked()==MotionEvent.ACTION_DOWN){sx=e.getX();sy=e.getY();return true;}
   if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=e.getX()-sx;if(Math.abs(dx)>dp(40))turnPage(dx<0?1:-1);swiping=false;return true;}
   return true;
  }
 }
 @Override void launch(App app,View icon){
  if(opening||launchPending||card!=null)return;
  if(!motion){super.launch(app,icon);return;}
  Drawable drawable=icon instanceof ImageView?((ImageView)icon).getDrawable():null;
  if(drawable==null){super.launch(app,icon);return;}
  appKey=app.key;origin=localRect(icon);Drawable.ConstantState state=drawable.getConstantState();
  card=new Morph(state==null?drawable:state.newDrawable(getResources()).mutate(),app.name);
  root.addView(card,new FrameLayout.LayoutParams(-1,-1));card.setClickable(true);opening=true;
  animateMorph(0,1,320,()->{
   final Morph expanded=card;
   if(expanded==null)return;
   expanded.progress=1;expanded.invalidate();
   // Keep the final expanded card visible for a frame before starting the real Activity.
   expanded.postOnAnimation(()->expanded.postOnAnimation(()->{
    if(card!=expanded||!opening||isFinishing()||isDestroyed())return;
    opening=false;launchPending=true;
    try{Bundle options=ActivityOptions.makeCustomAnimation(this,android.R.anim.fade_in,android.R.anim.fade_out).toBundle();launcher.startMainActivity(app.info.getComponentName(),app.info.getUser(),null,options);}
    catch(Exception e){clearMorph();toast("Не удалось открыть приложение");}
   }));
  });
 }
 private RectF localRect(View view){int[] a=new int[2],b=new int[2];view.getLocationOnScreen(a);root.getLocationOnScreen(b);return new RectF(a[0]-b[0],a[1]-b[1],a[0]-b[0]+Math.max(1,view.getWidth()),a[1]-b[1]+Math.max(1,view.getHeight()));}
 private void scheduleReturn(){
  if(!launchPending||card==null||returnQueued)return;returnQueued=true;
  root.post(()->{returnQueued=false;if(!launchPending||card==null||isFinishing()||isDestroyed())return;
   launchPending=false;
   if(selectedTab==0){for(Item i:items)if(i.type.equals("app")&&i.key.equals(appKey)){desktopPage=pageOf(i);break;}render();
    boolean found=false;for(Item i:items)if(i.type.equals("app")&&i.key.equals(appKey)&&pageOf(i)==desktopPage){FrameLayout box=itemViews.get(i.id);if(box!=null){origin=localRect(box);if(labels)origin.bottom-=dp(22);found=true;break;}}
    if(!found)origin=localRect(tabs[2]);
   }
   card.setVisibility(View.VISIBLE);animateMorph(1,0,360,()->clearMorph());
  });
 }
 private void animateMorph(float from,float to,int duration,Runnable end){
  if(morphAnimation!=null)morphAnimation.cancel();final Morph active=card;if(active==null)return;
  morphAnimation=ValueAnimator.ofFloat(from,to);morphAnimation.setDuration(motion?duration:1);morphAnimation.setInterpolator(new PathInterpolator(.2f,.8f,.2f,1));
  morphAnimation.addUpdateListener(a->{if(card==active){active.progress=(float)a.getAnimatedValue();active.invalidate();}});
  morphAnimation.addListener(new AnimatorListenerAdapter(){boolean cancelled=false;@Override public void onAnimationCancel(Animator a){cancelled=true;}@Override public void onAnimationEnd(Animator a){if(!cancelled&&card==active)end.run();}});morphAnimation.start();
 }
 private void clearMorph(){if(morphAnimation!=null){ValueAnimator a=morphAnimation;morphAnimation=null;a.cancel();}if(card!=null){root.removeView(card);card=null;}opening=false;launchPending=false;}
 @Override protected void onResume(){super.onResume();updateStatusClock();scheduleReturn();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);updateStatusClock();scheduleReturn();}
 @Override protected void onStop(){if(opening)clearMorph();else if(card!=null)card.setVisibility(View.INVISIBLE);super.onStop();}
 @Override public void onBackPressed(){if(opening){clearMorph();return;}super.onBackPressed();}
 @Override protected void onDestroy(){clearMorph();super.onDestroy();}
 private class Morph extends View {
  final Drawable icon;final String name;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF rect=new RectF();final Path clip=new Path();float progress;
  Morph(Drawable icon,String name){super(WorkspaceActivity.this);this.icon=icon;this.name=name;}
  float mix(float a,float b){return a+(b-a)*progress;}
  @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setColor(Color.BLACK);paint.setAlpha((int)(38*progress));c.drawRect(0,0,getWidth(),getHeight(),paint);
   rect.set(mix(origin.left,0),mix(origin.top,0),mix(origin.right,getWidth()),mix(origin.bottom,getHeight()));float radius=dp(18)+dp(12)*progress;int save=c.save();clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);c.clipPath(clip);paint.setColor(PANEL);paint.setAlpha(255);c.drawRect(rect,paint);
   float size=mix(Math.min(origin.width(),origin.height()),dp(74)),cx=rect.centerX(),cy=rect.centerY();icon.setBounds((int)(cx-size/2),(int)(cy-size/2),(int)(cx+size/2),(int)(cy+size/2));icon.draw(c);
   if(progress>.35f){paint.setColor(TEXT);paint.setAlpha((int)(255*(progress-.35f)/.65f));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(16));c.drawText(name,cx,cy+size/2+dp(30),paint);}c.restoreToCount(save);
  }
 }
}
