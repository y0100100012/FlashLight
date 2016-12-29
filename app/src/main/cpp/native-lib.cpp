#include <jni.h>

void doRotate(jbyte* data,jbyte* newData,jint width,jint height){
    int i =0;
    for(int x =0;x < width;x++){
        for(int y = height-1;y >=0;y--){
            newData[i]= data[y*width+x];
            i++;
        }
    }
    i = width*height*3/2-1;for(int x = width-1;x >0;x=x-2){
        for(int y =0;y < height/2;y++){
            newData[i]= data[(width*height)+(y*width)+x];
            i--;
            newData[i]= data[(width*height)+(y*width)+(x-1)];
            i--;
        }
    }
}

/**
 * nv21图片旋转90度
 */
extern "C"
jbyteArray
Java_cn_hzcec_www_io_CameraPreviewSend_picRotate90(
        JNIEnv *env, jobject /*this*/,jbyteArray data,jint width,jint heigth) {
    jbyte *pBuffer=env->GetByteArrayElements(data,0);
    int length=env->GetArrayLength(data);
    jbyte newData[length];//返回的图片数据
    //根据原数据pBuffer生成新数据newData
    doRotate(pBuffer,newData,width,heigth);

    jbyteArray array=env->NewByteArray(length);
    env->SetByteArrayRegion(array,0,length,newData);
    env->ReleaseByteArrayElements(data,pBuffer,0);
    return array;
}

extern "C"
jstring
Java_cn_hzcec_www_flashlight_VideoListActivity_formatTime(
        JNIEnv *env, jobject /*this*/,jint timeLength) {
    int secondCount=timeLength;
    int hour,minute,second;
    char timeStr[9];
    hour=secondCount/3600;
    secondCount-=hour*3600;
    minute=secondCount/60;
    second=secondCount-minute*60;
    timeStr[0]=(char)(hour/10+48);
    timeStr[1]=(char)(hour%10+48);
    timeStr[2]=':';
    timeStr[3]=(char)(minute/10+48);
    timeStr[4]=(char)(minute%10+48);
    timeStr[5]=':';
    timeStr[6]=(char)(second/10+48);
    timeStr[7]=(char)(second%10+48);
    timeStr[8]='\0';
    return env->NewStringUTF(timeStr);
}