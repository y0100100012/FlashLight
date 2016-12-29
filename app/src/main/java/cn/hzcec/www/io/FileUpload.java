package cn.hzcec.www.io;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.protocol.ProtocolAnalysis;
import cn.hzcec.www.protocol.ProtocolPhotoUpload;
import cn.hzcec.www.protocol.ProtocolPhotoUploadFinishedNotice;
import cn.hzcec.www.protocol.ProtocolPhotoUploadNotice;
import cn.hzcec.www.util.FlashlightTools;

/**
 * 文件上传类，
 * 每个对象对应一个需要上传的文件，
 * 文件需要切分后上传
 * 使用TCP协议
 * 发送文件上传请求、文件上传、文件上传完毕通知
 */
class FileUpload {
    /** 协议中的DLE字节*/
    private static final byte DLE=0x10;
    /** 协议中的STX字节*/
    private static final byte STX=0x02;
    /** 协议中的TX字节*/
    private static final byte TX=0x03;
    /** 文件名 */
    private String fileName;
    /** 目的IP */
    private String destinationIP;
    /** 目的端口 */
    private int destinationTCPPort;
    /** 待发送的、切分后的文件 */
    private DividedBytes[] divideBytesGroup;
    /** 切分的组数 */
    private int groupNum;
    /** 偏好设置 */
    private SharedPreferences preferences;
    /** 上下文 */
    private Context context;
    /** TCP套接字 */
    private Socket socket;
    /** TCP输出流 */
    private OutputStream os;
    /** TCP输入流 */
    private InputStream is;
    /** 超时计时线程 */
    private TimeLimitThread timeLimit=null;

    /**
     * FileUpload构造方法，传入相应参数后，调用
     * {@link #startSend()}开始传输照片
     * @param context 上下文
     * @param fileName 文件名
     * @param preferences 偏好设置
     * @throws IOException
     */
    FileUpload(Context context,String fileName, SharedPreferences preferences)throws IOException{
        this.context=context;
        this.fileName=fileName;
        this.preferences=preferences;
        int frameLength=preferences.getInt("frameLength",-1);//每帧文件中照片数据的限长
        destinationIP=preferences.getString("destinationIP",null);
        destinationTCPPort=preferences.getInt("destinationTCPPort",-1);
        if(frameLength==-1||destinationIP==null||destinationTCPPort==-1)
            throw new IOException();
        String filePath= Environment.getExternalStorageDirectory().getPath()+"/flashlightFiles/photos/w/"+fileName+".jpg";
        File file=new File(filePath);

        int fileLength=(int)file.length();//文件长度
        byte[] fileBytes=new byte[fileLength];//文件的字节数组
        boolean hasRemain=false;//是否有余组
        FileInputStream fis=new FileInputStream(file);

        int offset=0;
        int numRead;
        while(offset<fileBytes.length&&(numRead=fis.read(fileBytes,offset,fileBytes.length-offset))>= 0){
            offset+=numRead;
        }
        if(offset!=fileBytes.length){
            throw new IOException("Could not completely read file"+file.getName());
        }
        fis.close();
        groupNum=fileLength/frameLength;
        if(fileLength%frameLength!=0){
            hasRemain=true;
            groupNum++;
        }
        divideBytesGroup=new DividedBytes[groupNum];
        int i;
        for(i=0;i<groupNum-1;i++){
            divideBytesGroup[i]=new DividedBytes(new byte[frameLength]);
            System.arraycopy(fileBytes,frameLength*i,divideBytesGroup[i].divByte,0,frameLength);
        }
        if(hasRemain){
            int remainNum=fileLength-(groupNum-1)*frameLength;
            divideBytesGroup[i]=new DividedBytes(new byte[remainNum]);
            System.arraycopy(fileBytes,frameLength*i,divideBytesGroup[i].divByte,0,remainNum);
        }else{
            divideBytesGroup[i]=new DividedBytes(new byte[frameLength]);
            System.arraycopy(fileBytes,frameLength*i,divideBytesGroup[i].divByte,0,frameLength);
        }
        Log.e("test",String.valueOf(groupNum)+" groups");
    }

