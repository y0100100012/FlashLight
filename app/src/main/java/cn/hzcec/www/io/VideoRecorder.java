package cn.hzcec.www.io;

import android.content.SharedPreferences;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import cn.hzcec.www.util.LocalTime;


/**
 * 视频本地保存
 */
class VideoRecorder{
    /*
     * 视频格式 mjpg
     * 视频头： 0x00,0x00,0x00,0x0f,int视频帧数4字节,int视频宽度4字节,
     *          int视频高度4字节,int视频帧率4字节(单位：帧/千秒),0x00,0x00,0x00,0xf0
     * 视频帧： 0x00,0x00,0x00,0x0f,int视频帧字节数4字节,视频帧,0x00,0x00,0x00,0xf0
     */
    /** 录像保存路径 */
    private String savePath;
    /** 录像名称，扩展名为mjpg */
    private String fileName;
    /** 本地时间对象 */
    private LocalTime localTime;
    /** 文件输出流 */
    private FileOutputStream fos;
    /** 录像宽度 */
    private int width;
    /** 录像高度 */
    private int height;
    /** 当前录制的帧号 */
    private int frameNum;
    /** 录像开始的时间 */
    private long timeBegin;
    /** 是否正在录制 */
    private boolean isRecording=false;
    private int maxNum;
    /**
     * 构造方法
     * @param preferences 设置偏好
     * @param width 视频宽度
     * @param height 视频高度
     */
    VideoRecorder(SharedPreferences preferences,int width,int height){
        savePath=Environment.getExternalStorageDirectory().getPath()+
                "/flashlightFiles/videos/";
        localTime=new LocalTime(preferences);
        maxNum= Integer.parseInt(preferences.getString("录像上限","100"));
        this.width=width;
        this.height=height;
        frameNum=0;
    }

    /**
     * 开始录制
     * @throws IOException
     */
    void startRecord()throws IOException{
        deleteOldFile();
        isRecording=true;
        fileName=localTime.getTime()+".mjpg";
        File file=new File(savePath+fileName);
        fos = new FileOutputStream(file);
        writeHead(fos);
        timeBegin= SystemClock.elapsedRealtime();
    }

    /**
     * 停止录制
     * 更新视频头中的数据
     * 写入总帧数
     */
    void stopRecord(){
        int timeLength=(int)(SystemClock.elapsedRealtime()-timeBegin);//录像时长，单位毫秒
        int fps=frameNum*1000000/timeLength;
        isRecording=false;
        Log.e("test","local record stop");
        try {
            fos.close();
            if (frameNum <10) {
                File file = new File(savePath + fileName);
                if (file.delete())
                    Log.e("error", "delete failed");
            } else {
                writeRandomInt(savePath + fileName,4,frameNum);//修改总帧数
                writeRandomInt(savePath + fileName,16,fps);//修改帧率
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 向流中写视频头
     * @param os 输出流
     * @throws IOException
     */
    private void writeHead(OutputStream os)throws IOException{
        byte[] begin={0,0,0,0x0f};
        byte[] tail={0,0,0,(byte)0xf0};
        os.write(begin);//起始符
        writeInt(os,0);//总帧数，录像结束后修改，修改起始位4
        writeInt(os,width);//录像宽度
        writeInt(os,height);//录像高度
        writeInt(os,0);//帧率,单位：帧/千秒，录像结束后修改,修改起始位16
        os.write(tail);//结束符
    }

    /**
     * 向流中写入int，占4字节，高位在前
     * @param os 输出流
     * @param i int数字
     * @throws IOException
     */
    private void writeInt(OutputStream os,int i)throws IOException{
        os.write(i>>>24);
        os.write(i>>>16);
        os.write(i>>>8);
        os.write(i);
    }

    /**
     * 向文件随机位置写入4字节的int数据，高位在前
     * @param path 文件路径
     * @param ptr 写入的起始位置
     * @param num 写入的int数字
     * @throws IOException
     */
    private void writeRandomInt(String path,int ptr,int num)
            throws IOException{
        RandomAccessFile raFile = new RandomAccessFile(path, "rw");
        raFile.seek(ptr);
        byte[] bFrameNum = new byte[4];
        bFrameNum[0] = (byte) (num >>> 24);
        bFrameNum[1] = (byte) (num >>> 16);
        bFrameNum[2] = (byte) (num >>> 8);
        bFrameNum[3] = (byte) num;
        raFile.write(bFrameNum, 0, 4);
        raFile.close();
    }

    /**
     * 写入视频帧
     * 视频帧格式：0x00,0x00,0x00,0x0f,int帧号 4字节，0x00,0x00,0x00,0xf0
     * @param data 视频帧数据，格式：int长度N 4字节，数据N字节
     */
    void recordFrame(byte[] data){
        if(!isRecording)
            return;
        byte[] beg={0,0,0,0x0f};
        byte[] end={0,0,0,(byte)0xf0};
        try {
            fos.write(beg);
            writeInt(fos,++frameNum);
            fos.write(data);
            fos.write(end);
        }catch (Exception e){
            e.printStackTrace();
        }
        //超过一定长度，停止录制，新开文件重新开始
        if(frameNum>1800){
            try {
                stopRecord();
                frameNum=0;
                fos.close();
                fos=null;
                startRecord();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 视频过多时，删除文件夹中的旧文件
     */
    private void deleteOldFile(){
        File aFiles=new File(savePath);
        File[] allFiles=aFiles.listFiles();
        int fileNum=allFiles.length;

        if(fileNum>maxNum) {
            Comparator<File> comparator = new Comparator<File>() {
                private SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);

                @Override
                public int compare(File o1, File o2) {
                    try {
                        if (format.parse(o1.getName().substring(0, 14)).
                                before(format.parse(o2.getName().substring(0, 14))))
                            return -1;
                        else return 1;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            };
            Arrays.sort(allFiles, comparator);
            for (int i = 0; i < fileNum - maxNum; i++)
                if(!allFiles[i].delete())
                    Log.e("test","delete failed");
        }
    }
}
