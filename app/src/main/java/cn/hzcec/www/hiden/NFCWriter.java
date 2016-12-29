package cn.hzcec.www.hiden;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.hzcec.www.flashlight.R;
import cn.hzcec.www.util.FlashlightTools;

/**
 * NFC写入界面
 */
public class NFCWriter extends Activity{
    /** NFC适配器*/
    private NfcAdapter nfcAdapter;
    /** 扫描到的NFC标签是否为新的*/
    private boolean isNews = true;
    /** 为发现NFC标签时准备的意图*/
    private PendingIntent pendingIntent;
    /** 意图过滤器集合*/
    private IntentFilter[] mFilters;
    /** NFC标签支持的技术集合*/
    private String[][] mTechLists;
    /** 写入文本框 */
    private EditText editText;
    /** 待写入的数据 */
    private byte[] data;
    /** 提示信息 */
    private TextView tip;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout mLinearLayout = new LinearLayout(this);
        mLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        mLinearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView mTextView = new TextView(this);
        mTextView.setText("写入数据");
        mTextView.setTextSize(40);
        LinearLayout.LayoutParams mLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLinearLayout.addView(mTextView, mLayoutParams);
        editText=new EditText(this);
        mLinearLayout.addView(editText,mLayoutParams);
        Button button=new Button(this);
        button.setText("写入");
        mLinearLayout.addView(button,mLayoutParams);
        tip=new TextView(this);
        mLinearLayout.addView(tip,mLayoutParams);
        setContentView(mLinearLayout);
        TextView prompt = (TextView) findViewById(R.id.promt);
        data=new byte[8];

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String str=editText.getText().toString();
                if(!checkFormat(str)){
                    tip.setText("格式错误");
                    return;
                }
                str=doSub(str);
                byte[] byteRet=FlashlightTools.hexStringToByte(str);
                System.arraycopy(byteRet,0,data,0,8);
                tip.setText("请扫卡");
            }
        });
        InputFilter[] filters={new InputFilter.LengthFilter(23)};
        editText.setFilters(filters);
        editText.addTextChangedListener(new TextWatcher() {
            boolean isAdd=false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length()%3==2) {
                    if (!isAdd) {
                        isAdd = true;
                        editText.setText(s + "-");
                        if(s.length()<23)
                            editText.setSelection(s.length()+1);
                    }
                }else
                    isAdd=false;
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        editText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                editText.setText("");
                return false;
            }
        });

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
        super.onResume();
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, mFilters, mTechLists);
    }
    @Override
    protected void onNewIntent(Intent intent) {
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
                byte[] temp=new byte[4];
                System.arraycopy(data,0,temp,0,4);
                mifare.writePage(4,temp);
                System.arraycopy(data,4,temp,0,4);
                mifare.writePage(5,temp);
                tip.setText("已写入");
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

    /**
     * 检查输入的数据的格式是否正确
     * @param str 输入的字符串
     * @return 格式是否正确
     */
    private boolean checkFormat(String str){
        if(str.length()!=23)
            return false;
        char[] chars=str.toCharArray();
        for(int i=0;i<23;i++){
            if(i%3==2){
                if(chars[i]!='-')
                    return false;
            }else{
                if(chars[i]<0x30||(chars[i]>0x39&&chars[i]<0x61)||chars[i]>0x66)
                    return false;
            }
        }
        return true;
    }

    /**
     * 去除字符串中的“-”
     * @param s 原字符串
     * @return 修改后的字符串
     */
    private String doSub(String s){
        char[] chars=new char[16];
        char[] oriChars=s.toCharArray();
        int ptr=0;
        for(int i=0;i<23;i++){
            if(i%3!=2){
                chars[ptr++]=oriChars[i];
            }

        }
        return String.valueOf(chars);
    }
}
