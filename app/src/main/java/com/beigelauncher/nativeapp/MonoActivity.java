package com.beigelauncher.nativeapp;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Native implementation of the approved straight-type minimalist design. */
public class MonoActivity extends Activity {
 private FrameLayout root,content;
 private SharedPreferences prefs;
 private LauncherApps launcher;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ArrayList<App> apps=new ArrayList<>();
 private final Set<String> queued=ConcurrentHashMap.newKeySet();
 private final LruCache<String,Bitmap> icons=new LruCache<String,Bitmap>(6*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
 private boolean motion=true,light=false,dead=false,registered=false;
 private int speed=100;
 private String screen="home",query="";
 private BaseAdapter appAdapter;
 private EditText number;
 private final ArrayList<App> filtered=new ArrayList<>();
 private int selected=0;
 private final LauncherApps.Callback changes=new LauncherApps.Callback(){
  public void onPackageAdded(String p,UserHandle u){reloadApps();}
  public void onPackageRemoved(String p,UserHandle u){reloadApps();}
  public void onPackageChanged(String p,UserHandle u){reloadApps();}
  public void onPackagesAvailable(String[] p,UserHandle u,boolean r){reloadApps();}
  public void onPackagesUnavailable(String[] p,UserHandle u,boolean r){reloadApps();}
 };
 private final Runnable refreshIcons=()->{if(!dead&&appAdapter!=null)appAdapter.notifyDataSetChanged();};
 static class App {String name,key;LauncherActivityInfo info;App(LauncherActivityInfo i){info=i;name=i.getLabel().toString();key=i.getComponentName().flattenToString();}}
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private int foreground(){return light?0xff101010:0xfff0f0eb;}
 private int background(){return light?0xffe7e7e1:0xff080808;}
 private int muted(){return light?0xff66665f:0xff8d8d87;}
 private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(foreground());t.setTextSize(size);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
 private TextView action(String title,int size,Runnable run){TextView b=text(title,size);b.setGravity(Gravity.CENTER);b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->run.run());return b;}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);prefs=getSharedPreferences("mna-mono",MODE_PRIVATE);motion=prefs.getBoolean("motion",true);light=prefs.getBoolean("light",false);speed=prefs.getInt("speed",100);launcher=(LauncherApps)getSystemService(LAUNCHER_APPS_SERVICE);
  root=new FrameLayout(this);root.setBackgroundColor(background());setContentView(root);content=new FrameLayout(this);root.addView(content,new FrameLayout.LayoutParams(-1,-1));
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());Insets cut=insets.getInsets(WindowInsets.Type.displayCutout());Insets ime=insets.getInsets(WindowInsets.Type.ime());content.setPadding(Math.max(nav.left,cut.left),cut.top,Math.max(nav.right,cut.right),Math.max(nav.bottom,ime.bottom));return insets;});}
  else{root.setFitsSystemWindows(true);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);}
  if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
  colors();hideStatus();show("home",false);reloadApps();try{launcher.registerCallback(changes,main);registered=true;}catch(Exception ignored){}
 }
 private void colors(){root.setBackgroundColor(background());getWindow().setStatusBarColor(background());getWindow().setNavigationBarColor(background());if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.setSystemBarsAppearance(light?WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0,WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}}
 private void hideStatus(){if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.statusBars());}}}
 private void save(){prefs.edit().putBoolean("motion",motion).putBoolean("light",light).putInt("speed",speed).apply();}
 private void show(String target,boolean animate){screen=target;content.animate().cancel();content.setAlpha(1);content.setTranslationY(0);content.removeAllViews();appAdapter=null;
  if(target.equals("home"))home();else if(target.equals("apps"))appList();else if(target.equals("settings"))settings();else if(target.equals("dial"))dial();
  if(animate&&motion){content.setAlpha(.35f);content.setTranslationY(dp(6));content.animate().alpha(1).translationY(0).setDuration(speed).setInterpolator(v->v<.5f?0f:v<1f?.5f:1f).start();}
 }
 private void home(){
  FrameLayout wrap=new FrameLayout(this);content.addView(wrap,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout menu=new LinearLayout(this);menu.setOrientation(1);menu.setPadding(dp(16),0,dp(16),0);
  FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(46),dp(310)),-2,Gravity.CENTER);wrap.addView(menu,mp);
  String[] names={"Приложения","Настройки","Звонок","Основной лаунчер"};
  for(int i=0;i<4;i++){final int n=i;FrameLayout row=new FrameLayout(this);menu.addView(row,new LinearLayout.LayoutParams(-1,dp(57)));
   TextView label=action(names[i],26,()->{selected=n;if(n==0)show("apps",true);if(n==1)show("settings",true);if(n==2)show("dial",true);if(n==3)defaultLauncher();});label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);row.addView(label,new FrameLayout.LayoutParams(-1,-1));
   if(i==selected){View dot=new View(this);dot.setBackground(dotDrawable());FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(5),dp(5),Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(dot,p);}
  }
 }
 private android.graphics.drawable.Drawable dotDrawable(){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setShape(android.graphics.drawable.GradientDrawable.OVAL);d.setColor(foreground());return d;}
 private LinearLayout column(String title){LinearLayout col=new LinearLayout(this);col.setOrientation(1);col.setPadding(dp(26),dp(25),dp(26),dp(25));content.addView(col,new FrameLayout.LayoutParams(-1,-1));LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView h=text(title,23);header.addView(h,new LinearLayout.LayoutParams(0,dp(48),1));header.addView(action("←",25,()->show("home",true)),new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(48));hp.bottomMargin=dp(22);col.addView(header,hp);return col;}
 private void reloadApps(){if(dead)return;worker.execute(()->{ArrayList<App> loaded=new ArrayList<>();try{for(LauncherActivityInfo i:launcher.getActivityList(null,android.os.Process.myUserHandle()))if(!i.getComponentName().getPackageName().equals(getPackageName()))loaded.add(new App(i));Collections.sort(loaded,(a,b)->a.name.compareToIgnoreCase(b.name));}catch(Exception ignored){}
   main.post(()->{if(dead)return;apps.clear();apps.addAll(loaded);icons.evictAll();filter();});});}
 private void filter(){filtered.clear();String q=query.toLowerCase(Locale.getDefault());for(App a:apps)if(a.name.toLowerCase(Locale.getDefault()).contains(q))filtered.add(a);if(appAdapter!=null)appAdapter.notifyDataSetChanged();}
 private void icon(App app){if(dead||icons.get(app.key)!=null||!queued.add(app.key))return;worker.execute(()->{try{android.graphics.drawable.Drawable d=app.info.getBadgedIcon(0);Bitmap b=Bitmap.createBitmap(dp(48),dp(48),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);d.setBounds(0,0,b.getWidth(),b.getHeight());d.draw(c);icons.put(app.key,b);}catch(Exception ignored){}queued.remove(app.key);main.removeCallbacks(refreshIcons);main.postDelayed(refreshIcons,24);});}
 private void appList(){LinearLayout col=column("Приложения");EditText search=new EditText(this);search.setTextColor(foreground());search.setHintTextColor(muted());search.setTextSize(18);search.setSingleLine(true);search.setHint("Поиск");search.setText(query);col.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));ListView list=new ListView(this);list.setDivider(null);list.setSelector(android.R.color.transparent);col.addView(list,new LinearLayout.LayoutParams(-1,0,1));
  appAdapter=new BaseAdapter(){public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}
   public View getView(int p,View recycled,android.view.ViewGroup parent){LinearLayout row;ImageView image;TextView label;
    if(recycled==null){row=new LinearLayout(MonoActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));image=new ImageView(MonoActivity.this);image.setId(android.R.id.icon);row.addView(image,new LinearLayout.LayoutParams(dp(37),dp(37)));label=text("",22);label.setId(android.R.id.text1);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(45),1);lp.leftMargin=dp(17);label.setGravity(Gravity.CENTER_VERTICAL);row.addView(label,lp);}else{row=(LinearLayout)recycled;image=row.findViewById(android.R.id.icon);label=row.findViewById(android.R.id.text1);}
    App app=filtered.get(p);label.setText(app.name);Bitmap bitmap=icons.get(app.key);image.setImageBitmap(bitmap);if(bitmap==null)icon(app);row.setContentDescription(app.name);return row;}
  };list.setAdapter(appAdapter);filter();list.setOnItemClickListener((parent,v,pos,id)->{if(pos<filtered.size())open(filtered.get(pos));});search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();filter();}public void afterTextChanged(Editable e){}});
 }
 private void open(App app){try{launcher.startMainActivity(app.info.getComponentName(),app.info.getUser(),null,null);}catch(Exception e){toast("Не удалось открыть приложение");}}
 private void row(LinearLayout col,String title,TextView control){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(text(title,18),new LinearLayout.LayoutParams(0,dp(70),1));r.addView(control,new LinearLayout.LayoutParams(-2,dp(48)));col.addView(r);}
 private void settings(){LinearLayout col=column("Настройки");row(col,"Анимации",action(motion?"Вкл":"Выкл",14,()->{motion=!motion;save();show("settings",false);}));row(col,"Скорость",action(speed==100?"Быстрая":speed==180?"Обычная":"Медленная",14,()->new AlertDialog.Builder(this).setTitle("Скорость").setItems(new String[]{"Быстрая","Обычная","Медленная"},(d,n)->{speed=new int[]{100,180,260}[n];save();show("settings",false);}).show()));row(col,"Светлый фон",action(light?"Вкл":"Выкл",14,()->{light=!light;save();colors();show("settings",false);}));TextView about=text("MnaLauncher · Mono",12);about.setTextColor(muted());LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(-1,-2);a.topMargin=dp(32);col.addView(about,a);}
 private void dial(){LinearLayout col=column("Звонок");number=new EditText(this);number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);number.setTextColor(foreground());number.setHintTextColor(muted());number.setTextSize(30);number.setGravity(Gravity.CENTER);number.setSingleLine(true);number.setHint("Номер");col.addView(number,new LinearLayout.LayoutParams(-1,dp(76)));GridLayout keys=new GridLayout(this);keys.setColumnCount(3);col.addView(keys,new LinearLayout.LayoutParams(-1,-2));for(String key:new String[]{"1","2","3","4","5","6","7","8","9","*","0","#"}){TextView b=action(key,28,()->{int start=Math.max(0,number.getSelectionStart());number.getText().insert(start,key);});GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(54);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setGravity(Gravity.FILL_HORIZONTAL);keys.addView(b,p);}
  LinearLayout actions=new LinearLayout(this);col.addView(actions,new LinearLayout.LayoutParams(-1,dp(70)));actions.addView(action("⌫",23,()->{int cursor=Math.max(0,number.getSelectionStart());if(cursor>0)number.getText().delete(cursor-1,cursor);}),new LinearLayout.LayoutParams(dp(50),-1));actions.addView(action("Позвонить",21,()->{String n=number.getText().toString().replaceAll("[^0-9+*#]","");if(n.isEmpty()){toast("Введи номер");return;}try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",n,null)));}catch(Exception e){toast("Нет приложения для звонков");}}),new LinearLayout.LayoutParams(0,-1,1));
 }
 private void defaultLauncher(){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){toast("Не удалось открыть выбор основного лаунчера");}}
 private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
 @Override protected void onResume(){super.onResume();hideStatus();}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideStatus();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);show("home",true);}
 @Override public void onBackPressed(){if(!screen.equals("home"))show("home",true);}
 @Override protected void onDestroy(){dead=true;if(registered)launcher.unregisterCallback(changes);main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
