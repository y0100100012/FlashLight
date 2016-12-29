package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;

import cn.hzcec.www.hiden.NFCWriter;
import cn.hzcec.www.io.NetService;
import cn.hzcec.www.util.AudioPlayer;
import cn.hzcec.www.util.CustomToast;
import cn.hzcec.www.util.FlashlightTools;

/**
 * Application FlashLight
 * 主要活动界面
 * @author 杨麟
 * @version 2016.11.17
 * @since Android API 17
 */
public class MainActivity extends Activity {
    /** 提示音播放对象 */
    private AudioPlayer audioPlayer;
    /** WIFI状态显示 文本 */
    private TextView wifiPrompt;
    /** 显示终端连接状态 文本 */
    private TextView connectPrompt;
    /** 绑定服务{@link NetService}所用的连接 */
    private ServiceConnection conn=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };
    /** 接收WIFI状态和显示终端连接状态改变广播的广播接收器 */
    private BroadcastReceiver stateChangeBroadcastReceiver=new BroadcastReceiver() {
        /**
         * 接收到广播后改变相应状态显示文本
         * @see MainActivity#wifiPrompt,MainActivity#connectPrompt
         * @param context 上下文
         * @param intent 广播的意图
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(FlashlightTools.Action_SERVER_ONLINE.equals(action)){
                connectPrompt.setText(R.string.server_connect);
            }else if(FlashlightTools.Action_SERVER_OFFLINE.equals(action)){
                connectPrompt.setText(R.string.server_disconnect);
            }else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)){
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info.getState().equals(NetworkInfo.State.DISCONNECTED)){
                    audioPlayer.playSound(1);
                    wifiPrompt.setText(R.string.wifi_disconnect);
                }
                else if(info.getState().equals(NetworkInfo.State.CONNECTED)){
                    wifiPrompt.setText(R.string.wifi_connect);
                }
            }
        }
    };

    /**
     * 初始化各组件，注册广播接收器{@link #stateChangeBroadcastReceiver}，
     * 绑定启动服务{@link NetService}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去除标题栏，并设置全屏
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        createWorkDir();//创建工作目录
        putNetSettings();//写入设置

        wifiPrompt=(TextView) findViewById(R.id.wifiPrompt);
        connectPrompt=(TextView)findViewById(R.id.connectPrompt);
        audioPlayer=AudioPlayer.getAudioPlayer(this);

        IntentFilter intentFilter=new IntentFilter(FlashlightTools.Action_SERVER_ONLINE);
        intentFilter.addAction(FlashlightTools.Action_SERVER_OFFLINE);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(stateChangeBroadcastReceiver,intentFilter);
        //绑定启动网络通讯服务
        Intent netCommService=new Intent("cn.hzcec.www.service.NET_SERVICE");
        bindService(netCommService,conn,Service.BIND_AUTO_CREATE);
        //测试区域↓



        //测试区域↑
    }

    /**
     * 解绑服务{@link NetService},
     * 注销广播接收器{@link #stateChangeBroadcastReceiver}
     */
    @Override
    public void onDestroy(){
        unbindService(conn);
        unregisterReceiver(stateChangeBroadcastReceiver);
        super.onDestroy();
    }
    /**
     * 处理按键事件
     * @param keyCode 按键代码
     * @param event 按键事件源
     * @return 事件是否向下传播
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(event.getRepeatCount()==0) {
            audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                    //测试按键
                    startActivity(new Intent(MainActivity.this,PreferenceActivity.class));
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    CustomToast.show(this, (ViewGroup) findViewById(R.id.customToastRoot), "相机开启中");
                    Intent intentTake = new Intent(MainActivity.this, PhotoTakeActivity.class);
                    startActivity(intentTake);
                    overridePendingTransition(R.anim.push_down_in, R.anim.push_down_out);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    Intent intentBrowse = new Intent(MainActivity.this, PhotoBrowseActivity.class);
                    startActivity(intentBrowse);
                    overridePendingTransition(R.anim.push_up_in, R.anim.push_up_out);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    startActivity(new Intent(MainActivity.this, VideoListActivity.class));
                    overridePendingTransition(R.anim.push_right_in, R.anim.push_right_out);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    startActivity(new Intent(MainActivity.this, WifiActivity.class));
                    overridePendingTransition(R.anim.push_left_in, R.anim.push_left_out);
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    //隐藏入口，写NFC标签
                    startActivity(new Intent(this, NFCWriter.class));
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 将设置参数写入配置文件
     */
    public void putNetSettings(){
        SharedPreferences preferences=getSharedPreferences("setting",MODE_PRIVATE);
        SharedPreferences.Editor editor;
        editor=preferences.edit();
        editor.putString("sourcePortCode","A3");
        editor.putString("destinationPortCode","A1");
        editor.putInt("sourceUDPPort",20101);
        editor.putInt("destinationUDPPort",20002);
        editor.putInt("sourceTCPPort",20201);
        editor.putInt("destinationTCPPort",20200);
        editor.putString("sourceIP","192.168.1.1");
        editor.putString("destinationIP","192.168.1.136");
        editor.putInt("frameLength",600);
        editor.putBoolean("shouldSendPreview",true);//是否开启视频直播
        editor.putBoolean("isHorizontal",true);//true正常屏幕，false横置屏幕
        editor.putInt("previewWidth",480);
        editor.putInt("previewHeight",640);
        editor.putInt("photoMax",100);
        editor.putInt("videoMax",10);
        editor.apply();
    }

    /**
     * 创建应用所需的目录，若目录不存在
     */
    public void createWorkDir(){
        new Thread(){
            @Override
            public void run(){
                String[] dirStr={"photos/q","photos/u","photos/w","videos"};
                File file;
                for(String temp:dirStr){
                    file=new File(Environment.getExternalStorageDirectory().getPath()+"/flashlightFiles/"+temp);
                    if(!file.exists())
                        if(!file.mkdirs())
                            Log.e("error","mkDir failed");
                }
            }
        }.start();
    }
}
