#include <jni.h>
#include "crc16ccitt.h"
extern "C"
jbyteArray
Java_cn_hzcec_www_protocol_ProtocolBasic_nativeGetProtocolBytes(
        JNIEnv *env, jobject /*this*/,jbyte sourcePortCode,jbyteArray sourceIP,
        jbyte destinationPortCode,jbyteArray destinationIP,jbyteArray data){
    jsize dataLength=env->GetArrayLength(data);//搭载数据data的长度
    jint length=dataLength+20;//协议帧总长
    jbyte* pSourceIP=env->GetByteArrayElements(sourceIP,0);
    jbyte* pDestinationIP=env->GetByteArrayElements(destinationIP,0);
    jbyte* pData= env->GetByteArrayElements(data,0);

    jbyte* pBytes=new jbyte[length];
    int i;
    pBytes[0]=0x10;
    pBytes[1]=0x02;
    //协议帧长度写入
    pBytes[2]=(jbyte) ((0xff00 & (length-6)) >> 8);
    pBytes[3]=(jbyte) (0xff & (length-6));
    //源端口代码写入
    pBytes[4]=sourcePortCode;
    pBytes[5]=0x04;
    //源IP地址写入
    for(i=0;i<4;i++) {
        pBytes[i + 6] = pSourceIP[i];
    }
    //目的端口代码写入
    pBytes[10]=destinationPortCode;
    pBytes[11]=0x04;
    //目的IP地址写入
    for(i=0;i<4;i++) {
        pBytes[i + 12] = pDestinationIP[i];
    }
    //搭载数据写入
    for(i=0;i<dataLength;i++) {
        pBytes[i + 16] = pData[i];
    }
    //CRC校验码写入
    jbyte* crcBytes=new jbyte[length-6];
    for(i=0;i<length-6;i++){
        crcBytes[i]=pBytes[i+2];
    }
    int crcCode=CrcEntrance((unsigned char*)crcBytes,length-6);
    delete[] crcBytes;
    pBytes[length-4]=(jbyte) ((0xff00 & crcCode) >> 8);
    pBytes[length-3]=(jbyte) (0xff & crcCode);

    pBytes[length-2]=0x10;
    pBytes[length-1]=0x03;
    env->ReleaseByteArrayElements(data,pData,0);
    env->ReleaseByteArrayElements(destinationIP,pDestinationIP,0);
    env->ReleaseByteArrayElements(sourceIP,pSourceIP,0);

    jbyteArray protocolBytes=env->NewByteArray(dataLength+20);
    env->SetByteArrayRegion(protocolBytes,0,dataLength+20,pBytes);
    delete[] pBytes;
    return protocolBytes;
}

/**
 * CRC校验
 * @param message 校验数据
 * @return 校验码，高位在前
 */
extern "C"
jint
Java_cn_hzcec_www_util_CrcCheck_do_1crc2(
        JNIEnv *env, jobject /*this*/,jbyteArray message){
    jint length=env->GetArrayLength(message);
    unsigned char* data=(unsigned char*)(env->GetByteArrayElements(message,0));
    int crcCode=CrcEntrance(data,length);
    env->ReleaseByteArrayElements(message,(jbyte*)data,0);
    return crcCode;
}
