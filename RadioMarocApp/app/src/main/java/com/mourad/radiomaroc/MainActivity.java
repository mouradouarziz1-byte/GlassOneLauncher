package com.mourad.radiomaroc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(13,15,18),CARD=Color.rgb(28,31,36),CARD_ALT=Color.rgb(35,39,45),TEXT_DIM=Color.rgb(165,171,181),GREEN=Color.rgb(0,122,68),RED=Color.rgb(194,30,58);
    private Prefs prefs; private LinearLayout stationContainer,categoryContainer; private EditText searchBox; private TextView nowTitle,nowMeta,nowState,playerBadge; private Button sleepButton,alarmButton; private Switch dataSaverSwitch;
    private String selectedFilter="Tutte",currentStationId; private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(!RadioService.ACTION_STATE.equals(i.getAction()))return;currentStationId=i.getStringExtra(RadioService.EXTRA_STATION_ID);updateNowPlaying(currentStationId,i.getStringExtra(RadioService.EXTRA_STATE),i.getStringExtra(RadioService.EXTRA_METADATA));updateSleepButton(i.getLongExtra(RadioService.EXTRA_SLEEP_DEADLINE,0));}};

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=new Prefs(this);currentStationId=prefs.lastStationId();requestNotificationPermission();setContentView(buildUi());updateNowPlaying(currentStationId,"Pronta","");renderStations();updateAlarmButton();}
    @Override protected void onStart(){super.onStart();IntentFilter f=new IntentFilter(RadioService.ACTION_STATE);if(Build.VERSION.SDK_INT>=33)registerReceiver(stateReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(stateReceiver,f);receiverRegistered=true;}
    @Override protected void onResume(){super.onResume();if(prefs!=null&&prefs.isAlarmEnabled()&&AlarmScheduler.canScheduleExact(this))AlarmScheduler.scheduleNext(this,prefs.alarmStationId(),prefs.alarmHour(),prefs.alarmMinute());if(alarmButton!=null)updateAlarmButton();}
    @Override protected void onStop(){if(receiverRegistered){unregisterReceiver(stateReceiver);receiverRegistered=false;}super.onStop();}

    private View buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(22),dp(18),dp(36));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        root.addView(text("🇲🇦 Radio Maroc",30,Color.WHITE,true));TextView sub=text("راديو المغرب  •  Tutto il Marocco in diretta",15,TEXT_DIM,false);sub.setPadding(0,dp(3),0,dp(18));root.addView(sub);root.addView(buildPlayerCard());
        searchBox=new EditText(this);searchBox.setHint("Cerca stazione, città o categoria…");searchBox.setHintTextColor(Color.rgb(120,127,138));searchBox.setTextColor(Color.WHITE);searchBox.setTextSize(16);searchBox.setSingleLine(true);searchBox.setPadding(dp(16),0,dp(16),0);searchBox.setBackground(rounded(CARD,18));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));slp.setMargins(0,dp(18),0,dp(12));root.addView(searchBox,slp);searchBox.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){renderStations();}public void afterTextChanged(Editable e){}});
        HorizontalScrollView cats=new HorizontalScrollView(this);cats.setHorizontalScrollBarEnabled(false);categoryContainer=new LinearLayout(this);categoryContainer.setOrientation(LinearLayout.HORIZONTAL);cats.addView(categoryContainer,new HorizontalScrollView.LayoutParams(-2,-2));root.addView(cats);renderCategories();
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);tools.setPadding(dp(14),dp(12),dp(14),dp(12));tools.setBackground(rounded(CARD,18));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.setMargins(0,dp(14),0,dp(18));root.addView(tools,tlp);tools.addView(text("Strumenti",15,Color.WHITE,true));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(9),0,dp(4));tools.addView(row);
        sleepButton=smallButton("🌙 Sleep timer");sleepButton.setOnClickListener(v->showSleepTimer());row.addView(sleepButton,weighted());alarmButton=smallButton("⏰ Sveglia");alarmButton.setOnClickListener(v->showAlarmOptions());LinearLayout.LayoutParams alp=weighted();alp.setMargins(dp(8),0,0,0);row.addView(alarmButton,alp);
        dataSaverSwitch=new Switch(this);dataSaverSwitch.setText("Risparmio dati — usa stream più leggeri quando disponibili");dataSaverSwitch.setTextColor(TEXT_DIM);dataSaverSwitch.setTextSize(13);dataSaverSwitch.setChecked(prefs.isDataSaver());dataSaverSwitch.setPadding(0,dp(6),0,0);dataSaverSwitch.setOnCheckedChangeListener((v,on)->{prefs.setDataSaver(on);sendServiceAction(RadioService.ACTION_RELOAD,null,0);Toast.makeText(this,on?"Risparmio dati attivo":"Qualità normale",Toast.LENGTH_SHORT).show();});tools.addView(dataSaverSwitch);
        TextView section=text("Stazioni",21,Color.WHITE,true);section.setPadding(0,0,0,dp(10));root.addView(section);stationContainer=new LinearLayout(this);stationContainer.setOrientation(LinearLayout.VERTICAL);root.addView(stationContainer,new LinearLayout.LayoutParams(-1,-2));TextView foot=text("Streaming via Internet • Gli stream appartengono alle rispettive emittenti",11,Color.rgb(115,121,130),false);foot.setGravity(Gravity.CENTER);foot.setPadding(dp(8),dp(20),dp(8),0);root.addView(foot);return scroll;
    }

    private View buildPlayerCard(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(14));card.setBackground(rounded(CARD_ALT,22));LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);card.addView(top);playerBadge=text("M1",16,Color.WHITE,true);playerBadge.setGravity(Gravity.CENTER);playerBadge.setBackground(rounded(GREEN,18));top.addView(playerBadge,new LinearLayout.LayoutParams(dp(56),dp(56)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(14),0,0,0);top.addView(info,new LinearLayout.LayoutParams(0,-2,1));nowState=text("● LIVE",12,Color.rgb(245,83,91),true);info.addView(nowState);nowTitle=text("Medi 1 Radio",20,Color.WHITE,true);nowTitle.setPadding(0,dp(2),0,0);info.addView(nowTitle);nowMeta=text("Generalista • Tanger",12,TEXT_DIM,false);nowMeta.setPadding(0,dp(2),0,0);info.addView(nowMeta);LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER);controls.setPadding(0,dp(14),0,0);card.addView(controls);Button prev=playerButton("⏮");prev.setOnClickListener(v->sendServiceAction(RadioService.ACTION_PREV,null,0));controls.addView(prev,weighted());Button pp=playerButton("⏯  Play / Pausa");pp.setOnClickListener(v->sendServiceAction(RadioService.ACTION_TOGGLE,null,0));LinearLayout.LayoutParams pplp=new LinearLayout.LayoutParams(0,dp(48),2);pplp.setMargins(dp(8),0,dp(8),0);controls.addView(pp,pplp);Button next=playerButton("⏭");next.setOnClickListener(v->sendServiceAction(RadioService.ACTION_NEXT,null,0));controls.addView(next,weighted());return card;}

    private void renderCategories(){if(categoryContainer==null)return;categoryContainer.removeAllViews();String[] f={"Tutte","Preferiti","Recenti",StationRepository.CAT_MUSIC,StationRepository.CAT_NEWS,StationRepository.CAT_SPORT,StationRepository.CAT_RELIGIOUS,StationRepository.CAT_AMAZIGH,StationRepository.CAT_REGIONAL,StationRepository.CAT_GENERAL};for(String x:f){Button b=smallButton(x);boolean sel=x.equals(selectedFilter);b.setTextColor(sel?Color.WHITE:TEXT_DIM);b.setBackground(rounded(sel?GREEN:CARD,16));b.setOnClickListener(v->{selectedFilter=x;renderCategories();renderStations();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.setMargins(0,0,dp(8),0);categoryContainer.addView(b,lp);}}
    private void renderStations(){if(stationContainer==null)return;stationContainer.removeAllViews();List<Station> list=filteredStations();if(list.isEmpty()){TextView e=text("Nessuna stazione trovata.",15,TEXT_DIM,false);e.setGravity(Gravity.CENTER);e.setPadding(0,dp(26),0,dp(26));stationContainer.addView(e);return;}for(Station s:list)stationContainer.addView(stationCard(s));}
    private List<Station> filteredStations(){List<Station>b=new ArrayList<>();if("Preferiti".equals(selectedFilter)){Set<String>f=prefs.favorites();for(Station s:StationRepository.all())if(f.contains(s.id))b.add(s);}else if("Recenti".equals(selectedFilter)){for(String id:prefs.historyIds()){Station s=StationRepository.find(id);if(s!=null)b.add(s);}}else if("Tutte".equals(selectedFilter))b.addAll(StationRepository.all());else b.addAll(StationRepository.byCategory(selectedFilter));String q=searchBox==null?"":searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return b;List<Station>r=new ArrayList<>();for(Station s:b){String h=(s.name+" "+s.city+" "+s.category+" "+s.description).toLowerCase(Locale.ROOT);if(h.contains(q))r.add(s);}return r;}
    private View stationCard(Station s){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(12),dp(11),dp(10),dp(11));c.setBackground(rounded(CARD,18));c.setClickable(true);c.setFocusable(true);c.setOnClickListener(v->playStation(s));TextView badge=text(s.badge,s.badge.length()>2?12:15,Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setBackground(rounded(categoryColor(s.category),16));c.addView(badge,new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(13),0,dp(5),0);c.addView(info,new LinearLayout.LayoutParams(0,-2,1));info.addView(text(s.name,17,Color.WHITE,true));TextView m=text(s.subtitle(),12,TEXT_DIM,false);m.setPadding(0,dp(2),0,0);info.addView(m);TextView d=text(s.description,11,Color.rgb(130,137,147),false);d.setPadding(0,dp(2),0,0);info.addView(d);Button fav=new Button(this);fav.setText(prefs.isFavorite(s.id)?"★":"☆");fav.setTextColor(prefs.isFavorite(s.id)?Color.rgb(255,204,70):TEXT_DIM);fav.setTextSize(23);fav.setMinWidth(0);fav.setMinHeight(0);fav.setPadding(0,0,0,0);fav.setBackgroundColor(Color.TRANSPARENT);fav.setOnClickListener(v->{prefs.toggleFavorite(s.id);renderStations();});c.addView(fav,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));c.setLayoutParams(lp);return c;}

    private void playStation(Station s){currentStationId=s.id;prefs.setLastStationId(s.id);prefs.addHistory(s.id);updateNowPlaying(s.id,"Connessione…","");sendServiceAction(RadioService.ACTION_PLAY_STATION,s.id,0);if("Recenti".equals(selectedFilter))renderStations();}
    private void updateNowPlaying(String id,String state,String meta){Station s=StationRepository.find(id);if(s==null)s=StationRepository.find(prefs.lastStationId());if(s==null)return;currentStationId=s.id;if(playerBadge!=null){playerBadge.setText(s.badge);playerBadge.setBackground(rounded(categoryColor(s.category),18));}if(nowTitle!=null)nowTitle.setText(s.name);if(nowState!=null){String x=state==null?"Pronta":state;nowState.setText("LIVE".equals(x)?"● LIVE":x);nowState.setTextColor("LIVE".equals(x)?Color.rgb(245,83,91):TEXT_DIM);}if(nowMeta!=null)nowMeta.setText(meta!=null&&!meta.trim().isEmpty()?meta+"\n"+s.subtitle():s.subtitle());}
    private void showSleepTimer(){String[]o={"15 minuti","30 minuti","60 minuti","90 minuti","Disattiva"};int[]m={15,30,60,90,0};new AlertDialog.Builder(this).setTitle("Sleep timer").setItems(o,(d,w)->{sendServiceAction(RadioService.ACTION_SLEEP_TIMER,null,m[w]);sleepButton.setText(m[w]==0?"🌙 Sleep timer":"🌙 "+m[w]+" min");Toast.makeText(this,m[w]==0?"Sleep timer disattivato":"La radio si fermerà tra "+m[w]+" minuti",Toast.LENGTH_SHORT).show();}).show();}
    private void updateSleepButton(long d){if(sleepButton==null)return;if(d<=System.currentTimeMillis()){sleepButton.setText("🌙 Sleep timer");return;}long m=Math.max(1,(d-System.currentTimeMillis()+59999)/60000);sleepButton.setText("🌙 "+m+" min");}
    private void showAlarmOptions(){if(!prefs.isAlarmEnabled()){showTimePicker();return;}Station s=StationRepository.find(prefs.alarmStationId());String label=String.format(Locale.ITALY,"%02d:%02d • %s",prefs.alarmHour(),prefs.alarmMinute(),s==null?"Radio":s.name);new AlertDialog.Builder(this).setTitle("Sveglia radio").setMessage("Attiva ogni giorno: "+label).setPositiveButton("Modifica",(d,w)->showTimePicker()).setNegativeButton("Disattiva",(d,w)->disableAlarm()).setNeutralButton("Annulla",null).show();}
    private void showTimePicker(){int h=prefs.isAlarmEnabled()?prefs.alarmHour():7,m=prefs.isAlarmEnabled()?prefs.alarmMinute():0;TimePickerDialog d=new TimePickerDialog(this,(TimePicker v,int hh,int mm)->enableAlarm(hh,mm),h,m,true);d.setTitle("Avvia la radio ogni giorno alle…");d.show();}
    private void enableAlarm(int h,int m){String id=currentStationId;if(StationRepository.find(id)==null)id=prefs.lastStationId();if(StationRepository.find(id)==null)id="medi1";prefs.setAlarm(true,h,m,id);updateAlarmButton();if(!AlarmScheduler.canScheduleExact(this)){try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));Toast.makeText(this,"Consenti ‘Sveglie e promemoria’, poi torna nell’app",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Abilita le sveglie esatte nelle impostazioni Android",Toast.LENGTH_LONG).show();}return;}AlarmScheduler.scheduleNext(this,id,h,m);Station s=StationRepository.find(id);Toast.makeText(this,String.format(Locale.ITALY,"Sveglia %02d:%02d con %s",h,m,s==null?"Radio Maroc":s.name),Toast.LENGTH_LONG).show();}
    private void disableAlarm(){prefs.setAlarm(false,prefs.alarmHour(),prefs.alarmMinute(),prefs.alarmStationId());AlarmScheduler.cancel(this);updateAlarmButton();Toast.makeText(this,"Sveglia radio disattivata",Toast.LENGTH_SHORT).show();}
    private void updateAlarmButton(){if(alarmButton==null)return;alarmButton.setText(!prefs.isAlarmEnabled()?"⏰ Sveglia":String.format(Locale.ITALY,"⏰ %02d:%02d",prefs.alarmHour(),prefs.alarmMinute()));}
    private void sendServiceAction(String a,String id,int min){Intent i=new Intent(this,RadioService.class);i.setAction(a);if(id!=null)i.putExtra(RadioService.EXTRA_STATION_ID,id);if(RadioService.ACTION_SLEEP_TIMER.equals(a))i.putExtra(RadioService.EXTRA_MINUTES,min);startService(i);}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1001);}
    private TextView text(String v,float s,int c,boolean b){TextView t=new TextView(this);t.setText(v);t.setTextColor(c);t.setTextSize(s);if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button smallButton(String v){Button b=new Button(this);b.setText(v);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(rounded(CARD_ALT,14));return b;}
    private Button playerButton(String v){Button b=new Button(this);b.setText(v);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(0);b.setMinWidth(0);b.setBackground(rounded(GREEN,15));return b;}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,dp(44),1);}
    private int categoryColor(String c){if(StationRepository.CAT_SPORT.equals(c))return Color.rgb(28,112,205);if(StationRepository.CAT_NEWS.equals(c))return Color.rgb(111,79,170);if(StationRepository.CAT_RELIGIOUS.equals(c))return Color.rgb(12,118,92);if(StationRepository.CAT_AMAZIGH.equals(c))return Color.rgb(190,114,32);if(StationRepository.CAT_REGIONAL.equals(c))return Color.rgb(87,105,127);if(StationRepository.CAT_MUSIC.equals(c))return RED;return GREEN;}
    private GradientDrawable rounded(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
