package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import cn.hzcec.www.util.AudioPlayer;
import cn.hzcec.www.util.CustomToast;
import cn.hzcec.www.util.PhotoInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 照片浏览界面
 * 不合格、待确认照片
 */
public class PhotoBrowseActivity extends Activity{
    /** 图片浏览视图,左右移动标志 */
    private ImageView photoView,toLeft,toRight;
    /** 图片名称显示 */
    private TextView photoNameTextView,photoNameTextView2,photoNameTextView3;
    /** 图片数量显示 、图片状态*/
    private TextView photoNum,photoState;
    /** 图片信息集合 */
    private List<PhotoInfo> photoInfoList;
    /** 当前浏览图片的编号 */
    private int currentBrowsePhotoNo;
    /** 图片存储路径 */
    private String path;
    /** 提示音播放对象 */
    private AudioPlayer audioPlayer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去除标题栏，并设置全屏
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_photo_browse);

        photoView = (ImageView) findViewById(R.id.photoView);
        photoNameTextView=(TextView)findViewById(R.id.photoName);
        photoNameTextView2=(TextView)findViewById(R.id.photoName2);
        photoNameTextView3=(TextView)findViewById(R.id.photoName3);
        photoNum=(TextView)findViewById(R.id.photoNum);
        photoState=(TextView)findViewById(R.id.photoState);
        toLeft=(ImageView)findViewById(R.id.toLeft);
        toRight=(ImageView)findViewById(R.id.toRight);

        photoInfoList = new ArrayList<>();
        //设置照片存储位置
        path = Environment.getExternalStorageDirectory().toString() + "/flashlightFiles/photos/";
        audioPlayer=AudioPlayer.getAudioPlayer(this);
        ImageView sign=(ImageView)findViewById(R.id.photoSign);
        sign.setImageResource(R.drawable.wrong);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPhotoList();
        browsePhotoRefresh();
    }

    /**
     * 处理按键事件
     * @param keyCode 按键代码
     * @param event 按键事件源
     * @return 事件是否向下传播
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(event.getRepeatCount()==0){
            switch(keyCode){
                case KeyEvent.KEYCODE_BACK:
                    audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                    Intent backToMainIntent=new Intent(PhotoBrowseActivity.this,MainActivity.class);
                    startActivity(backToMainIntent);
                    overridePendingTransition(R.anim.push_down_in,R.anim.push_down_out);
                    finish();
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                    audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                    /*
                    for(int i=0;i<photoInfoList.size();i++){
                        if(!new File(photoInfoList.get(i).path).delete())
                            Log.e("error","delete failed");
                    }
                    photoInfoList.clear();
                    browsePhotoRefresh();*/
                    showDeleteDialog();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    CustomToast.show(this,(ViewGroup)findViewById(R.id.customToastRoot),"相机开启中");
                    audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                    Intent intentTake=new Intent(PhotoBrowseActivity.this,PhotoTakeActivity.class);
                    startActivity(intentTake);
                    overridePendingTransition(R.anim.push_down_in,R.anim.push_down_out);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                    startActivity(new Intent(PhotoBrowseActivity.this,PhotoBrowse2Activity.class));
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if(currentBrowsePhotoNo==0||photoInfoList.size()==0)
                        return true;
                    else{
                        currentBrowsePhotoNo--;
                        browsePhotoRefresh();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if(photoInfoList.size()==0||currentBrowsePhotoNo>=photoInfoList.size()-1)
                        return true;
                    else {
                        currentBrowsePhotoNo++;
                        browsePhotoRefresh();
                    }
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    /**
     * 确认给定文件是否为jpg文件
     * @param fPath 文件路径
     * @return 是否为jpg文件
     */
    private boolean checkIsImageFile(String fPath){
        String FileType=fPath.substring(fPath.lastIndexOf(".")+1,fPath.length()).toLowerCase();
        return FileType.equals("jpg");
    }
    /**
     * 刷新照片浏览视图及相关信息显示视图
     */
    private void browsePhotoRefresh(){
        if(photoInfoList.size()==0) {
            photoView.setImageResource(R.drawable.nophoto);
            photoNameTextView.setText("没有图片");
            photoNameTextView2.setText("");
            photoNameTextView3.setText("");
            photoNameTextView2.setVisibility(View.GONE);
            photoNameTextView3.setVisibility(View.GONE);
            photoState.setVisibility(View.GONE);
            photoNum.setVisibility(View.GONE);
            toLeft.setVisibility(View.INVISIBLE);
            toRight.setVisibility(View.GONE);
            photoNum.setText("");
            return;
        }
        photoNameTextView2.setVisibility(View.VISIBLE);
        photoNameTextView3.setVisibility(View.VISIBLE);
        photoState.setVisibility(View.VISIBLE);
        photoNum.setVisibility(View.VISIBLE);
        if(currentBrowsePhotoNo==0)
            toLeft.setVisibility(View.INVISIBLE);
        else
            toLeft.setVisibility(View.VISIBLE);
        if(currentBrowsePhotoNo==photoInfoList.size()-1)
            toRight.setVisibility(View.GONE);
        else
            toRight.setVisibility(View.VISIBLE);
        PhotoInfo currentPhoto=photoInfoList.get(currentBrowsePhotoNo);
        String name=currentPhoto.name;
        if(currentPhoto.state.equals("u"))
            photoState.setText("不合格");
        else
            photoState.setText("待确认");
        StringBuilder sb=new StringBuilder(name.substring(0,16));
        sb.insert(14,"-");
        sb.insert(12,"-");
        sb.insert(10,"-");
        sb.insert(8,"\n");
        sb.insert(6,"-");
        sb.insert(4,"-");
        sb.insert(2,"-");
        photoNameTextView.setText(sb);
        sb=new StringBuilder(name.substring(16,24));
        sb.insert(6,"-");
        sb.insert(4,"-");
        photoNameTextView2.setText(sb);
        sb=new StringBuilder(name.substring(24));
        sb.insert(4,":");
        sb.insert(2,":");
        photoNameTextView3.setText(sb);
        Bitmap photoBitmap = BitmapFactory.decodeFile(currentPhoto.path);
        photoView.setImageBitmap(photoBitmap);
        photoNum.setText(currentBrowsePhotoNo+1+"/"+photoInfoList.size());
    }
    private void getPhotoList(){
        photoInfoList.clear();
        currentBrowsePhotoNo=0;
        File filePath=new File(path+"u");
        File[] allFiles;
        allFiles=filePath.listFiles();
        String filePathTemp;
        if(allFiles!=null){
            String fileNameTemp;
            for(File fileTemp:allFiles){
                filePathTemp=fileTemp.getPath();
                if(checkIsImageFile(filePathTemp)){
                    fileNameTemp=fileTemp.getName();
                    PhotoInfo photoInfoTemp=new PhotoInfo(fileNameTemp.substring(0,fileNameTemp.lastIndexOf(".")),filePathTemp,"u");
                    photoInfoList.add(photoInfoTemp);
                }
            }
        }
        filePath=new File(path+"w");
        allFiles=filePath.listFiles();
        if(allFiles!=null){
            String fileNameTemp;
            for(File fileTemp:allFiles){
                filePathTemp=fileTemp.getPath();
                if(checkIsImageFile(filePathTemp)){
                    fileNameTemp=fileTemp.getName();
                    PhotoInfo photoInfoTemp=new PhotoInfo(fileNameTemp.substring(0,fileNameTemp.lastIndexOf(".")),filePathTemp,"w");
                    photoInfoList.add(photoInfoTemp);
                }
            }
        }
    }
    private void showDeleteDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除确认");
        builder.setMessage("是否删除该照片？");
        builder.setNegativeButton("否", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {}
        });
        builder.setPositiveButton("是", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.e("test","确定");
                File file=new File(photoInfoList.get(currentBrowsePhotoNo).path);
                if(!file.delete())
                    Log.e("error","删除失败，路径 "+photoInfoList.get(currentBrowsePhotoNo).path);
                getPhotoList();
                browsePhotoRefresh();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
