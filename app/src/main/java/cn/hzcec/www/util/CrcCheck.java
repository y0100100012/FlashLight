package cn.hzcec.www.util;

/**
 * CRC16-CCITT校验，采用高位在先
 */
public class CrcCheck {
    /**
     * 根据数据生成CRC校验码入口
     * @param message 待测数据
     * @return 校验码
     * @deprecated 用do_crc2(byte[])代替
     * @see #do_crc2(byte[])
     */
    public static int do_crc(byte[] message){
        char usCRCValue=0;
        for(byte temp:message){
            usCRCValue=GenCRC16(temp,usCRCValue);
        }
        return 0x00ffff & usCRCValue;
    }
    private static char GenCRC16(byte ucData,char nOldCRC){
        int i;
        nOldCRC=(char)(nOldCRC^(ucData<<8));
        for(i=0;i<=7;i++){
            if((nOldCRC&0x8000)!=0){
                nOldCRC=(char)((nOldCRC<<1)^0x1021);
            }else{
                nOldCRC<<=1;
            }
        }
        return nOldCRC;
    }

    /**
     * 根据数据生成CRC校验码入口，jni
     * @param message 待测数据
     * @return 校验码
     */
    public static native int do_crc2(byte[] message);
    static{
        System.loadLibrary("protocol-lib");
    }
}
