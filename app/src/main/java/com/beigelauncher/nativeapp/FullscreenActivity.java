package com.beigelauncher.nativeapp;

import android.app.ActivityOptions;
import android.content.*;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class FullscreenActivity extends DockActivity {
 private LinearLayout status;
 private TextView smallTime,connection,battery;
 private int safeLeft,safeRight,safeTop,navBottom,keyboardBottom;
 private final Runnable statusTick=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,5000);}};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  root.setFitsSystemWindows(false);root.setPadding(0,0,0,0);
  getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
  if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
  status=new LinearLayout(this);status.setGravity(Gravity.CENTER_VERTICAL);
  status.setPadding(dp(12),0,dp(12),0);
  smallTime=text("",12,TEXT);smallTime.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  connection=text("",11,TEXT);connection.setGravity(Gravity.CENTER);connection.setSingleLine(true);connection.setEllipsize(TextUtils.TruncateAt.END);
  battery=text("",12,TEXT);battery.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
  status.addView(smallTime,new LinearLayout.LayoutParams(dp(53),-1));
  status.addView(connection,new LinearLayout.LayoutParams(0,-1,1));
  status.addView(battery,new LinearLayout.LayoutParams(dp(76),-1));
  root.addView(status,new android.widget.FrameLayout.LayoutParams(-1,dp(28),Gravity.TOP));
  root.setOnApplyWindowInsetsListener((v,insets)->{
   if(Build.VERSION.SDK_INT>=30){
    Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());
    Insets ime=insets.getInsets(WindowInsets.Type.ime());
    Insets cut=insets.getInsets(WindowInsets.Type.displayCutout());
    navBottom=nav.bottom;keyboardBottom=ime.bottom;safeLeft=Math.max(nav.left,cut.left);safeRight=Math.max(nav.right,cut.right);safeTop=cut.top;
   }else{
    navBottom=insets.getStableInsetBottom();keyboardBottom=insets.getSystemWindowInsetBottom();
    safeLeft=insets.getStableInsetLeft();safeRight=insets.getStableInsetRight();safeTop=0;
    if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
     DisplayCutout c=insets.getDisplayCutout();safeLeft=Math.max(safeLeft,c.getSafeInsetLeft());safeRight=Math.max(safeRight,c.getSafeInsetRight());safeTop=c.getSafeInsetTop();
    }
   }
   root.setPadding(0,0,0,0);applyGeometry();return insets;
  });
  if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
  else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  applyGeometry();root.requestApplyInsets();updateStatus();
 }
 private void applyGeometry(){
  if(status==null)return;
  FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,dp(28),Gravity.TOP);bar.setMargins(safeLeft,safeTop,safeRight,0);status.setLayoutParams(bar);
  FrameLayout.LayoutParams content=(FrameLayout.LayoutParams)stage.getLayoutParams();
  content.setMargins(safeLeft+dp(14),safeTop+dp(34),safeRight+dp(14),dp(100)+Math.max(navBottom,keyboardBottom));stage.setLayoutParams(content);
  FrameLayout.LayoutParams bottom=(FrameLayout.LayoutParams)dock.getLayoutParams();bottom.setMargins(safeLeft+dp(10),0,safeRight+dp(10),dp(14)+Math.max(navBottom,keyboardBottom));dock.setLayoutParams(bottom);
 }
 private void updateStatus(){
  if(status==null)return;
  smallTime.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
  Intent power=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  if(power!=null){int level=power.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=power.getIntExtra(BatteryManager.EXTRA_SCALE,100),state=power.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
   boolean charging=state==BatteryManager.BATTERY_STATUS_CHARGING;
   battery.setText(level>=0&&scale>0?(charging?"⚡ ":"")+(level*100/scale)+"%":"—");}
  String label="Нет сети";
  try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());
   if(cap!=null){if(cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))label="Wi-Fi";else if(cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))label="Моб. сеть";else if(cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))label="Ethernet";else if(cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN))label="VPN";else label="Сеть";
    if(!cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))label+=" • без интернета";}
  }catch(SecurityException e){label="Сеть недоступна";}
  connection.setText(label);
 }
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(statusTick);handler.post(statusTick);}
 @Override protected void onPause(){handler.removeCallbacks(statusTick);super.onPause();}
 @Override void launch(App app,View icon){
  try{lastLaunch=icon;launched=true;android.graphics.Rect rect=new android.graphics.Rect();icon.getGlobalVisibleRect(rect);
   Bundle options=motion?ActivityOptions.makeScaleUpAnimation(icon,0,0,icon.getWidth(),icon.getHeight()).toBundle():null;
   launcher.startMainActivity(app.info.getComponentName(),app.info.getUser(),rect,options);
  }catch(Exception e){launched=false;toast("Не удалось открыть приложение");}
 }
}
