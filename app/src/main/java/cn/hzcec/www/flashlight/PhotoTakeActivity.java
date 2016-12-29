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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import cn.hzcec.www.io.NetService;
import cn.hzcec.www.util.AudioPlayer;
import cn.hzcec.www.io.CameraPreviewSend;
import cn.hzcec.www.util.CustomToast;
import cn.hzcec.www.util.FlashlightTools;
import cn.hzcec.www.util.LocalTime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * 拍照界面
 * @since Android API 17
 */
public class PhotoTakeActivity extends Activity {
    /** 相机预览视图 */
    private SurfaceView surfaceView;
    /** 视频预览帧 帧号 */
    private int cameraPreviewFrameNo=0;
    /** 照片名（拍摄时间） */
    private String photoName;
    /** 位置信息 */
    private String position="";
    /** 拍照后照片的回显位图 */
    private Bitmap bitmap;
    /** 拍照后显示的拍到的照片 */
    private ImageView previewPhoto;
    /** 界面下方提示条 */
    private RelativeLayout photoTakeBar;
    /** 等待提示标志 */
    private LinearLayout photoCheckBar;
    /** 相机对象 */
    private Camera camera;
    /** 不合格照片数量提示标志 */
    private TextView uPhotoNum;
    /** 是否正在上传照片的标志 */
    private ProgressBar uploadSign;
    /** 拍照预览视图控制器 */
    private SurfaceHolder mHolder;
    /** 标志位  是否拍照预览中*/
    private boolean isPreview;
    /** 标志位  广播接收器{@link #photoTakeBroadcastReceiver}是否已注册 */
    private boolean isReceiverRegister=false;
    /** 是否需要发送预览帧 */
    private boolean shouldSendPreview=true;
    /** 通过binder与网络连接服务{@link NetService}通信 */
    private NetService.MyBinder binder;
    /** 提示音播放对象 */
    private AudioPlayer audioPlayer;
    /** 相机预览帧发送对象 */
    private CameraPreviewSend previewSend=null;
    /** 设置偏好 */
    private SharedPreferences preferences;
    /** Activity是否已退出 */
    private boolean quit;
    private int previewWidth;
    private int previewHeight;
    private MyHandler handler;
    /**
     * 接收照片上传反馈信息的广播接收器
     */
    private BroadcastReceiver photoTakeBroadcastReceiver=new BroadcastReceiver() {
        /**
         * 每次照片上传后注册该接收器，
         * 接收器收到照片状态相关信息（如合格、不合格、网络错误等）后，
         * 进行相应的操作
         * @param context 上下文
         * @param intent 广播的意图
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            Log.e("test",action);
            uploadSign.setVisibility(View.GONE);
            if(FlashlightTools.Action_PHOTO_CHECK_SUCCESS.equals(action)){
                //合格
                CustomToast.showSign(getApplicationContext(),(ViewGroup)findViewById(R.id.signToastRoot),0);
                audioPlayer.playSound(AudioPlayer.SOUND_CORRECT);
                String name=intent.getStringExtra("photoName");
                savePhotoToSD(name,"q");
                unregisterReceiver(photoTakeBroadcastReceiver);
                isReceiverRegister=false;
            }else if(FlashlightTools.Action_PHOTO_CHECK_FAILED.equals(action)){
                //不合格
                audioPlayer.playSound(AudioPlayer.SOUND_WRONG);
                CustomToast.showSign(getApplicationContext(),(ViewGroup)findViewById(R.id.signToastRoot),1);
                String name=intent.getStringExtra("photoName");
                savePhotoToSD(name,"u");
                unregisterReceiver(photoTakeBroadcastReceiver);
                isReceiverRegister=false;
            }else if(FlashlightTools.Action_PHOTO_NETERROR.equals(action)){
                //上传失败
                audioPlayer.playSound(AudioPlayer.SOUND_WRONG);
                //CustomToast.show(getApplicationContext(),(ViewGroup)findViewById(R.id.customToastRoot),"上传失败");
                CustomToast.showSign(getApplicationContext(),(ViewGroup)findViewById(R.id.signToastRoot),1);
                String name=intent.getStringExtra("photoName");
                //savePhotoToSD(name,"u");
                unregisterReceiver(photoTakeBroadcastReceiver);
                isReceiverRegister=false;
            }else if(FlashlightTools.Action_PHOTO_UPLOAD_FINISHED.equals(action)){
                //表示成功上传，等待回复
                audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
            }
            refreshUPhotoNum();
        }
    };
    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what==0){
                uploadSign.setVisibility(View.VISIBLE);
            }else if(msg.what==1){
                refreshUPhotoNum();
            }
        }
    }
    /** 绑定服务{@link NetService}所用的连接 */
    private ServiceConnection conn=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder=(NetService.MyBinder)iBinder;
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去除标题栏，并设置全屏
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_photo_take);

