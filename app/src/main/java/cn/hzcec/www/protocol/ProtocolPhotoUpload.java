package cn.hzcec.www.protocol;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议帧
 * 文件上传
 */
public class ProtocolPhotoUpload implements Protocol{
    private byte[] byteOutput;//待发送帧的“数据”部分
    private int frameLength;
    private SharedPreferences preferences;
    /**
     * 文件上传时，用第一个文件块的信息创建ProtocolPhotoUpload对象，
     * 之后的文件块使用makeNewFrame方法生成协议帧，可提高性能
     * 也可以每个文件块创建一个ProtocolPhotoUpload对象（不推荐）
     * @see #makeNewFrame(int, byte[])
     * @param preferences 设置偏好
     * @param frameNo 帧号
     * @param data 数据块
     */
    public ProtocolPhotoUpload(SharedPreferences preferences,int frameNo,byte[] data){
        this.preferences=preferences;
        frameLength=preferences.getInt("frameLength",-1);
        List<Byte> dataOutput = new ArrayList<>();
        dataOutput.add((byte)0xB6);
        dataOutput.add((byte)0x02);
        FlashlightTools.writeInt(dataOutput,frameNo);
        FlashlightTools.writeInt(dataOutput,data.length);
        for(byte temp:data){
            dataOutput.add(temp);
        }
        byteOutput=new ProtocolBasic(preferences,dataOutput).outputData();
    }

    /**
     * 基于原协议帧，生成新的协议帧，
     * 帧中frameNO和data的位置固定，
     * 分别为18和26
     * @param frameNo 新协议帧的帧号
     * @param data 新协议帧的数据块
     */
    public void makeNewFrame(int frameNo,byte[] data){
        if(data.length<frameLength)
            byteOutput=new ProtocolPhotoUpload(preferences,frameNo,data).byteOutput;
        else{
            byteOutput[18]=((byte)(frameNo>>>24));
            byteOutput[19]=((byte)(0x00ff & (frameNo>>>16)));
            byteOutput[20]=((byte)(0x00ff & (frameNo>>>8)));
            byteOutput[21]=((byte)(0x00ff & frameNo));
            System.arraycopy(data,0,byteOutput,26,frameLength);
        }
        byteOutput=ProtocolBasic.crcRecheck(byteOutput);
    }
    /**
     * 返回协议帧字节数组
     * @return 协议帧字节数组
     */
    public byte[] outputData(){
        return FlashlightTools.DLEAdd(byteOutput);
    }
}
