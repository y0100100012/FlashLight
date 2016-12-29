package cn.hzcec.www.protocol;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议帧
 * 时间同步请求
 */
public class ProtocolTimeSynchronizeRequire implements Protocol{
    private byte[] byteOutput;//待发送帧的“数据”部分
    public ProtocolTimeSynchronizeRequire(SharedPreferences preferences){
        List<Byte> dataOutput = new ArrayList<>();
        dataOutput.add((byte)0xB4);
        dataOutput.add((byte)0x41);
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
