package com.beigelauncher.nativeapp;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MnaActivity extends MainActivity {
 private boolean loaded=false,registered=false,dead=false;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(6*1024*1024){
  @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
 };
 private final Set<String> queued=ConcurrentHashMap.newKeySet();
 private AppsAdapter adapter;
 private final LauncherApps.Callback changes=new LauncherApps.Callback(){
  public void onPackageAdded(String p,UserHandle u){refreshApps();}
  public void onPackageRemoved(String p,UserHandle u){refreshApps();}
  public void onPackageChanged(String p,UserHandle u){refreshApps();}
  public void onPackagesAvailable(String[] p,UserHandle u,boolean r){refreshApps();}
  public void onPackagesUnavailable(String[] p,UserHandle u,boolean r){refreshApps();}
 };
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  try{launcher.registerCallback(changes,handler);registered=true;}catch(Exception ignored){}
  warmIcons();
 }
 @Override void loadApps(){if(!loaded){super.loadApps();loaded=true;}}
 private void refreshApps(){
  if(dead)return;
  worker.execute(()->{
   ArrayList<App> updated=new ArrayList<>();
   try{
    for(LauncherActivityInfo a:launcher.getActivityList(null,android.os.Process.myUserHandle()))
     if(!a.getComponentName().getPackageName().equals(getPackageName()))
      updated.add(new App(a.getLabel().toString(),a.getComponentName().flattenToString(),a));
    Collections.sort(updated,(a,b)->a.name.compareToIgnoreCase(b.name));
   }catch(Exception e){return;}
   handler.post(()->{if(dead)return;apps.clear();apps.addAll(updated);icons.evictAll();
    if(adapter!=null)adapter.filter(adapter.query);
    if(selectedTab==0)render();warmIcons();});
  });
 }
 private void warmIcons(){for(App a:new ArrayList<>(apps))requestIcon(a);}
 private void requestIcon(App a){
  if(dead||icons.get(a.key)!=null||!queued.add(a.key))return;
  worker.execute(()->{
   Bitmap bitmap=null;
   try{Drawable d=a.info.getBadgedIcon(0);int size=dp(64);
    bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
    Canvas c=new Canvas(bitmap);d.setBounds(0,0,size,size);d.draw(c);
   }catch(Exception ignored){}
   if(bitmap!=null)icons.put(a.key,bitmap);
   queued.remove(a.key);
   handler.post(()->{if(!dead&&adapter!=null)adapter.notifyDataSetChanged();});
  });
 }
 @Override void appsPage(FrameLayout target){
  LinearLayout column=new LinearLayout(this);column.setOrientation(1);
  column.setPadding(dp(4),dp(10),dp(4),0);
  target.addView(column,new FrameLayout.LayoutParams(-1,-1));
  column.addView(text("Все приложения",24,TEXT));
  EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Поиск приложений");
  search.setTextColor(TEXT);search.setHintTextColor(MUTED);column.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));
  GridView grid=new GridView(this);grid.setNumColumns(4);grid.setVerticalSpacing(dp(10));
  grid.setHorizontalSpacing(dp(4));grid.setClipToPadding(false);grid.setPadding(0,dp(12),0,dp(16));
  adapter=new AppsAdapter();grid.setAdapter(adapter);
  column.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
  column.addView(text("Удерживай приложение, чтобы добавить ярлык на стол.",12,MUTED));
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){adapter.filter(s.toString());}
   public void afterTextChanged(Editable e){}
  });
 }
 private class AppsAdapter extends BaseAdapter {
  String query="";final ArrayList<App> visible=new ArrayList<>();
  AppsAdapter(){filter("");}
  void filter(String q){query=q;visible.clear();String value=q.toLowerCase(Locale.getDefault());
   for(App a:apps)if(a.name.toLowerCase(Locale.getDefault()).contains(value))visible.add(a);
   notifyDataSetChanged();}
  public int getCount(){return visible.size();}
  public Object getItem(int position){return visible.get(position);}
  public long getItemId(int position){return position;}
  @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
   LinearLayout cell;ImageView icon;TextView label;
   if(recycled==null){cell=new LinearLayout(MnaActivity.this);cell.setOrientation(1);cell.setGravity(Gravity.CENTER);
    cell.setPadding(dp(2),dp(8),dp(2),dp(8));
    icon=new ImageView(MnaActivity.this);icon.setId(android.R.id.icon);
    icon.setPadding(dp(4),dp(4),dp(4),dp(4));cell.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
    label=text("",11,TEXT);label.setId(android.R.id.text1);label.setGravity(Gravity.CENTER);
    label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);
    cell.addView(label,new LinearLayout.LayoutParams(-1,dp(26)));
   }else{cell=(LinearLayout)recycled;icon=cell.findViewById(android.R.id.icon);label=cell.findViewById(android.R.id.text1);}
   App app=visible.get(position);icon.setBackground(shape(PANEL,round));
   Bitmap bitmap=icons.get(app.key);icon.setImageBitmap(bitmap);if(bitmap==null)requestIcon(app);
   label.setText(app.name);cell.setContentDescription(app.name);
   final ImageView launchIcon=icon;
   cell.setOnClickListener(v->launch(app,launchIcon));
   cell.setOnLongClickListener(v->{Item i=new Item();i.key=app.key;items.add(i);save();switchPage(0,true);toast("Ярлык добавлен");return true;});
   return cell;
  }
 }
 @Override void buildDock(){
  super.buildDock();String[] labels={"Рабочий стол","Настройки","Приложения","Кастомизация"};
  for(int n=0;n<4;n++){tabs[n].setText(labels[n]);tabs[n].setCompoundDrawablesWithIntrinsicBounds(null,new DockIcon(n),null,null);tabs[n].setCompoundDrawablePadding(dp(4));}
 }
 @Override void movePill(boolean animate){super.movePill(animate);
  for(TextView t:tabs)if(t!=null)for(Drawable d:t.getCompoundDrawables())if(d!=null)d.invalidateSelf();}
 private class DockIcon extends Drawable {
  final int kind;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  DockIcon(int k){kind=k;}
  @Override public int getIntrinsicWidth(){return dp(22);}
  @Override public int getIntrinsicHeight(){return dp(22);}
  @Override public void draw(Canvas canvas){int saved=canvas.save();canvas.translate(getBounds().left,getBounds().top);
   canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
   paint.setColor(kind==selectedTab?0xff211b15:MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);
   paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
   Path p=new Path();
   if(kind==0){p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.lineTo(21,21);p.lineTo(15,21);p.lineTo(15,15);p.lineTo(9,15);p.lineTo(9,21);p.lineTo(3,21);p.close();canvas.drawPath(p,paint);}
   if(kind==1){canvas.drawCircle(12,12,3,paint);canvas.drawCircle(12,12,7,paint);
    for(int n=0;n<8;n++){double a=n*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,paint);}}
   if(kind==2){for(int y=4;y<=16;y+=6)for(int x=4;x<=16;x+=6)canvas.drawRoundRect(x,y,x+3,y+3,1,1,paint);}
   if(kind==3){for(int n=0;n<3;n++){float x=5+n*7;canvas.drawLine(x,3,x,21,paint);canvas.drawCircle(x,n==1?15:8,2,paint);}}
   canvas.restoreToCount(saved);
  }
  public void setAlpha(int alpha){paint.setAlpha(alpha);}
  public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
  public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 @Override void launch(App app,View icon){
  try{lastLaunch=icon;launched=true;Rect bounds=new Rect();icon.getGlobalVisibleRect(bounds);
   Bundle options=motion?ActivityOptions.makeScaleUpAnimation(icon,0,0,icon.getWidth(),icon.getHeight()).toBundle():null;
   launcher.startMainActivity(app.info.getComponentName(),app.info.getUser(),bounds,options);
  }catch(Exception e){launched=false;toast("Не удалось открыть приложение");}
 }
 @Override protected void onDestroy(){dead=true;if(registered)launcher.unregisterCallback(changes);worker.shutdownNow();super.onDestroy();}
}
