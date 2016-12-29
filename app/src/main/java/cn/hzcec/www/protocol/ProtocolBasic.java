package cn.hzcec.www.protocol;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cn.hzcec.www.util.CrcCheck;
import cn.hzcec.www.util.FlashlightTools;

/**
 * 协议基本帧
 */
class ProtocolBasic implements Protocol{
    private byte[] byteOutput;//待发送帧的整体
    ProtocolBasic(SharedPreferences preferences,List<Byte> byteList){
        byte sourcePortCode;
        String sourceIP;
        byte destinationPortCode;
        String destinationIP;
        byte[] data=byteListToArray(byteList);
        byte[] b;
        b=FlashlightTools.hexStringToByte(preferences.getString("sourcePortCode",""));
        sourcePortCode=b[0];
        sourceIP=preferences.getString("sourceIP","");
        b=FlashlightTools.hexStringToByte(preferences.getString("destinationPortCode",""));
        destinationPortCode=b[0];
        destinationIP=preferences.getString("destinationIP","");
        byteOutput=nativeGetProtocolBytes(sourcePortCode,IPStringToByteArray(sourceIP),
                destinationPortCode,IPStringToByteArray(destinationIP),data);
    }

    /**
     * 重设CRC校验码
     * @param data 原帧
     * @return 修改后的帧
     */
    static byte[] crcRecheck(byte[] data){
        //修改CRC16码
        int length=data.length;
        byte[] crcByte=new byte[length-6];
        System.arraycopy(data,2,crcByte,0,length-6);
        //CRC
        int crc= CrcCheck.do_crc2(crcByte);
        data[length-4]=(byte)(crc>>>8);
        data[length-3]=(byte)(0xff & crc);
        return data;
    }
    /**
     * 返回协议帧字节数组
     * @return 协议帧字节数组
     */
    public byte[] outputData(){
        return byteOutput;
    }

    /**
     * Byte列表转换为byte数组
     * @param byteList 原Byte列表
     * @return 转换后的byte数组
     */
    private byte[] byteListToArray(List<Byte> byteList){
        int i,length;
        length=byteList.size();
        byteOutput=new byte[length];
        for(i=0;i<length;i++){
            byteOutput[i]=byteList.get(i);
        }
        return byteOutput;
    }

    /**
     * IP地址字符串转为一个长度为4的byte数组
     * @param IPStr IP字符串
     * @return 转换后的byte数组
     */
    private byte[] IPStringToByteArray(String IPStr){
        int index;
        byte[] IPBytes=new byte[4];
        String[] IPInts=new String[4];
        index=IPStr.indexOf(".");
        IPInts[0]=IPStr.substring(0,index);
        index=IPStr.indexOf(".",index+1);
        IPInts[1]=IPStr.substring(IPStr.indexOf(".")+1,index);
        IPInts[2]=IPStr.substring(index+1,IPStr.lastIndexOf("."));
        IPInts[3]=IPStr.substring(IPStr.lastIndexOf(".")+1);
        for(int i=0;i<4;i++)
            IPBytes[i]=(byte)Integer.parseInt(IPInts[i]);
        return IPBytes;
    }

    /**
     * 获取协议帧字节数组
     * @param sourcePortCode 源端口代码
     * @param sourceIP 源IP地址
     * @param destinationPortCode 目的端口代码
     * @param destinationIP 目的IP地址
     * @param data 搭载数据
     * @return 协议帧字节数组
     * @deprecated 用 nativeGetProtocolBytes(byte, byte[], byte, byte[], byte[]) 代替
     * @see #nativeGetProtocolBytes(byte, byte[], byte, byte[], byte[])
     */
    private byte[] getProtocolBytes(byte sourcePortCode,byte[] sourceIP,
                                    byte destinationPortCode,byte[] destinationIP,byte[] data){
        List<Byte> dataOutput=new ArrayList<>();
        int i,length;
        dataOutput.add((byte)0x10);
        dataOutput.add((byte)0x02);
        dataOutput.add((byte)0);//信息长度，待修改
        dataOutput.add((byte)0);//信息长度，待修改
        dataOutput.add(sourcePortCode);
        dataOutput.add((byte)4);
        for(byte temp1:sourceIP)
            dataOutput.add(temp1);
        dataOutput.add(destinationPortCode);
        dataOutput.add((byte)4);
        for(byte temp2:destinationIP)
            dataOutput.add(temp2);
        int dataLength=data.length;
        for(i=0;i<dataLength;i++){
            dataOutput.add(data[i]);
        }
        dataOutput.add((byte)0);//CRC校验，
        dataOutput.add((byte)0);//待修改
        dataOutput.add((byte)0x10);
        dataOutput.add((byte)0x03);

        length=dataOutput.size();

        byte[] byteRet=new byte[length];
        for(i=0;i<length;i++){
            byteRet[i]=dataOutput.get(i);
        }
        //修改信息长度
        byteRet[2]=(byte) ((0xff00 & (length-6)) >> 8);
        byteRet[3]=(byte) (0xff & (length-6));

        //修改CRC16码
        byte[] crcByte=new byte[length-6];
        System.arraycopy(byteRet,2,crcByte,0,length-6);
        int crc= CrcCheck.do_crc2(crcByte);
        byteRet[length-4]=(byte)(crc>>>8);
        byteRet[length-3]=(byte)(0xff & crc);

        return byteRet;
    }
    private native byte[] nativeGetProtocolBytes(byte sourcePortCode,byte[] sourceIP,
                                    byte destinationPortCode,byte[] destinationIP,byte[] data);
    static{
        System.loadLibrary("protocol-lib");
    }
}
