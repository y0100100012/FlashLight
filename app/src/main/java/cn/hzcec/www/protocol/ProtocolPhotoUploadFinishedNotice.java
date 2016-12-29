package cn.hzcec.www.protocol;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议帧
 * 文件上传完毕通知
 */
public class ProtocolPhotoUploadFinishedNotice implements Protocol{
    private byte[] byteOutput;//待发送帧的“数据”部分
    public ProtocolPhotoUploadFinishedNotice(SharedPreferences preferences,String fileName){
        List<Byte> dataOutput = new ArrayList<>();
        dataOutput.add((byte)0xB6);
        dataOutput.add((byte)0x03);
        byte[] nameByte=FlashlightTools.photoNameMapOpposite(fileName);
        dataOutput.add((byte)(0x00ff & nameByte.length));
        for(byte temp:nameByte){
            dataOutput.add(temp);
        }
        byteOutput=new ProtocolBasic(preferences,dataOutput).outputData();
    }
    /**
     * 返回协议帧字节数组
     * @return 协议帧字节数组
     */
    public byte[] outputData(){
        return FlashlightTools.DLEAdd(byteOutput);
    }
}
