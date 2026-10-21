package com.mourad.radiomaroc;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "radio_maroc_prefs";
    private static final String KEY_FAVORITES = "favorites", KEY_HISTORY = "history", KEY_DATA_SAVER = "data_saver", KEY_LAST_STATION = "last_station", KEY_ALARM_ENABLED = "alarm_enabled", KEY_ALARM_HOUR = "alarm_hour", KEY_ALARM_MINUTE = "alarm_minute", KEY_ALARM_STATION = "alarm_station";
    private final SharedPreferences sp;
    public Prefs(Context context) { sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public boolean isFavorite(String id) { return favorites().contains(id); }
    public Set<String> favorites() { return new HashSet<>(sp.getStringSet(KEY_FAVORITES, new HashSet<>())); }
    public void toggleFavorite(String id) { Set<String> s=favorites(); if(!s.add(id))s.remove(id); sp.edit().putStringSet(KEY_FAVORITES,s).apply(); }
    public void addHistory(String id) { if(id==null||id.isEmpty())return; List<String> items=historyIds(); items.remove(id); items.add(0,id); while(items.size()>8)items.remove(items.size()-1); sp.edit().putString(KEY_HISTORY,join(items)).apply(); }
    public List<String> historyIds() { String raw=sp.getString(KEY_HISTORY,""); List<String> r=new ArrayList<>(); if(raw==null||raw.isEmpty())return r; for(String id:raw.split("\\|")) if(!id.isEmpty()&&StationRepository.find(id)!=null)r.add(id); return r; }
    private static String join(List<String> items) { StringBuilder b=new StringBuilder(); for(String i:items){if(b.length()>0)b.append('|');b.append(i);}return b.toString(); }
    public boolean isDataSaver(){return sp.getBoolean(KEY_DATA_SAVER,false);} public void setDataSaver(boolean e){sp.edit().putBoolean(KEY_DATA_SAVER,e).apply();}
    public String lastStationId(){return sp.getString(KEY_LAST_STATION,"medi1");} public void setLastStationId(String id){sp.edit().putString(KEY_LAST_STATION,id).apply();}
    public void setAlarm(boolean e,int h,int m,String id){sp.edit().putBoolean(KEY_ALARM_ENABLED,e).putInt(KEY_ALARM_HOUR,h).putInt(KEY_ALARM_MINUTE,m).putString(KEY_ALARM_STATION,id).apply();}
    public boolean isAlarmEnabled(){return sp.getBoolean(KEY_ALARM_ENABLED,false);} public int alarmHour(){return sp.getInt(KEY_ALARM_HOUR,7);} public int alarmMinute(){return sp.getInt(KEY_ALARM_MINUTE,0);} public String alarmStationId(){return sp.getString(KEY_ALARM_STATION,lastStationId());}
}
