package cn.hzcec.www.protocol;

import android.util.Log;

import cn.hzcec.www.util.CrcCheck;
import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议解析
 */
public class ProtocolAnalysis {
    public int messageLength=0;
    public byte sourcePort;
    public String sourceIP;
    public byte destinationPort;
    public String DestinationIP;
    public byte serviceType;
    public byte command;
    public byte[] data;
    public boolean correct=true;
    public ProtocolAnalysis(byte[] dataInput){
        dataInput= FlashlightTools.DLESub(dataInput);
        int length,i;
        int ptr=0;
        byte[] stringByte;
        messageLength=0x00ff & dataInput[2];
        messageLength<<=8;
        messageLength |= 0x00ff & dataInput[3];
        if(dataInput[0]!=(byte)0x10||dataInput[1]!=(byte)0x02||dataInput[dataInput.length-2]!=
                (byte)0x10||dataInput[dataInput.length-1]!=(byte)0x03){
            correct=false;
            return;
        }
        sourcePort=dataInput[4];
        length=0x00ff & dataInput[5];
        stringByte=new byte[length];
        for(i=0;i<length;i++){
            stringByte[i]=dataInput[i+6];
        }
        sourceIP=new String(stringByte);
        ptr=6+length;
        destinationPort=dataInput[ptr++];
        length=0xff & dataInput[ptr++];
        stringByte=new byte[length];
        for(i=0;i<length;i++){
            stringByte[i]=dataInput[ptr++];
        }
        DestinationIP=new String(stringByte);
        serviceType=dataInput[ptr++];
        command=dataInput[ptr++];
        data=new byte[dataInput.length-4-ptr];
        length=data.length;
        for(i=0;i<length;i++){
            data[i]=dataInput[ptr++];
        }
        byte[] crc16ccitt;
        crc16ccitt=new byte[2];
        crc16ccitt[0]=dataInput[dataInput.length-4];
        crc16ccitt[1]=dataInput[dataInput.length-3];
        int crc=0x00ff & crc16ccitt[0];
        crc<<=8;
        crc|=(0x00ff & crc16ccitt[1]);
        byte[] byteToCheck=new byte[dataInput.length-6];
        System.arraycopy(dataInput,2,byteToCheck,0,dataInput.length-6);
        //correct=crc== CrcCheck.do_crc2(byteToCheck);
        if(crc!= CrcCheck.do_crc2(byteToCheck))
            Log.e("proto",String.valueOf(crc));
    }
}
