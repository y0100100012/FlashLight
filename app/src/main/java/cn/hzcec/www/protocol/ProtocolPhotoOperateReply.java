package cn.hzcec.www.protocol;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议帧
 * 照片操作指令应答帧
 */
public class ProtocolPhotoOperateReply implements Protocol{
    private byte[] byteOutput;//待发送帧的“数据”部分
    public ProtocolPhotoOperateReply(SharedPreferences preferences,char serialNum){
        List<Byte> dataOutput = new ArrayList<>();
        dataOutput.add((byte)0xB5);
        dataOutput.add((byte)0x81);
        dataOutput.add((byte)(serialNum>>>8));
        dataOutput.add((byte)(0x00ff & serialNum));

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
