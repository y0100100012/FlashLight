package cn.hzcec.www.protocol;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.util.CrcCheck;
import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议帧
 * 活动性检测帧
 */
public class ProtocolHeartbeat implements Protocol{
    private byte[] byteOutput;//待发送帧的“数据”部分
    public ProtocolHeartbeat(SharedPreferences preferences, String verison, byte workState, String remain){

        List<Byte> dataOutput=new ArrayList<>();
        dataOutput.add((byte)0xB1);
        dataOutput.add((byte)0x01);
        //序列号，待修改
        dataOutput.add((byte)0);
        dataOutput.add((byte)0);

        FlashlightTools.writeString(dataOutput,verison,10);
        dataOutput.add(workState);
        FlashlightTools.writeString(dataOutput,remain,7);

        byteOutput=new ProtocolBasic(preferences,dataOutput).outputData();
    }
    public void setSerialNumAndCrc(char serialNum){
        int length=byteOutput.length;
        byteOutput[length-24]=(byte)(serialNum>>>8);
        byteOutput[length-23]=(byte)(0xff & serialNum);
        //修改CRC16码
        byte[] crcByte=new byte[length-6];
        System.arraycopy(byteOutput,2,crcByte,0,length-6);
        int crc= CrcCheck.do_crc2(crcByte);
        byteOutput[length-4]=(byte)(crc>>>8);
        byteOutput[length-3]=(byte)(0xff & crc);
    }
    /**
     * 返回协议帧字节数组
     * @return 协议帧字节数组
     */
    public byte[] outputData(){
        return FlashlightTools.DLEAdd(byteOutput);
    }
}
