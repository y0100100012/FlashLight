package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hzcec.www.util.AudioPlayer;

public class PreferenceActivity extends Activity {
    private ListView preferenceListView;
    private SimpleAdapter adapter;
    private List<Map<String,Object>> preferenceList;
    private int selectPosition=0;
    private SharedPreferences preferences;
    /** 提示音播放对象 */
    private AudioPlayer audioPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_preference);

        audioPlayer=AudioPlayer.getAudioPlayer(this);
        preferences=getSharedPreferences("setting",MODE_PRIVATE);
        initSettings();
        preferenceListView=(ListView)findViewById(R.id.preferenceListView);
        preferenceList=new ArrayList<>();
        adapter=new SimpleAdapter(this,preferenceList,R.layout.preference_list_item,
                new String[]{"preferenceName","preferenceDetail","preferenceValue"},
                new int[]{R.id.preferenceName,R.id.preferenceDetail,R.id.preferenceValue});
        preferenceListView.setAdapter(adapter);
        preferenceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                operateValue(position);
            }
        });
        refreshList();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==11){
            String value=data.getStringExtra("value");
            if(value!=null&&value.length()>0)
                setValue(requestCode,value);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
            int keyCode=event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if(selectPosition>0)
                        preferenceListView.setSelection(--selectPosition);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if(preferenceList.size()==0)
                        return true;
                    if(selectPosition+1<preferenceList.size())
                        preferenceListView.setSelection(++selectPosition);
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void refreshList(){
        preferenceList.clear();
        Map<String,?> preMap=preferences.getAll();
        try {
            Map<String,Object> map;
            for (Map.Entry<String,?> entry : preMap.entrySet()) {
                if(entry.getKey().equals("显示终端IP")||
                        entry.getKey().equals("照片上限")||entry.getKey().equals("录像上限")||
                        entry.getKey().equals("声音")) {
                    map = new HashMap<>();
                    map.put("preferenceName", entry.getKey());
                    map.put("preferenceDetail",getDetail(entry.getKey()));
                    map.put("preferenceValue", entry.getValue());
                    preferenceList.add(map);
                }
            }
            adapter.notifyDataSetChanged();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void operateValue(int position){
        startActivityForResult(new Intent(PreferenceActivity.this,KeyboardActivity.class),position);
    }
    private void initSettings(){
        if(!preferences.contains("显示终端IP")) {
            SharedPreferences.Editor editor= preferences.edit();
            editor.putString("照片上限", "100");
            editor.putString("录像上限", "10");
            editor.putString("声音","0");
            editor.putString("显示终端IP", "192.168.1.136");
            editor.apply();
        }
    }
    private void setValue(int position,String value){
        if(!checkValue(position,value))
            return;
        if(preferenceList.get(position).get("preferenceName").equals("声音")) {
            AudioManager audioManager=(AudioManager)getSystemService(Service.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, Integer.parseInt(value), 0);
            audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
        }
        String preferenceName = (String) preferenceList.get(position).get("preferenceName");
        SharedPreferences.Editor editor= preferences.edit();
        editor.putString(preferenceName,value);
        editor.apply();
        refreshList();
    }
    private boolean checkValue(int position,String value){
        try {
            String preferenceName = (String) preferenceList.get(position).get("preferenceName");
            char[] str;
            int i;
            switch (preferenceName){
                case "显示终端IP":
                    str = (value+".").toCharArray();
                    StringBuilder sb=new StringBuilder();
                    int count=0;
                    for(char tempChar:str){
                        if(tempChar=='.'){
                            count++;
                            if(!checkNumRange(sb.toString(),0,255))
                                return false;
                            sb=new StringBuilder();
                        }else{
                            sb.append(tempChar);
                        }
                    }
                    return count==4;
                case "照片上限":
                    str = value.toCharArray();
                    for(char tempChar:str){
                        if(tempChar<0x30||tempChar>0x39)
                            return false;
                    }
                    i=Integer.parseInt(value);
                    return (i>4&&i<201);
                case "录像上限":
                    str = value.toCharArray();
                    for(char tempChar:str){
                        if(tempChar<0x30||tempChar>0x39)
                            return false;
                    }
                    i=Integer.parseInt(value);
                    return (i>4&&i<21);
                case "声音":
                    str=value.toCharArray();
                    for(char tempChar:str){
                        if(tempChar<0x30||tempChar>0x39)
                            return false;
                    }
                    i=Integer.parseInt(value);
                    return (i>=0&&i<=13);
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return false;
    }
    private boolean checkNumRange(String num,int min,int max)throws Exception{
        int n=Integer.parseInt(num);
        return (n>=min&&n<=max);
    }
    private String getDetail(String preName){
        switch (preName){
            case "照片上限":
                return "(5~200)";
            case "录像上限":
                return "(5~20)";
            case "声音":
                return "(0~13)";
            default:
                return "";
        }
    }
}
