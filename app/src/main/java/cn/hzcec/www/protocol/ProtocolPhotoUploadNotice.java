package cn.hzcec.www.protocol;


import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import cn.hzcec.www.util.FlashlightTools;


/**
 * 协议帧
 * 文件上传通知
 */
public class ProtocolPhotoUploadNotice implements Protocol{
    private byte[] byteOutput;//待发送帧的“数据”部分
    public ProtocolPhotoUploadNotice(SharedPreferences preferences, byte compressType, String fileName) throws IOException{
        List<Byte> dataOutput = new ArrayList<>();
        int fileLength=0;
        int frameLength=preferences.getInt("frameLength",-1);//每个帧中文件的限制长度
        String path = Environment.getExternalStorageDirectory().toString() + "/flashlightFiles/photos/w/"+fileName+".jpg";
        File file=new File(path);
        if(file.exists()){
            fileLength=(int)file.length();
        }
        else{
            Log.e("error","file not exist");
        }
        dataOutput.add((byte) 0xB6);
        dataOutput.add((byte) 0x01);
        dataOutput.add(compressType);

        //照片名
        byte[] nameByte=FlashlightTools.photoNameMapOpposite(fileName);

        dataOutput.add((byte)nameByte.length);
        for(byte temp:nameByte){
            dataOutput.add(temp);
        }
        FlashlightTools.writeInt(dataOutput,fileLength);
        FlashlightTools.writeInt(dataOutput,fileLength/frameLength+1);
        FlashlightTools.writeInt(dataOutput,frameLength);

        FileInputStream fi = new FileInputStream(file);
        byte[] buffer = new byte[fileLength];
        int offset=0;
        int numRead;
        while(offset<buffer.length&&(numRead=fi.read(buffer,offset,buffer.length-offset))>= 0){
            offset+=numRead;
        }
        if(offset!=buffer.length){
            throw new IOException("Could not completely read file"+file.getName());
        }
        CRC32 crc32=new CRC32();
        crc32.update(buffer);
        FlashlightTools.writeInt(dataOutput,(int)crc32.getValue());
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
