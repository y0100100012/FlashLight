package cn.hzcec.www.util;


import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import cn.hzcec.www.flashlight.R;

/**
 * 自定义的提示消息显示
 */
public class CustomToast {
    /**
     * 显示提示消息
     * @param context 上下文
     * @param root 根视图
     * @param msg 显示的消息的字符串
     */
    public static void show(Context context, ViewGroup root,String msg){
        Toast toast;
        View layout= LayoutInflater.from(context).inflate(R.layout.toast_item,root);
        TextView text = (TextView) layout.findViewById(R.id.customToastText);
        text.setText(msg);
        toast = new Toast(context);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 20);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
    public static void showSign(Context context, ViewGroup root,int sign){
        Toast toast;
        View layout= LayoutInflater.from(context).inflate(R.layout.sign_toast_item,root);
        if(sign==0) {
            ImageView signImage = (ImageView) layout.findViewById(R.id.signToastImage);
            signImage.setImageResource(R.drawable.right);
        }
        toast = new Toast(context);
        toast.setGravity(Gravity.END|Gravity.TOP,0,0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}
