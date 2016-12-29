package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class KeyboardActivity extends Activity implements View.OnClickListener{
    private TextView keyboardValue;
    private String value="";
    private static final int maxStr=20;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_keyboard);

        keyboardValue=(TextView)findViewById(R.id.keyboardValue);
    }
    @Override
    public void onClick(View v) {
        int id=v.getId();
        switch(id){
            case R.id.keyNum0:
                addChar("0");
                return ;
            case R.id.keyNum1:
                addChar("1");
                return ;
            case R.id.keyNum2:
                addChar("2");
                return ;
            case R.id.keyNum3:
                addChar("3");
                return ;
            case R.id.keyNum4:
                addChar("4");
                return ;
            case R.id.keyNum5:
                addChar("5");
                return ;
            case R.id.keyNum6:
                addChar("6");
                return ;
            case R.id.keyNum7:
                addChar("7");
                return ;
            case R.id.keyNum8:
                addChar("8");
                return ;
            case R.id.keyNum9:
                addChar("9");
                return;
            case R.id.keyPoint:
                addChar(".");
                return;
            case R.id.keyConfirm:
                returnValue();
        }
    }

    private void addChar(String c){
        if(value!=null&&value.length()<=maxStr) {
            value = value+c;
            showValue();
        }
    }
    private void subChar(){
        if(value!=null&&value.length()>0) {
            value = value.substring(0,value.length()-1);
            showValue();
        }
    }
    private void showValue(){
        if(value!=null){
            keyboardValue.setText(value);
        }
    }
    private void returnValue(){
        if(value!=null&&value.length()>0) {
            Intent intent = new Intent();
            intent.putExtra("value", value);
            setResult(11, intent);
        }
        finish();
    }
}