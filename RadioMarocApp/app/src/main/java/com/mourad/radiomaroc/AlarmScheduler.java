package com.mourad.radiomaroc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

public final class AlarmScheduler {
    private static final int REQUEST_CODE=7401; private AlarmScheduler(){}
    public static boolean canScheduleExact(Context c){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);return Build.VERSION.SDK_INT<Build.VERSION_CODES.S||a.canScheduleExactAlarms();}
    public static boolean scheduleNext(Context c,String id,int h,int m){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S&&!a.canScheduleExactAlarms())return false;Calendar now=Calendar.getInstance(),when=Calendar.getInstance();when.set(Calendar.HOUR_OF_DAY,h);when.set(Calendar.MINUTE,m);when.set(Calendar.SECOND,0);when.set(Calendar.MILLISECOND,0);if(!when.after(now))when.add(Calendar.DAY_OF_YEAR,1);a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when.getTimeInMillis(),pi(c,id));return true;}
    public static void cancel(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi(c,""));}
    private static PendingIntent pi(Context c,String id){Intent i=new Intent(c,AlarmReceiver.class);i.setAction("com.mourad.radiomaroc.RADIO_ALARM");i.putExtra(RadioService.EXTRA_STATION_ID,id);return PendingIntent.getBroadcast(c,REQUEST_CODE,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
