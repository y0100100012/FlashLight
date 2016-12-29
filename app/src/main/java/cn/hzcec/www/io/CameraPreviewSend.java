package cn.hzcec.www.io;


import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import cn.hzcec.www.util.PreviewFrame;

/**
 * 视频预览帧实时上传
 */
public class CameraPreviewSend{

    /** 本地录像保存对象 */
    private VideoRecorder recorder=null;
    /** 目的端口 */
    private int destinationTCPPort;
    /** 目的IP地址 */
    private String destinationIP;
    /** TCP套接字 */
    private Socket socket;
    /** TCP输出流 */
    private OutputStream os;
    /** 与TCP发送线程通信的控制器
     * @see sendThread*/
    private Handler handler;
    /** 网络状态，0断开，1连接 */
    private int state=0;
    /** 是否拥塞 */
    private boolean congestion=false;
    /** 设置偏好 */
    private SharedPreferences preferences;
    private boolean isHorizontal;
    private int previewWidth;
    private int previewHeight;
    /** 构造方法 */
    public CameraPreviewSend(SharedPreferences preferences){
        this.preferences=preferences;
        destinationTCPPort=6666;
        destinationIP=preferences.getString("destinationIP",null);
        isHorizontal=preferences.getBoolean("isHorizontal",false);
        previewWidth=preferences.getInt("previewWidth",640);
        previewHeight=preferences.getInt("previewHeight",480);
    }

    /**
     * 发送一帧
     * @param data nv21图片，需要旋转90°
     * @param frameNo 帧号
     */
    public void sendOneFrame(byte[] data, int frameNo) {
        if(congestion) {
            if(isHorizontal)
                localFrameSave(picRotate90(data,previewWidth,previewHeight));
            else
                localFrameSave(data);
            return;
        }
        congestion=true;
        if(state==1){
            Message msg=handler.obtainMessage();
            if(isHorizontal)
                msg.obj=new PreviewFrame(picRotate90(data,previewWidth,previewHeight),frameNo);
            else
                msg.obj=new PreviewFrame(data,frameNo);
            msg.what=3;
            handler.sendMessage(msg);
        }
    }

    /**
     * 先对图片数据进行处理，然后发送帧到流中
     * @param data nv21图片数据，需要旋转90°
     * @throws IOException
     */
    private void sendFrame(byte[] data)throws IOException{
        if(data==null)
            return;
        YuvImage image;
        if(isHorizontal)
            image = new YuvImage(data, ImageFormat.NV21, previewHeight, previewWidth, null);
        else
            image = new YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null);
        ByteArrayOutputStream outputSteam = new ByteArrayOutputStream();
        image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputSteam);
        data = outputSteam.toByteArray();
        //图片长度
        int dataLength=data.length;
        byte[] sign=new byte[4];
        sign[0]=((byte)(dataLength>>>24));
        sign[1]=((byte)(0x00ff & (dataLength>>>16)));
        sign[2]=((byte)(0x00ff & (dataLength>>>8)));
        sign[3]=((byte)(0x00ff & dataLength));
        //图片本体
        byte[] byteOutput=new byte[data.length+4];
        System.arraycopy(sign,0,byteOutput,0,4);
        System.arraycopy(data,0,byteOutput,4,dataLength);
        //下方可改为异步任务

        if(recorder!=null)
            recorder.recordFrame(byteOutput);
        sendTCPPacket(byteOutput);
    }

    /**
     * 开始发送
     * 在此方法调用后，才能进行预览帧发送，即调用
     * {@link #sendFrame(byte[])}方法
     */
    public void startSend(){
        try {
            if(isHorizontal)
                recorder=new VideoRecorder(preferences,previewHeight,previewWidth);
            else
                recorder=new VideoRecorder(preferences,previewWidth,previewHeight);
            recorder.startRecord();
        }catch (Exception e){
            e.printStackTrace();
        }

        new TcpLinkStartThread().start();
        new sendThread().start();
    }

    /**
     * 结束发送
     * 在此方法调用后，不能再调用 {@link #sendFrame(byte[])}方法
     */
    public void endSend(){
        handler.sendEmptyMessage(2);

        try {
            if(recorder!=null){
                recorder.stopRecord();
                recorder=null;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 本地预览帧写入
     * @param data 预览帧数据
     */
    private void localFrameSave(byte[] data){
        if(data==null)
            return;
        YuvImage image;
        if(isHorizontal)
            image = new YuvImage(data, ImageFormat.NV21, previewHeight, previewWidth, null);
        else
            image = new YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null);
        ByteArrayOutputStream outputSteam = new ByteArrayOutputStream();
        image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputSteam);
        data = outputSteam.toByteArray();
        //图片长度
        int dataLength=data.length;
        byte[] sign=new byte[4];
        sign[0]=((byte)(dataLength>>>24));
        sign[1]=((byte)(0x00ff & (dataLength>>>16)));
        sign[2]=((byte)(0x00ff & (dataLength>>>8)));
        sign[3]=((byte)(0x00ff & dataLength));
        //图片本体
        byte[] byteOutput=new byte[data.length+4];
        System.arraycopy(sign,0,byteOutput,0,4);
        System.arraycopy(data,0,byteOutput,4,dataLength);
        if(recorder!=null)
            recorder.recordFrame(byteOutput);
    }
    private synchronized void sendTCPPacket(byte[] data)throws IOException{
            os.write(data);
        congestion=false;
    }
    private class sendThread extends Thread{
        public void run(){
            Looper.prepare();
            handler=new Handler(){
                public void handleMessage(Message msg){
                    if(state==1&&msg.what==2) {//停止传输，断开连接
                        if(handler!=null){
                            handler.getLooper().quit();
                        }
                        Log.e("test", "preview discon network");
                        state=0;
                        try {
                            if(os!=null)
                                os.close();
                            os = null;
                            if(socket!=null) {
                                socket.close();
                                socket = null;
                            }
                        } catch (IOException e) {
                            state=0;
                            e.printStackTrace();
                        }
                    }else if(msg.what==3){//传输帧
                        if(state==1) {
                            PreviewFrame frame = (PreviewFrame) msg.obj;
                            try {
                                sendFrame(frame.data);
                            }catch (IOException e){
                                e.printStackTrace();
                                try {
                                    if (os != null) {
                                        os.close();
                                        os = null;
                                    }
                                    if (socket != null) {
                                        socket.close();
                                        socket = null;
                                    }
                                    if(state==1) {
                                        new TcpLinkStartThread().start();
                                    }
                                }catch (Exception e2){
                                    e2.printStackTrace();
                                }
                            }
                        }else
                            congestion=false;
                    }
                }
            };
            Looper.loop();
        }
    }
    private class TcpLinkStartThread extends Thread{
        @Override
        public void run(){
            socket=new Socket();
            try {
                socket.connect(new InetSocketAddress(destinationIP, destinationTCPPort), 5000);
                socket.setSoTimeout(5000);//超时时间
                os=socket.getOutputStream();
                state=1;
                congestion=false;
                Log.e("test","preview connect succeed");
            }catch (IOException e){
                if(state==1){
                    new TcpLinkStartThread().start();
                }
            }
        }
    }
    public native byte[] picRotate90(byte[] data,int width,int height);
    static{
        System.loadLibrary("native-lib");
    }
}