        surfaceView=(SurfaceView) findViewById(R.id.surfaceView);
        previewPhoto=(ImageView)findViewById(R.id.previewPhoto);
        photoTakeBar=(RelativeLayout)findViewById(R.id.photoTakeBar);
        photoCheckBar=(LinearLayout)findViewById(R.id.photoCheckBar);
        uPhotoNum=(TextView)findViewById(R.id.uPhotoNum);
        uploadSign=(ProgressBar)findViewById(R.id.uploadSign);
        uploadSign.setVisibility(View.GONE);

        preferences=getSharedPreferences("setting",MODE_PRIVATE);
        shouldSendPreview=preferences.getBoolean("shouldSendPreview",true);
        previewWidth=preferences.getInt("previewWidth",480);
        previewHeight=preferences.getInt("previewHeight",640);
        Intent NetCommService=new Intent(PhotoTakeActivity.this,NetService.class);
        bindService(NetCommService,conn, Service.BIND_AUTO_CREATE);
        audioPlayer=AudioPlayer.getAudioPlayer(this);
        handler=new MyHandler();

        //设置照相预览视图
        mHolder=surfaceView.getHolder();
        mHolder.setFixedSize(240,240);
        mHolder.addCallback(new SurfaceCallback());
    }
    @Override
    public void onStart(){
        super.onStart();
        String pathStr=Environment.getExternalStorageDirectory().getPath()+
                "/flashlightFiles/photos/q";
        File aFiles=new File(pathStr);
        File[] allFiles=aFiles.listFiles();
        int fileNum=allFiles.length;
        int maxNum= Integer.parseInt(getSharedPreferences("setting",MODE_PRIVATE).
                getString("照片上限","100"));
        if(fileNum>maxNum) {
            Comparator<File> comparator = new Comparator<File>() {
                private SimpleDateFormat format =
                        new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);
                @Override
                public int compare(File o1, File o2) {
                    try {
                        if (format.parse(o1.getName().substring(16, 30)).
                                before(format.parse(o2.getName().substring(16, 30))))
                            return -1;
                        else return 1;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            };
            Arrays.sort(allFiles,comparator);
            for(int i=0;i<fileNum-maxNum;i++)
                if(!allFiles[i].delete())
                    Log.e("test","delete failed");
        }
        refreshUPhotoNum();
    }
    @Override
    public void onResume(){
        super.onResume();
        if(!isPreview){
            surfaceView.setVisibility(View.VISIBLE);
        }
        quit=false;
    }
    @Override
    public void onPause(){
        super.onPause();
        if(isPreview){
            surfaceView.setVisibility(View.GONE);
        }
    }

    /**
     * 解绑服务{@link NetService}，注销接收器{@link #photoTakeBroadcastReceiver}
     */
    @Override
    public void onDestroy(){
        unbindService(conn);
        if(isReceiverRegister)
            unregisterReceiver(photoTakeBroadcastReceiver);
        super.onDestroy();
    }

    /**
     * 根据由活动NFCScan关闭时的返回信息，
     * 设置位置信息
     * @param requestCode 请求代码
     * @param resultCode 结果代码
     * @param data 带有位置信息的意图
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(resultCode==1){
            byte[] positionByte=data.getByteArrayExtra("position");
            position=FlashlightTools.byteToHexString(positionByte);
            //上传照片
            savePhotoToSD(position + photoName, "w");//照片放入"待确认"中
            handler.sendEmptyMessage(0);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(FlashlightTools.Action_PHOTO_CHECK_SUCCESS);
            intentFilter.addAction(FlashlightTools.Action_PHOTO_CHECK_FAILED);
            intentFilter.addAction(FlashlightTools.Action_PHOTO_NETERROR);
            intentFilter.addAction(FlashlightTools.Action_PHOTO_UPLOAD_FINISHED);
            registerReceiver(photoTakeBroadcastReceiver, intentFilter);
            isReceiverRegister = true;
            binder.uploadPhoto(position + photoName);
            handler.sendEmptyMessage(1);
        }
    }
    /**
     * 处理按键事件
     * @param keyCode 按键代码
     * @param event 按键事件源
     * @return 事件是否向下传播
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(event==null||event.getRepeatCount()==0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    audioPlayer.playSound(1);
                    if (isPreview) {
                        quit = true;
                        Intent backToMainIntent = new Intent(PhotoTakeActivity.this, MainActivity.class);
                        startActivity(backToMainIntent);
                        overridePendingTransition(R.anim.push_up_in, R.anim.push_up_out);
                        finish();
                    } else {
                        surfaceView.setVisibility(View.VISIBLE);
                        previewPhoto.setImageBitmap(null);
                        previewPhoto.setVisibility(View.GONE);
                        if (bitmap != null) {
                            bitmap.recycle();
                            bitmap = null;
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                    if (isPreview) {
                        //拍照
                        if (camera != null) {
                            try {
                                camera.reconnect();
                                camera.takePicture(null, null, new TakePictureCallback());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        //读取NFC
                        if (position.equals("")) {
                            Intent nfcIntent = new Intent(PhotoTakeActivity.this, NFCScanActivity.class);
                            startActivityForResult(nfcIntent, 1);
                            return true;
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    //测试
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    quit = true;
                    audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                    Intent photoBrowseIntent = new Intent(PhotoTakeActivity.this, PhotoBrowseActivity.class);
                    startActivity(photoBrowseIntent);
                    overridePendingTransition(R.anim.push_up_in, R.anim.push_up_out);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    audioPlayer.playSound(AudioPlayer.SOUND_BUTTON);
                    return onKeyDown(KeyEvent.KEYCODE_BACK, event);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * surfaceView回调，在{@link #surfaceView}创建、更改、摧毁时自动回调
     */
    private class SurfaceCallback implements SurfaceHolder.Callback{
        /**
         * 在surfaceView创建时回调
         * 初始化相机预览设置
         * 相机的部分设置不能为任意值
         * 应获取其支持的范围，并从中选取
         * 不能任意设置
         * @param holder 控制器
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder){
            photoCheckBar.setVisibility(View.GONE);
            photoTakeBar.setVisibility(View.VISIBLE);
            try{
                camera=Camera.open();
                camera.setDisplayOrientation(90);
                Camera.Parameters parameters=camera.getParameters();
                // 首先需要获取设备支持的照片格式、照片分辨率、fps、预览帧的图像格式
                //只可从限定的几种中选择，调用parameters.getSupported...获取
                parameters.setPictureFormat(ImageFormat.JPEG);
                parameters.setPictureSize(480,640);
                //经过与其他安卓设备对比测试，巡检仪预览帧采集最大为100/6fps
                parameters.setPreviewFpsRange(10000, 15000);//每秒帧数范围
                parameters.setPreviewFormat(ImageFormat.NV21);//预览图格式，该句可删，默认即NV21
                parameters.setPreviewSize(previewWidth,previewHeight);//预览尺寸

                camera.setParameters(parameters);
                camera.setPreviewDisplay(mHolder);
                camera.setPreviewCallback(new CameraPreviewCallback());
                camera.startPreview();
                isPreview=true;
                if(shouldSendPreview) {
                    previewSend = new CameraPreviewSend(preferences);
                    previewSend.startSend();
                }
            }catch(IOException e){
                e.printStackTrace();
            }
        }

        /**
         * 在surfaceView摧毁时回调
         * 停止录像，释放相机资源
         * @param holder 控制器
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder){
            photoTakeBar.setVisibility(View.GONE);
            if(!quit)
                photoCheckBar.setVisibility(View.VISIBLE);

            if (camera != null) {
                if(isPreview){
                    if(previewSend!=null){
                        previewSend.endSend();
                        previewSend=null;
                    }
                    camera.setPreviewCallback(null);
                    camera.stopPreview();
                    camera.release();
                    isPreview=false;
                }
                camera=null;
            }
        }

        /**
         * 在surfaceView的格式或尺寸改变时回调，此处不做任何处理
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder,int format,int width,int height) {
        }
    }

    /**
     * 相机{@link #camera}预览回调类
     */
    private class CameraPreviewCallback implements Camera.PreviewCallback{
        /**
         * 在相机预览时每产生一帧时回调
         * @param data 图片数据，格式 NV21
         * @param camera 相机对象
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            //图片格式：NV21
            try{
                if(previewSend!=null)
                    previewSend.sendOneFrame(data,cameraPreviewFrameNo++);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 回调类，拍照时触发onPictureTaken方法
     */
    private class TakePictureCallback implements Camera.PictureCallback{
        /**
         * 在{@link #camera}调用拍takePicture照时触发该方法
         * 将相机拍摄的图片转为位图对象bitmap
         * 更改相应视图，由相机预览转为照片预览
         * @param data 图片数据
         * @param camera 相机对象
         */
        public void onPictureTaken(byte[] data, Camera camera){
            try{
                if(previewSend!=null){
                    previewSend.endSend();
                    previewSend=null;
                }
                position="";
                bitmap= BitmapFactory.decodeByteArray(data,0,data.length);
                Matrix matrix=new Matrix();
                matrix.postRotate(90);
                bitmap=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                //照片命名为 4字节位置编号+年月日时分秒
                LocalTime localTime=new LocalTime(preferences);
                photoName=localTime.getTime();

                //切换视图，相机预览-->照片预览
                previewPhoto.setImageBitmap(bitmap);
                previewPhoto.setVisibility(View.VISIBLE);
                surfaceView.setVisibility(View.GONE);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 存储照片到SD卡相关目录
     * @param flag 照片状态，0待检验，1合格，2不合格
     */
    public void savePhotoToSD(String nameStr,String flag){
        String pathStr=Environment.getExternalStorageDirectory().getPath()+
                "/flashlightFiles/photos/";
        try{
            if(!flag.equals("q")&&!flag.equals("u")&&!flag.equals("w"))
                throw new Exception("flag illegal");
            deleteOldPhoto(nameStr,flag);
            if(flag.equals("w")) {//待确认
                File file = new File(pathStr + flag, nameStr + ".jpg");
                FileOutputStream outputStream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 97, outputStream);//在这里修改图片压缩比
                outputStream.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 删除或移动旧照片
     * @param nameStr 照片名（位置+时间）
     */
    public void deleteOldPhoto(String nameStr,String flag){
        String position=nameStr.substring(0,16);
        String pathStr=Environment.getExternalStorageDirectory().getPath()+
                "/flashlightFiles/photos/";
        File file=new File(pathStr+"w");
        File[] aFiles=file.listFiles();
        if(aFiles!=null){
            for(File temp:aFiles){
                if((flag.equals("u")||flag.equals("q"))&&temp.getName().substring(0,30).equals(nameStr)){
                    if(!temp.renameTo(new File(pathStr+flag+"/"+nameStr+".jpg")))
                        Log.e("error","rename failed");
                    return;
                }
                if(temp.getName().substring(0,16).equals(position))
                    if(!temp.delete()) {
                        Log.e("test", "delete old failed");
                    }
            }
        }
        if(flag.equals("u"))
            return;
        file=new File(pathStr+"u");
        aFiles=file.listFiles();
        if(aFiles!=null){
            for(File temp:aFiles){
                if(temp.getName().substring(0,16).equals(position))
                    if(!temp.delete()) {
                        Log.e("test", "delete old failed");
                    }
            }
        }
    }

    /**
     * 刷新不合格照片提示
     */
    private void refreshUPhotoNum(){
        String pathStr=Environment.getExternalStorageDirectory().getPath()+
                "/flashlightFiles/photos/";
        File uFile=new File(pathStr+"u");
        File wFile=new File(pathStr+"w");
        int num=uFile.listFiles().length+wFile.listFiles().length;
        if(num<0)
            uPhotoNum.setText("");
        else if(num==0) {
            uPhotoNum.setTextColor(getResources().getColor(R.color.colorLightGreen));
            uPhotoNum.setText("●");
        }
        else{
            uPhotoNum.setTextColor(getResources().getColor(R.color.colorRed));
            uPhotoNum.setText(String.valueOf(num));
        }
    }
}
