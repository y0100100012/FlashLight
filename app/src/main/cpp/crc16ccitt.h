#ifndef _CRC16CCITT_H_
#define _CRC16CCITT_H_

/**
 * CRC校验的循环体
 * @return 校验码，高位在前
 */
unsigned short GenCrc16(unsigned char ucData, unsigned short nOldCRC)//CRC校验
{
    unsigned int i;
    //方法1：摘自XMODEM协议
    nOldCRC = nOldCRC ^ (ucData << 8);//如要连续计算N个字节使用此式
    for (i=0; i<=7; i++)
    {
        if ((nOldCRC & 0x8000) != 0)//只测试最高位
        {
            nOldCRC = (unsigned short)((nOldCRC << 1) ^ 0x1021);    //最高位为1，移位和异或处理
        }
        else
        {
            nOldCRC <<= 1;//否则只移位（乘2）
        }
    }
    return nOldCRC;
}
/**
 * CRC校验码生成入口
 * @param data 校验数据
 * @param length 数据data的长度
 * @return 校验码，高位在前
 */
int CrcEntrance(unsigned char* data,int length){
    unsigned short usCRCValue=0;
    for(int i=0;i<length;i++){
        usCRCValue=GenCrc16(data[i],usCRCValue);
    }
    return usCRCValue;
}

#endif