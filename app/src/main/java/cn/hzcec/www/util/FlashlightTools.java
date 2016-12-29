package cn.hzcec.www.util;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 工具集
 */
public class FlashlightTools {
    /** Intent action<br>照片审查 合格*/
    public static final String Action_PHOTO_CHECK_SUCCESS="Action_PHOTO_CHECK_SUCCESS";
    /** Intent action<br>照片审查 不合格*/
    public static final String Action_PHOTO_CHECK_FAILED="Action_PHOTO_CHECK_FAILED";
    /** Intent action<br>照片审查 网络错误*/
    public static final String Action_PHOTO_NETERROR="Action_PHOTO_NETERROR";
    /** Intent action<br>照片上传 完成*/
    public static final String Action_PHOTO_UPLOAD_FINISHED="Action_PHOTO_UPLOAD_FINISHED";
    /** Intent action<br>照片上传 正在进行*/
    public static final String Action_PHOTO_IS_UPLOADING="Action_PHOTO_IS_UPLOADING";
    /** Intent action<br>显示终端 在线*/
    public static final String Action_SERVER_ONLINE="Action_SERVER_ONLINE";
    /** Intent action<br>显示终端 离线*/
    public static final String Action_SERVER_OFFLINE="Action_SERVER_OFFLINE";
    /** Intent action<br>WIFI列表 刷新*/
    public static final String Action_REFRESH_WIFI_LIST="Action_REFRESH_WIFI_LIST";
    /** 协议中的DLE字节*/
    private static final byte DLE=0x10;
    /**
     * 将String变量加入Byte列表
     * @param dataOutput 目标列表
     * @param str 待写入String变量
     * @param writeLength 定长写入时的长度，若不需要定长写则<0
     */
    public static void writeString(List<Byte> dataOutput, String str, int writeLength){
        int strLength=str.length();
        int i;
        char[] strChar=str.toCharArray();
        if(writeLength<0){
            for(i=0;i<strLength;i++){
                dataOutput.add((byte)(0xff & strChar[i]));
            }
        }else{
            int blankLength=writeLength-strLength;
            for(i=0;i<writeLength;i++){
                if(i<blankLength)
                    dataOutput.add((byte)0);
                else
                    dataOutput.add((byte)(0xff & strChar[i-blankLength]));
            }
        }
    }
    /**
     * 将int变量加入Byte列表
     * @param dataOutput 目标列表
     * @param integer 待写入int变量
     */
    public static void writeInt(List<Byte> dataOutput,int integer){
        dataOutput.add((byte)(integer>>>24));
        dataOutput.add((byte)(0x00ff & (integer>>>16)));
        dataOutput.add((byte)(0x00ff & (integer>>>8)));
        dataOutput.add((byte)(0x00ff & integer));
    }
    /**
     * 将byte数组中的四个元素组合成int，高位在前
     * @param b 源byte数组
     * @param begin 需要的数据在byte数组中的起始位
     * @return 组合成的int数字
     */
    public static int readInt(byte[] b,int begin){
        int intRet=0x00ff & b[begin];
        intRet<<=8;
        intRet|=0x00ff & b[begin+1];
        intRet<<=8;
        intRet|=0x00ff & b[begin+2];
        intRet<<=8;
        intRet|=0x00ff & b[begin+3];
        return intRet;
    }
    /**
     * 字节数组转为相应16进制数的字符串
     * @param b 字节数组
     * @return 16进制数字的字符串
     */
    public static String byteToHexString( byte[] b) {
        if(b==null)
            return "";
        StringBuilder str=new StringBuilder();
        for (byte temp:b) {
            String hex = Integer.toHexString(0x00ff & temp);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            str.append(hex);
        }
        return str.toString();
    }
    /**
     * 16进制数的字符串转为字节数组
     * @param str 16进制数字的字符串，长度必须为偶数
     * @return 字节数组
     */
    public static byte[] hexStringToByte(String str) {
        byte[] b=new byte[str.length()/2];
        char[] ch=str.toCharArray();
        for(int i=0;i<b.length;i++){
            if(ch[i*2]>=48&&ch[i*2]<=57){
                ch[i*2]-=48;
            }else
                ch[i*2]-=87;
            b[i]=(byte)(0x000f & ch[i*2]);
            b[i]<<=4;
            if(ch[i*2+1]>=48&&ch[i*2+1]<=57){
                ch[i*2+1]-=48;
            }else
                ch[i*2+1]-=87;
            b[i]|=(byte)(0x000f & ch[i*2+1]);
        }
        return b;
    }
    /**
     * 为帧内所有DLE字节(除了头尾)之后增加一个DLE字节
     * @param data 原始数组
     * @return 处理后数组
     */
    public static byte[] DLEAdd(byte[] data){
        List<Byte> bytes=new ArrayList<>();
        int i;
        for(i=0;i<2;i++){
            bytes.add(data[i]);
        }
        for(i=2;i<data.length-2;i++){
            if(data[i]==DLE)
                bytes.add(DLE);
            bytes.add(data[i]);
        }
        for(;i<data.length;i++){
            bytes.add(data[i]);
        }
        byte[] byteRet=new byte[bytes.size()];
        for(i=0;i<byteRet.length;i++){
            byteRet[i]=bytes.get(i);
        }
        return byteRet;
    }
    /**
     * 为帧内连续两个DLE字节处删除一个DLE字节
     * @param data 原始数组
     * @return 处理后数组
     */
    public static byte[] DLESub(byte[] data){
        byte lastByte=0;
        List<Byte> bytes=new ArrayList<>();
        for(byte temp:data){
            if(lastByte==DLE&&temp==DLE){
                lastByte=0;
            }else{
                lastByte=temp;
                bytes.add(temp);
            }
        }
        byte[] byteRet=new byte[bytes.size()];
        for(int i=0;i<byteRet.length;i++){
            byteRet[i]=bytes.get(i);
        }
        return byteRet;
    }
    /**
     * 照片文件名映射
     * @param nameByte 从网络协议中的文件名
     * @return 本地存储的文件名
     */
    public static String photoNameMap(byte[] nameByte){
        byte[] position=new byte[8];
        byte[] time=new byte[6];
        System.arraycopy(nameByte,0,position,0,8);
        System.arraycopy(nameByte,8,time,0,6);
        String posiStr=byteToHexString(position);
        String timeStr;
        Calendar calendar=Calendar.getInstance();
        calendar.set(2000+time[0],time[1]-1,time[2],time[3],time[4],time[5]);
        SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);
        timeStr=format.format(calendar.getTime());
        return posiStr+timeStr;
    }
    /**
     * 照片文件名反向映射
     * @param str 本地存储的文件名，前8个字符为位置信息
     * @return 网络协议中的文件名
     */
    public static byte[] photoNameMapOpposite(String str){
        byte[]  position=hexStringToByte(str.substring(0,16));
        byte[] time=new byte[6];
        SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);
        try {
            Date date=format.parse(str.substring(16));
            Calendar calendar=Calendar.getInstance();
            calendar.setTime(date);
            time[0]=(byte)(calendar.get(Calendar.YEAR)-2000);
            time[1]=(byte)(calendar.get(Calendar.MONTH)+1);
            time[2]=(byte)calendar.get(Calendar.DATE);
            time[3]=(byte)calendar.get(Calendar.HOUR_OF_DAY);
            time[4]=(byte)calendar.get(Calendar.MINUTE);
            time[5]=(byte)calendar.get(Calendar.SECOND);
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
        byte[] bytesRet=new byte[position.length+time.length];
        System.arraycopy(position,0,bytesRet,0,8);
        System.arraycopy(time,0,bytesRet,position.length,6);
        return bytesRet;
    }

    /**
     * 测试用方法
     * @param e 标签名
     * @param b 输出的字节数组
     */
    public static synchronized void protocolOut(String e,byte[] b){
        Log.e("proto",e);
        byte[] x=new byte[1];
        StringBuilder sb=new StringBuilder();
        for(byte temp:b){
            x[0]=temp;
            sb.append(byteToHexString(x));
            sb.append("  ");
        }
        Log.e("proto",sb.toString());
    }
}