package cn.hzcec.www.io;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

class RedoUploadThread extends Thread{
    private static final int maxItemNum=4;
    private static final long delayTime=16000;
    private Queue<String> photoQueue;
    private Context context;
    private boolean quit=false;
    private SharedPreferences preferences;
    RedoUploadThread(Context context){
        this.context=context;
        preferences=context.getSharedPreferences("setting",Context.MODE_PRIVATE);
        photoQueue=new LinkedList<>();
    }
    @Override
    public void run(){
        String photoName;
        File file;
        FileUpload fileUpload;
        while(!quit){
            try{
                photoName=photoQueue.poll();
                if(photoName!=null){
                    file=new File(Environment.getExternalStorageDirectory().getPath()+
                            "/flashlightFiles/photos/w/"+photoName+".jpg");
                    if(!file.exists())
                        continue;
                    fileUpload=new FileUpload(context,photoName,preferences);
                    fileUpload.startSend();
                }
                Thread.sleep(delayTime);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    boolean addItem(String photoName){
        return photoQueue.size()<maxItemNum && photoQueue.offer(photoName);
    }
    void stopThread(){
        quit=true;
    }
}
