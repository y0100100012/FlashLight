package cn.hzcec.www.util;

import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/*
Android 系统若要设置系统时间，需要获取root权限，本app放弃直接设置系统时间，
改用记录偏差值的方法来处理时间
 */
public class LocalTime {
    private long deltaTime;
    private SharedPreferences preferences;
    public LocalTime(SharedPreferences preferences){
        this.preferences=preferences;
    }
    /*
    * 方法名：getTime()
    * 功  能：获取设备时间
    * 参  数：无
    * 返回值：String - 设备时间，格式为年月日时分秒yyyyMMddHHmmss
    */
    public String getTime(){
        deltaTime=preferences.getLong("deltaTime",0);
        SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss",Locale.CHINA);
        Date curDate=new Date(System.currentTimeMillis()+deltaTime);
        return format.format(curDate);
    }
    /*
    * 方法名：setTime(String timeStr)
    * 功  能：设置设备时间
    * 参  数：String timeStr - 设置的时间，格式为年月日时分秒yyyyMMddHHmmss
    * 返回值：boolean - 设置是否成功
    */
    public boolean setTime(String timeStr){
        SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss",Locale.CHINA);
        SharedPreferences.Editor editor=preferences.edit();
        try{
            deltaTime=format.parse(timeStr).getTime()-System.currentTimeMillis();
            editor.putLong("deltaTime",deltaTime);
            Log.e("test","time changed");
            return editor.commit();
        }catch (Exception e){
            Log.e("error","date parse");
            return false;
        }
    }
}
