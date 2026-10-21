package com.mourad.radiomaroc;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;
public class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c,Intent i){Prefs p=new Prefs(c);if(p.isAlarmEnabled())AlarmScheduler.scheduleNext(c,p.alarmStationId(),p.alarmHour(),p.alarmMinute());}}