    /**
     * 开启TCP连接
     * 开始传输文件
     */
    void startSend(){
        Log.e("test","startSend");
        new SendThread().start();
        if(timeLimit==null){
            timeLimit=new TimeLimitThread();
            timeLimit.start();
        }
    }

    /**
     * 文件发送入口
     */
    private class SendThread extends Thread{
        /**
         * 发送文件上传通知，开启命令接收器CommandReceiver
         * @see CommandReceiver
         */
        @Override
        public void run(){
            try{
                //在这里开启socket，创建输出流对象
                socket=new Socket();
                socket.connect(new InetSocketAddress(destinationIP,destinationTCPPort),5000);
                Log.e("test","照片上传TCP连接成功");
                socket.setSoTimeout(5000);//超时时间
                os=socket.getOutputStream();
                Log.e("test","sendUploadNotice");
                sendUploadNotice();//发送文件上传通知
                new CommandReceiver().start();//开启命令接收器
            } catch (Exception e){
                e.printStackTrace();
                if(timeLimit!=null){
                    timeLimit.interrupt();
                    timeLimit=null;
                }
                Intent errIntent=new Intent(FlashlightTools.Action_PHOTO_NETERROR);
                errIntent.putExtra("photoName",fileName);
                context.sendBroadcast(errIntent);
                try{
                    if(os!=null)
                        os.close();
                    socket.close();
                }catch (Exception e2){
                    e2.printStackTrace();
                }
            }
        }
    }

