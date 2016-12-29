package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import cn.hzcec.www.util.AudioPlayer;
import cn.hzcec.www.util.CustomToast;

/**
 * NFC扫描界面
 */
public class NFCScanActivity extends Activity {
    /** NFC适配器*/
    private NfcAdapter nfcAdapter;
    /** 扫描到的NFC标签是否为新的*/
    //private boolean isNews = true;
    /** 为发现NFC标签时准备的意图*/
    private PendingIntent pendingIntent;
    /** 意图过滤器集合*/
    private IntentFilter[] mFilters;
    /** NFC标签支持的技术集合*/
    private String[][] mTechLists;
    /** 按键是否可用，true可用，false锁定 */
    private boolean isInputEnable =true;
    /** 提示音播放对象 */
    private AudioPlayer audioPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_nfcscan);

        TextView prompt = (TextView) findViewById(R.id.promt);
        audioPlayer=AudioPlayer.getAudioPlayer(this);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            prompt.setText(R.string.nfc_no_exist);
            finish();
            setResult(0);
            finish();
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            prompt.setText(R.string.nfc_not_open);
            finish();
            setResult(0);
            finish();
            return;
        }
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        ndef.addCategory("*/*");
        mFilters = new IntentFilter[] { ndef };
        mTechLists = new String[][] {
                new String[] { MifareUltralight.class.getName() } };
    }
    @Override
    protected void onResume() {
        Log.e("test","onResume");
        super.onResume();
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, mFilters, mTechLists);
        /*
        if (isNews) {
            if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction())) {
                processIntent(getIntent());
                isNews = false;
            }
        }*/
    }
    @Override
    protected void onNewIntent(Intent intent) {
        Log.e("test","onNewIntent");
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            processIntent(intent);
        }
    }

    /**
     * 处理来自系统的intent
     * 判断NFC标签是否含有需要的技术支持
     * @param intent 检测到NFC标签时由系统发出的intent
     */
    private void processIntent(Intent intent) {
        if(!isInputEnable)
            return;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        String[] techList = tag.getTechList();
        boolean haveMifareUltralight=false;
        for (String tech : techList) {
            if(tech.contains("MifareUltralight")){
                haveMifareUltralight=true;
            }
        }
        if(haveMifareUltralight){
            processMifareUltralight(intent);
        }
    }

    /**
     * 处理MifareUltralight技术支持的NFC标签
     * 读取其中的位置信息，返回上层Activity
     * @param intent 检测到NFC标签时由系统发出的intent
     */
    private void processMifareUltralight(Intent intent){
        Tag tag=intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if(tag!=null){
            MifareUltralight mifare=MifareUltralight.get(tag);
            try{
                mifare.connect();
                byte[] retByte=new byte[8];
                byte[] payload=mifare.readPages(4);
                System.arraycopy(payload,0,retByte,0,4);
                payload=mifare.readPages(5);
                System.arraycopy(payload,0,retByte,4,4);
                Intent retIntent=new Intent();
                retIntent.putExtra("position",retByte);
                setResult(1,retIntent);
                Log.e("test","finish");
                finish();
            }catch (Exception e){
                Log.e("nfc err","connect");
            }finally {
                if(mifare!=null){
                    try{
                        mifare.close();
                    }catch (Exception e){
                        Log.e("nfc err","close");
                    }
                }
            }
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(isInputEnable &&event.getRepeatCount()==0) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                isInputEnable =false;
                Intent retIntent = new Intent();
                byte[] retByte = null;
                retIntent.putExtra("position", retByte);
                setResult(1, retIntent);
                CustomToast.show(this, (ViewGroup) findViewById(R.id.customToastRoot), "相机开启中");
                setResult(0,null);
                finish();
                return true;
            } else
                return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }
}