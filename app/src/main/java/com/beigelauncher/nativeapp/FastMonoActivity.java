package com.beigelauncher.nativeapp;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Cached screens, visible-row icon updates, continuous short transitions. */
public class FastMonoActivity extends Activity {
 private FrameLayout root,host;
 private SharedPreferences prefs;
 private LauncherApps launcher;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService catalogWorker=Executors.newSingleThreadExecutor();
 private final ExecutorService iconWorker=Executors.newFixedThreadPool(2);
 private final HashMap<String,View> pages=new HashMap<>();
 private final ArrayList<App> apps=new ArrayList<>(),visible=new ArrayList<>();
 private final Set<String> queued=ConcurrentHashMap.newKeySet(),failed=ConcurrentHashMap.newKeySet();
 private final ConcurrentHashMap<String,Integer> versions=new ConcurrentHashMap<>();
 private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(6*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
 private final LinearInterpolator linear=new LinearInterpolator();
 private final Typeface normal=Typeface.create("sans-serif",Typeface.NORMAL);
 private volatile boolean dead=false;
 private boolean motion=true,light=false,registered=false;
 private int speed=100,selected=0,request=0;
 private String current="",query="",savedNumber="";
 private View active;
 private ListView list;
 private AppsAdapter adapter;
 private EditText number,search;
 private final ArrayList<View> dots=new ArrayList<>();
 private final Runnable reload=()->loadApps();
 private final Runnable searchFilter=()->filter();
 static class App {
  final String name,key,lower;final LauncherActivityInfo info;
  App(LauncherActivityInfo i){info=i;name=i.getLabel().toString();key=i.getComponentName().flattenToString();lower=name.toLowerCase(Locale.getDefault());}
 }
 static class Holder {ImageView icon;TextView label;String key="";Bitmap bitmap;}
 private final LauncherApps.Callback changes=new LauncherApps.Callback(){
  public void onPackageAdded(String p,UserHandle u){changed(p);}
  public void onPackageRemoved(String p,UserHandle u){changed(p);}
  public void onPackageChanged(String p,UserHandle u){changed(p);}
  public void onPackagesAvailable(String[] p,UserHandle u,boolean r){for(String k:p)changed(k);}
  public void onPackagesUnavailable(String[] p,UserHandle u,boolean r){for(String k:p)changed(k);}
 };
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private int fg(){return light?0xff101010:0xfff0f0eb;}
 private int bg(){return light?0xffe7e7e1:0xff080808;}
 private int muted(){return light?0xff66665f:0xff8d8d87;}
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(fg());t.setTextSize(size);t.setTypeface(normal);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 private TextView action(String s,int size,Runnable run){TextView t=text(s,size);t.setGravity(Gravity.CENTER);t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->run.run());return t;}
 @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("mna-mono",MODE_PRIVATE);motion=prefs.getBoolean("motion",true);light=prefs.getBoolean("light",false);speed=prefs.getInt("speed",100);launcher=(LauncherApps)getSystemService(LAUNCHER_APPS_SERVICE);
  root=new FrameLayout(this);setContentView(root);host=new FrameLayout(this);root.addView(host,new FrameLayout.LayoutParams(-1,-1));
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{Insets n=insets.getInsets(WindowInsets.Type.navigationBars()),c=insets.getInsets(WindowInsets.Type.displayCutout()),k=insets.getInsets(WindowInsets.Type.ime());int l=Math.max(n.left,c.left),r=Math.max(n.right,c.right),b=Math.max(n.bottom,k.bottom);if(host.getPaddingLeft()!=l||host.getPaddingTop()!=c.top||host.getPaddingRight()!=r||host.getPaddingBottom()!=b)host.setPadding(l,c.top,r,b);return insets;});}
  else{root.setFitsSystemWindows(true);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);}
  if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
  theme();hideStatus();show("home",false);loadApps();try{launcher.registerCallback(changes,ui);registered=true;}catch(Exception ignored){}
 }
 private void theme(){root.setBackgroundColor(bg());getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.setSystemBarsAppearance(light?WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0,WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}}
 private void hideStatus(){if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.statusBars());}}}
 private void save(){prefs.edit().putBoolean("motion",motion).putBoolean("light",light).putInt("speed",speed).apply();}
 private void show(String target,boolean animate){
  if(target.equals(current)&&active!=null){if(target.equals("home"))updateDots();return;}
  View next=pages.get(target);if(next==null){next=makePage(target);pages.put(target,next);host.addView(next,new FrameLayout.LayoutParams(-1,-1));}
  if(active!=null){active.animate().cancel();active.setTranslationY(0);active.setVisibility(View.GONE);active.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
  next.animate().cancel();next.setVisibility(View.VISIBLE);next.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);next.setAlpha(1);next.setTranslationY(0);active=next;current=target;
  if(target.equals("home"))updateDots();
  if(target.equals("apps"))next.post(()->updateVisibleIcons());
  if(animate&&motion){next.setTranslationY(dp(4));next.animate().translationY(0).setDuration(speed).setInterpolator(linear).start();}
 }
 private View makePage(String name){if(name.equals("home"))return home();if(name.equals("apps"))return appsPage();if(name.equals("settings"))return settingsPage();return dialPage();}
 private View home(){FrameLayout wrap=new FrameLayout(this);LinearLayout menu=new LinearLayout(this);menu.setOrientation(1);menu.setPadding(dp(16),0,dp(16),0);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(46),dp(310)),-2,Gravity.CENTER);wrap.addView(menu,mp);dots.clear();String[] labels={"Приложения","Настройки","Звонок","Основной лаунчер"};
  for(int i=0;i<4;i++){final int n=i;FrameLayout row=new FrameLayout(this);menu.addView(row,new LinearLayout.LayoutParams(-1,dp(57)));TextView b=action(labels[i],26,()->{selected=n;updateDots();if(n==0)show("apps",true);if(n==1)show("settings",true);if(n==2)show("dial",true);if(n==3)defaultLauncher();});b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);row.addView(b,new FrameLayout.LayoutParams(-1,-1));View dot=new View(this);android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(fg());dot.setBackground(circle);row.addView(dot,new FrameLayout.LayoutParams(dp(5),dp(5),Gravity.RIGHT|Gravity.CENTER_VERTICAL));dots.add(dot);}updateDots();return wrap;}
 private void updateDots(){for(int i=0;i<dots.size();i++)dots.get(i).setVisibility(i==selected?View.VISIBLE:View.INVISIBLE);}
 private LinearLayout column(String title){LinearLayout col=new LinearLayout(this);col.setOrientation(1);col.setPadding(dp(26),dp(25),dp(26),dp(25));LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(title,23),new LinearLayout.LayoutParams(0,dp(48),1));header.addView(action("←",25,()->show("home",true)),new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(22);col.addView(header,p);return col;}
 private void changed(String pkg){versions.merge(pkg,1,Integer::sum);for(String key:icons.snapshot().keySet())if(key.startsWith(pkg+"/"))icons.remove(key);failed.removeIf(k->k.startsWith(pkg+"/"));ui.removeCallbacks(reload);ui.postDelayed(reload,180);}
 private void loadApps(){if(dead)return;final int token=++request;catalogWorker.execute(()->{ArrayList<App> loaded=new ArrayList<>();try{for(LauncherActivityInfo a:launcher.getActivityList(null,android.os.Process.myUserHandle()))if(!a.getComponentName().getPackageName().equals(getPackageName()))loaded.add(new App(a));Collections.sort(loaded,(a,b)->a.name.compareToIgnoreCase(b.name));}catch(Exception e){return;}ui.post(()->{if(dead||token!=request)return;apps.clear();apps.addAll(loaded);filter();});});}
 private void filter(){String q=query.toLowerCase(Locale.getDefault());ArrayList<App> next=new ArrayList<>();for(App a:apps)if(a.lower.contains(q))next.add(a);boolean same=next.size()==visible.size();if(same)for(int i=0;i<next.size();i++)if(!next.get(i).key.equals(visible.get(i).key)||!next.get(i).name.equals(visible.get(i).name)){same=false;break;}visible.clear();visible.addAll(next);if(adapter!=null&&!same)adapter.notifyDataSetChanged();else updateVisibleIcons();}
 private void requestIcon(App app){if(dead||icons.get(app.key)!=null||failed.contains(app.key)||!queued.add(app.key))return;final String pkg=app.info.getComponentName().getPackageName();final int version=versions.getOrDefault(pkg,0);
  iconWorker.execute(()->{Bitmap result=null;try{Drawable d=app.info.getBadgedIcon(0);int size=dp(40);result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);d.setBounds(0,0,size,size);d.draw(c);}catch(Exception ignored){}
   final Bitmap ready=result;ui.post(()->{queued.remove(app.key);if(dead)return;if(version!=versions.getOrDefault(pkg,0)){if(current.equals("apps"))updateVisibleIcons();return;}if(ready!=null)icons.put(app.key,ready);else failed.add(app.key);if(current.equals("apps"))updateVisibleIcons();});
  });
 }
 private void updateVisibleIcons(){if(list==null||!current.equals("apps"))return;for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);Object tag=row.getTag();if(!(tag instanceof Holder))continue;Holder h=(Holder)tag;Bitmap b=icons.get(h.key);if(b!=null&&b!=h.bitmap){h.bitmap=b;h.icon.setImageBitmap(b);}else if(b==null){int position=list.getFirstVisiblePosition()+i;if(position>=0&&position<visible.size())requestIcon(visible.get(position));}}}
 private View appsPage(){LinearLayout col=column("Приложения");search=new EditText(this);search.setTextColor(fg());search.setHintTextColor(muted());search.setTextSize(18);search.setSingleLine(true);search.setHint("Поиск");search.setText(query);col.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);list.setScrollingCacheEnabled(false);adapter=new AppsAdapter();list.setAdapter(adapter);col.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setOnItemClickListener((parent,v,p,id)->{if(p<visible.size())open(visible.get(p));});search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();ui.removeCallbacks(searchFilter);ui.postDelayed(searchFilter,24);}public void afterTextChanged(Editable e){}});return col;}
 private class AppsAdapter extends BaseAdapter {
  public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
  public View getView(int p,View recycled,android.view.ViewGroup parent){LinearLayout row;Holder h;
   if(recycled==null){row=new LinearLayout(FastMonoActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));h=new Holder();h.icon=new ImageView(FastMonoActivity.this);row.addView(h.icon,new LinearLayout.LayoutParams(dp(37),dp(37)));h.label=text("",22);h.label.setSingleLine(true);h.label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(45),1);lp.leftMargin=dp(17);row.addView(h.label,lp);row.setTag(h);}else{row=(LinearLayout)recycled;h=(Holder)row.getTag();}
   App a=visible.get(p);if(!h.key.equals(a.key)||!h.label.getText().toString().equals(a.name)){h.key=a.key;h.label.setText(a.name);row.setContentDescription(a.name);h.bitmap=null;h.icon.setImageDrawable(null);}Bitmap b=icons.get(a.key);if(h.bitmap!=b){h.bitmap=b;h.icon.setImageBitmap(b);}if(b==null)requestIcon(a);return row;
  }
 }
 private void open(App app){try{launcher.startMainActivity(app.info.getComponentName(),app.info.getUser(),null,null);}catch(Exception e){toast("Не удалось открыть приложение");}}
 private void settingRow(LinearLayout col,String title,TextView control){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(title,18),new LinearLayout.LayoutParams(0,dp(70),1));row.addView(control,new LinearLayout.LayoutParams(-2,dp(48)));col.addView(row);}
 private View settingsPage(){LinearLayout col=column("Настройки");TextView animation=action(motion?"Вкл":"Выкл",14,()->{});animation.setOnClickListener(v->{motion=!motion;animation.setText(motion?"Вкл":"Выкл");save();});settingRow(col,"Анимации",animation);TextView duration=action(speedName(),14,()->{});duration.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Скорость").setItems(new String[]{"Быстрая","Обычная","Медленная"},(d,n)->{speed=new int[]{100,180,260}[n];duration.setText(speedName());save();}).show());settingRow(col,"Скорость",duration);settingRow(col,"Светлый фон",action(light?"Вкл":"Выкл",14,()->{if(number!=null)savedNumber=number.getText().toString();light=!light;save();theme();for(View p:pages.values())p.animate().cancel();pages.clear();host.removeAllViews();active=null;list=null;adapter=null;search=null;number=null;current="";show("settings",false);}));TextView about=text("MnaLauncher · Mono",12);about.setTextColor(muted());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(32);col.addView(about,p);return col;}
 private String speedName(){return speed==100?"Быстрая":speed==180?"Обычная":"Медленная";}
 private View dialPage(){LinearLayout col=column("Звонок");number=new EditText(this);number.setInputType(InputType.TYPE_CLASS_PHONE);number.setTextColor(fg());number.setHintTextColor(muted());number.setTextSize(30);number.setGravity(Gravity.CENTER);number.setSingleLine(true);number.setHint("Номер");number.setText(savedNumber);col.addView(number,new LinearLayout.LayoutParams(-1,dp(76)));GridLayout keys=new GridLayout(this);keys.setColumnCount(3);col.addView(keys,new LinearLayout.LayoutParams(-1,-2));for(String key:new String[]{"1","2","3","4","5","6","7","8","9","*","0","#"}){TextView b=action(key,28,()->{int cursor=Math.max(0,number.getSelectionStart());number.getText().insert(cursor,key);});GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(54);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setGravity(Gravity.FILL_HORIZONTAL);keys.addView(b,p);}LinearLayout buttons=new LinearLayout(this);col.addView(buttons,new LinearLayout.LayoutParams(-1,dp(70)));buttons.addView(action("⌫",23,()->{int cursor=Math.max(0,number.getSelectionStart());if(cursor>0)number.getText().delete(cursor-1,cursor);}),new LinearLayout.LayoutParams(dp(50),-1));buttons.addView(action("Позвонить",21,()->{String n=number.getText().toString().replaceAll("[^0-9+*#]","");if(n.isEmpty()){toast("Введи номер");return;}try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",n,null)));}catch(Exception e){toast("Нет приложения для звонков");}}),new LinearLayout.LayoutParams(0,-1,1));return col;}
 private void defaultLauncher(){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){toast("Не удалось открыть выбор лаунчера");}}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 @Override protected void onResume(){super.onResume();hideStatus();}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)hideStatus();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);show("home",true);}
 @Override public void onBackPressed(){if(!current.equals("home"))show("home",true);}
 @Override protected void onDestroy(){dead=true;if(registered)launcher.unregisterCallback(changes);ui.removeCallbacksAndMessages(null);catalogWorker.shutdownNow();iconWorker.shutdownNow();icons.evictAll();super.onDestroy();}
}
