package com.beigelauncher.nativeapp;

import android.os.Build;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

public class DockActivity extends MnaActivity {
 private final ImageView[] dockIcons=new ImageView[4];
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(Build.VERSION.SDK_INT>=30){
   getWindow().setDecorFitsSystemWindows(false);
   root.setFitsSystemWindows(false);
   root.setOnApplyWindowInsetsListener((v,insets)->{
    Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());
    Insets ime=insets.getInsets(WindowInsets.Type.ime());
    Insets cut=insets.getInsets(WindowInsets.Type.displayCutout());
    v.setPadding(Math.max(nav.left,cut.left),cut.top,Math.max(nav.right,cut.right),Math.max(nav.bottom,ime.bottom));
    return insets;
   });
   root.requestApplyInsets();
  }
  hideStatus();
 }
 private void hideStatus(){
  if(Build.VERSION.SDK_INT>=30){
   WindowInsetsController c=getWindow().getInsetsController();
   if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.statusBars());}
  }else{getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);}
 }
 @Override protected void onResume(){super.onResume();hideStatus();}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)hideStatus();}
 @Override void buildDock(){
  dock=new FrameLayout(this);dock.setBackground(shape(PANEL,24));
  FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(-1,dp(74),Gravity.BOTTOM);
  placement.setMargins(dp(10),0,dp(10),dp(14));root.addView(dock,placement);
  pill=new View(this);pill.setBackground(shape(accent,18));
  FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(72),dp(58));pp.topMargin=dp(8);dock.addView(pill,pp);
  LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
  dock.addView(row,new FrameLayout.LayoutParams(-1,-1));
  String[] names={"Рабочий стол","Настройки","Приложения","Кастомизация"};
  for(int n=0;n<4;n++){
   final int index=n;
   LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);
   cell.setPadding(dp(2),0,dp(2),0);cell.setClickable(true);cell.setFocusable(true);cell.setContentDescription(names[n]);
   row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
   ImageView icon=new ImageView(this);icon.setImageDrawable(new Glyph(n));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
   dockIcons[n]=icon;cell.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));
   TextView label=text(names[n],10,MUTED);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);
   label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER);tabs[n]=label;
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(15));lp.topMargin=dp(4);cell.addView(label,lp);
   cell.setOnClickListener(v->switchPage(index,true));
  }
  dock.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{if(r-l!=or_-ol)movePill(false);});
  dock.post(()->movePill(false));
 }
 @Override void movePill(boolean animate){
  super.movePill(animate);
  for(int n=0;n<4;n++)if(dockIcons[n]!=null)dockIcons[n].setColorFilter(n==selectedTab?0xff211b15:MUTED);
 }
 private class Glyph extends Drawable {
  final int kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  Glyph(int kind){this.kind=kind;}
  public int getIntrinsicWidth(){return dp(23);}
  public int getIntrinsicHeight(){return dp(23);}
  public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(MUTED);
   Path path=new Path();
   if(kind==0){path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.lineTo(21,21);path.lineTo(15,21);path.lineTo(15,15);path.lineTo(9,15);path.lineTo(9,21);path.lineTo(3,21);path.close();c.drawPath(path,p);}
   if(kind==1){c.drawCircle(12,12,3,p);c.drawCircle(12,12,7,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}}
   if(kind==2)for(int y=4;y<=16;y+=6)for(int x=4;x<=16;x+=6)c.drawRoundRect(x,y,x+3,y+3,1,1,p);
   if(kind==3)for(int i=0;i<3;i++){float x=5+i*7;c.drawLine(x,3,x,21,p);c.drawCircle(x,i==1?15:8,2,p);}
   c.restoreToCount(save);
  }
  public void setAlpha(int a){p.setAlpha(a);}
  public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
  public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
