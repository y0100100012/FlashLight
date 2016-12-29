package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class VideoPlayActivity extends Activity{
    private String path;
    private SurfaceHolder videoHolder;
    private ProgressBar videoProgress;
    private boolean quit;
    private boolean isHorizontal;
    private VideoPlayTask playTask;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video_play);
        Intent intent=getIntent();
        path=intent.getStringExtra("path");
        SharedPreferences preferences=getSharedPreferences("setting",MODE_PRIVATE);
        isHorizontal=preferences.getBoolean("isHorizontal",false);
        quit=false;

        SurfaceView videoView=(SurfaceView)findViewById(R.id.videoView);
        videoProgress=(ProgressBar)findViewById(R.id.videoProgress);
        videoHolder=videoView.getHolder();
        videoHolder.setFixedSize(240,240);
        playTask=new VideoPlayTask();
        playTask.execute();
    }

    @Override
    protected void onDestroy() {
        quit=true;
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(event.getRepeatCount()==0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                    playTask.pauseOrResume();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    playTask.fastForward();
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 视频播放
     * 异步任务
     */
    private class VideoPlayTask extends AsyncTask<Void,Integer,Void>{
        private InputStream videoStream;
        private int frameNum=0;
        private int width;
        private int height;
        /** 帧间播放间隔，需减去播放一帧的耗时 */
        private long interval;
        private byte[] frame;
        private int currentFrameNum;
        private int skipStep=0;
        private int state=0;//当前状态，0停止，1播放，2暂停
        private int command=1;//命令，0无，1播放，2暂停，3快进

        /**
         * 暂停或播放
         */
        void pauseOrResume(){
            if(state==1)
                command=2;
            else if(state==2){
                onThreadResume();
            }
        }

        /**
         * 快进
         */
        void fastForward(){
            if(state==1)
                skipStep=180;
        }
        /**
         * 异步任务后台运行
         * 不在UI线程中运行
         * 读取视频帧通知UI线程更新UI
         */
        @Override
        protected Void doInBackground(Void... params) {
            if(!init())
                return null;
            try {
                if(frameNum==0)
                    return null;
                int length;
                //读取视频帧
                while(!quit) {
                    state=1;
                    findHead(videoStream);
                    currentFrameNum = readInt(videoStream);
                    length = readInt(videoStream);
                    frame = readFullArray(videoStream, length);
                    if(!checkTail(videoStream))
                        continue;
                    if(skipStep>0&&frameNum-currentFrameNum>skipStep) {
                        skipStep--;
                        continue;
                    }
                    Bitmap bitmap= BitmapFactory.decodeByteArray(frame,0,frame.length);
                    bitmap=dealBitmap(bitmap);
                    Canvas canvas=videoHolder.lockCanvas();
                    if(canvas!=null) {
                        if(bitmap!=null)
                            canvas.drawBitmap(bitmap, 0, 0, null);
                        videoHolder.unlockCanvasAndPost(canvas);
                    }
                    publishProgress();
                    if(currentFrameNum>=frameNum)
                        break;
                    if(command==2&&state==1){
                        state=2;
                        command=0;
                        onThreadWait();
                    }
                    Thread.sleep(interval);
                }
                if(quit)
                    videoStream.close();
            }catch (Exception e){
                e.printStackTrace();
                try {
                    videoStream.close();
                }catch (Exception e2){
                    e2.printStackTrace();
                }
            }
            return null;
        }

        /**
         * 更新UI
         * 运行在UI线程中
         * @param values values[0]为进度条的进度值
         */
        @Override
        protected void onProgressUpdate(Integer... values) {
            videoProgress.setProgress((int)(currentFrameNum*1.0/frameNum*100));
        }
        private int readInt(InputStream is)throws IOException{
            int i=is.read();
            i<<=8;
            i|=is.read();
            i<<=8;
            i|=is.read();
            i<<=8;
            i|=is.read();
            return i;
        }

        /**
         * 读取完整byte数组
         * @param is 输入流
         * @param length 数组长度
         * @return 完整的byte数组
         * @throws IOException
         */
        private byte[] readFullArray(InputStream is,int length)throws IOException{
            byte[] byteRet=new byte[length];
            int total=0;
            while(total<length)
                total+=is.read(byteRet,total,length-total);
            return byteRet;
        }

        /**
         * 寻找帧头
         * @param is 输入流
         * @throws IOException I/O错误，或已读到文件尾
         */
        private void findHead(InputStream is)throws IOException{
            int zeroCount=0;
            int temp;
            while(true){
                temp=is.read();
                switch (temp){
                    case 0:
                        zeroCount++;
                        break;
                    case 0x0f:
                        if(zeroCount==3)
                            return;
                        else
                            zeroCount=0;
                        break;
                    case -1:
                        throw new IOException("file end");
                    default:
                        zeroCount=0;
                }
            }
        }
        private boolean checkTail(InputStream is)throws IOException{
            byte[] tail=readFullArray(is,4);
            return (tail[0]==0&&tail[1]==0&&tail[2]==0&&tail[3]==(byte)0xf0);
        }
        private Bitmap dealBitmap(Bitmap bitmap){
            if (bitmap == null) {
                return null;
            }
            float scaleWidth,scaleHeight;
            ///!!!横纵交换
            if(isHorizontal) {
                scaleWidth = ((float) 240) / width;
                scaleHeight = ((float) 240) / height;
            }else{
                scaleWidth = ((float) 240) / height;
                scaleHeight = ((float) 240) / width;
            }
            Matrix matrix = new Matrix();
            if(!isHorizontal)
                matrix.setRotate(90);
            matrix.postScale(scaleWidth, scaleHeight);
            Bitmap newBM = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
            bitmap.recycle();
            return newBM;
        }
        private boolean init(){
            File file=new File(path);
            try {
                videoStream = new FileInputStream(file);
                if(readInt(videoStream)!=0x0f)
                    return false;
                frameNum=readInt(videoStream);
                width=readInt(videoStream);
                height=readInt(videoStream);
                interval=(long)(1000000.0/readInt(videoStream)-40);
                return readInt(videoStream)==0x00f0;
            }catch (Exception e){
                e.printStackTrace();
                return false;
            }
        }
        private void onThreadWait() {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        private synchronized void onThreadResume() {
            this.notify();
        }
    }
}