    /**
     * TCP命令接收器
     */
    private class CommandReceiver extends Thread{
        /**
         * 接收并处理文件上传请求
         */
        @Override
        public void run(){
            try{
                Log.e("test","openReceiver");
                //在这里创建输入流对象
                byte[] data;
                is=socket.getInputStream();
                while(true){
                    ProtocolAnalysis pa=new ProtocolAnalysis(readOneFrame(is));
                    int fileNameLength;
                    byte frameNumRequire;
                    if(pa.serviceType==(byte)0xB6 && pa.command==(byte)0x81){//文件上传请求
                        data=pa.data;
                        fileNameLength=0x00ff & data[0];
                        frameNumRequire=data[fileNameLength+1];
                        if(frameNumRequire==(byte)0x00){//接收完毕通知
                            if(timeLimit!=null){
                                timeLimit.interrupt();
                                timeLimit=null;
                            }
                            is.close();
                            os.close();
                            socket.close();
                            Log.e("test","收到接收完毕通知");
                            context.sendBroadcast(new Intent(FlashlightTools.Action_PHOTO_UPLOAD_FINISHED));
                            break;
                        }else if(frameNumRequire==(byte)0xff){
                            send(null);
                        }
                        else {
                            int frameNumRequireInt=0x00ff & frameNumRequire;
                            int[] frameNos=new int[frameNumRequireInt];
                            for(int i=0;i<frameNumRequireInt;i++){
                                frameNos[i]=FlashlightTools.readInt(data,fileNameLength+2+i*4);
                            }
                            send(frameNos);
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
                try{
                    is.close();
                }catch (Exception e2){
                    e2.printStackTrace();
                }
            }
        }
    }

    /**
     * 发送文件上传通知
     * @throws Exception
     */
    private void sendUploadNotice()throws Exception{
        ProtocolPhotoUploadNotice ppun=
                new ProtocolPhotoUploadNotice(preferences,(byte)0,fileName);
        sendTCPPacket(ppun.outputData());
        Log.e("test","已发送文件上传通知");
    }

    /**
     * 发送上传完成通知
     * @throws Exception
     */
    private void sendUploadFinishedNotice()throws Exception{
        Log.e("test","finish");
        ProtocolPhotoUploadFinishedNotice ppufn=
                new ProtocolPhotoUploadFinishedNotice(preferences,fileName);
        try{
            sendTCPPacket(ppufn.outputData());
            Log.e("test","已发送文件上传完毕通知");
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /**
     * 发送文件
     * 有两种模式：发送全部、发送个别帧
     * @param frameNos 帧号集合，为null时发送全部，否则发送frameNos指定的帧号集
     */
    private void send(int[] frameNos){
        try{
            if(frameNos==null){//发送全部
                ProtocolPhotoUpload ppu=new ProtocolPhotoUpload(preferences,0,
                        divideBytesGroup[0].divByte);
                for(int i=0;i<groupNum;i++){
                    ppu.makeNewFrame(i,divideBytesGroup[i].divByte);
                    sendTCPPacket(ppu.outputData());
                    Log.e("proto","send file frame."+String.valueOf(i));//测试用
                }
            }else {//发送个别帧
                for(int temp:frameNos){
                    ProtocolPhotoUpload ppu=
                            new ProtocolPhotoUpload(preferences,temp,divideBytesGroup[temp].divByte);
                    sendTCPPacket(ppu.outputData());
                    //Log.e("proto","send file frame."+String.valueOf(temp));//测试用
                }
            }
            sendUploadFinishedNotice();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 文件经过切分后的块
     */
    private class DividedBytes{
        /** 块的数据 */
        byte[] divByte;
        /** 块是否发送完*/
        boolean isSent;

        /**
         * DividedBytes方法
         * @param divByte 块的数据
         */
        DividedBytes(byte[] divByte){
            this.divByte=divByte;
            isSent=false;
        }
    }

    /**
     * 发送一个帧
     * @param sendBuf 发送的帧的字节数组
     * @throws Exception 发送失败时抛出
     */
    private void sendTCPPacket(byte[] sendBuf)throws Exception{
        os.write(sendBuf);
        //调试输出，时间消耗巨大
        //FlashlightTools.protocolOut("TCP send",sendBuf);
    }

    /**
     * 从流中使用基于定界符的方法读取一个帧
     * @param is 输入流
     * @return 帧字节数组
     * @throws Exception 不能读取一个完整帧时抛出
     */
    private byte[] readOneFrame(InputStream is) throws Exception{
        List<Byte> byteList=new ArrayList<>();
        int byteTempInt;
        byte byteTemp;
        int DLECount=0;//DLE个数计数
        int byteCount=0;//字节计数
        byte byteLast=0;
        while((byteTempInt=is.read())!=-1 && byteCount++<1024){
            Log.e("test","接收T头");
            byteTemp=(byte)byteTempInt;
            if(byteLast==DLE && byteTemp==STX){
                byteList.add(DLE);
                byteList.add(STX);
                break;
            }
            byteLast=byteTemp;
        }
        while ((byteTempInt=is.read())!=-1 && byteList.size()<1024){
            byteTemp=(byte)byteTempInt;
            byteList.add(byteTemp);
            if(byteTemp==DLE)
                DLECount++;
            else{
                if(byteTemp==TX && DLECount%2==1){
                    byte[] byteRet=new byte[byteList.size()];
                    for(int i=0;i<byteRet.length;i++)
                        byteRet[i]=byteList.get(i);
                    FlashlightTools.protocolOut("TCP receive",byteRet);
                    return byteRet;
                }
                DLECount=0;
            }
        }
        throw new IOException();
    }

    /**
     * 超时计时线程
     */
    private class TimeLimitThread extends Thread{
        @Override
        public void run(){
            try{
                Thread.sleep(10000);
                Intent errIntent=new Intent(FlashlightTools.Action_PHOTO_NETERROR);
                errIntent.putExtra("photoName",fileName);
                context.sendBroadcast(errIntent);
                Log.e("test","照片上传超时");
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }
}
