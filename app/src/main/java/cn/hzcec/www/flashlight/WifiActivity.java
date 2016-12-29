package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hzcec.www.util.CustomToast;
import cn.hzcec.www.util.FlashlightTools;

/**
 * WIFI选取界面
 */
public class WifiActivity extends Activity{
    private ListView wifiListView;
    /** 用于ListView的适配器 */
    private SimpleAdapter adapter;
    /** 创建SimpleAdapter示例所用的List
     * @see #adapter */
    private List<Map<String,Object>> list;
    /** wifi控制器 */
    private WifiManager wifiManager;
    /** 扫描出的网络连接列表 */
    private List<ScanResult> mWifiList;
    /** 网络连接列表 */
    private List<WifiConfiguration> mWifiConfigurations;
    /** 标示本Activity是否退出，用于停止下属线程 */
    private boolean quit;
    private int selectPosition=0;
    /**
     * 监听系统wifi状态改变的广播接收器
     */
    private BroadcastReceiver wifiStateReceiver=new BroadcastReceiver() {
        /**
         * 接收到wifi状态改变的广播后，刷新WIFI列表以及连接状态
         * @param context 上下文
         * @param intent 广播的意图
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)){
                refreshList();
            }else if(FlashlightTools.Action_REFRESH_WIFI_LIST.equals(action)){
                refreshList();
            }
        }
    };
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        //去除标题栏，并设置全屏
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_wifi);
        wifiListView=(ListView)findViewById(R.id.wifiListView);
        wifiManager=(WifiManager)getSystemService(Context.WIFI_SERVICE);
        if(!wifiManager.isWifiEnabled()){
            wifiManager.setWifiEnabled(true);
        }
        list=new ArrayList<>();
        adapter=new SimpleAdapter(this,list,R.layout.wifi_list_item,
                new String[]{"wifiName","wifiState"},new int[]{R.id.wifiName,R.id.wifiState});
        wifiListView.setAdapter(adapter);
        wifiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                connectWifi(position);
            }
        });
        quit=false;
        new freshListThread().start();
    }
    @Override
    public void onResume(){
        super.onResume();
        wifiManager.startScan();
        mWifiList=wifiManager.getScanResults();
        mWifiConfigurations=wifiManager.getConfiguredNetworks();
        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(FlashlightTools.Action_REFRESH_WIFI_LIST);
        registerReceiver(wifiStateReceiver,intentFilter);
        refreshList();
    }
    @Override
    public void onPause(){
        super.onPause();
        unregisterReceiver(wifiStateReceiver);
    }
    @Override
    public void onDestroy(){
        quit=true;
        super.onDestroy();
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
            int keyCode=event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if(selectPosition>0)
                        wifiListView.setSelection(--selectPosition);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if(list.size()==0)
                        return true;
                    if(selectPosition+1<list.size())
                        wifiListView.setSelection(++selectPosition);
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_BACK){
            finish();
            overridePendingTransition(R.anim.push_right_in,R.anim.push_right_out);
            return true;
        }if(keyCode== KeyEvent.KEYCODE_DPAD_LEFT){
            return onKeyDown(KeyEvent.KEYCODE_BACK,event);
        }else
            return  super.onKeyDown(keyCode,event);
    }
    /**
     * 获取合格的wifi名称列表和状态，刷新list
     */
    private void refreshList(){
        list.clear();
        Map<String,Object> map;
        wifiManager.startScan();
        mWifiList=wifiManager.getScanResults();
        int length=mWifiList.size();
        WifiInfo wifiInfo=wifiManager.getConnectionInfo();
        String connectedWifiSSID=wifiInfo.getSSID();
        connectedWifiSSID=connectedWifiSSID.substring(1,connectedWifiSSID.length()-1);
        for(int i=0;i<length;i++){
            if(!mWifiList.get(i).SSID.contains("hzcec"))
                continue;
            map=new HashMap<>();
            map.put("wifiName",mWifiList.get(i).SSID);
            if(mWifiList.get(i).SSID.equals(connectedWifiSSID))
                map.put("wifiState","已连接");
            else
                map.put("wifiState","");
            list.add(map);
        }
        adapter.notifyDataSetChanged();
        if (list.size()==0)
            CustomToast.show(getApplicationContext(),(ViewGroup)findViewById(R.id.customToastRoot),"无网络连接");
    }
    /**
     * 尝试连接列表中第position个wifi
     * @param position wifi在列表中的编号
     */
    private void connectWifi(int position){
        if(mWifiConfigurations==null)
            return;
        String ssid=(String)list.get(position).get("wifiName");
        int networkId=-1;
        int i,length;
        length=mWifiConfigurations.size();
        for(i=0;i<length;i++){
            if(mWifiConfigurations.get(i).SSID.equals(ssid)){
                networkId=mWifiConfigurations.get(i).networkId;
            }
        }
        if (i==length){
            WifiConfiguration wifiCong = new WifiConfiguration();
            wifiCong.SSID = "\""+ssid+"\"";
            wifiCong.preSharedKey = "\""+"12345678"+"\"";
            wifiCong.hiddenSSID = false;
            wifiCong.status = WifiConfiguration.Status.ENABLED;
            networkId = wifiManager.addNetwork(wifiCong);
        }
        if(wifiManager.enableNetwork(networkId,true))
            new Thread(){
                @Override
                public void run(){
                    try {
                        Thread.sleep(2000);
                        finish();
                        overridePendingTransition(R.anim.push_right_in,R.anim.push_right_out);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }.start();
    }
    /** 定时刷新WIFI列表的线程 */
    private class freshListThread extends Thread{
        /**
         * 定时刷新WIFI列表
         */
        @Override
        public void run(){
            while(!quit){
                try {
                    Thread.sleep(5000);
                }catch (Exception e){
                    e.printStackTrace();
                }
                sendBroadcast(new Intent(FlashlightTools.Action_REFRESH_WIFI_LIST));
            }
        }
    }
}
