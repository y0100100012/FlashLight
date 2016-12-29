package cn.hzcec.www.flashlight;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.hzcec.www.util.CustomToast;

public class VideoListActivity extends Activity{
    ListView videoListView;
    private SimpleAdapter adapter;
    private List<Map<String,Object>> videoList;
    private File[] allVideos;
    private int selectPosition=0;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Window window=getWindow();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_video_list);
        videoListView=(ListView)findViewById(R.id.videoListView);

        videoList=new ArrayList<>();
        adapter=new SimpleAdapter(this,videoList,R.layout.video_list_item,
                new String[]{"videoName","videoTimeLength","videoState"},new int[]{R.id.videoName,R.id.videoTimeLength,R.id.videoState});
        videoListView.setAdapter(adapter);
        videoListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openVideo(position);
            }
        });
    }
    @Override
    public void onResume(){
        super.onResume();
        refreshList();
    }
    public void refreshList(){
        videoList.clear();
        File aVideos=new File(Environment.getExternalStorageDirectory().getPath()+
                "/flashlightFiles/videos");
        allVideos=aVideos.listFiles();
        int videoNum=allVideos.length;
        if(videoNum==0)
            CustomToast.show(getApplicationContext(),(ViewGroup)findViewById(R.id.customToastRoot),"无视频记录");
        else{
            Comparator<File> comparator = new Comparator<File>() {
                private SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA);

                @Override
                public int compare(File o1, File o2) {
                    try {
                        if (format.parse(o1.getName().substring(0, 14)).
                                before(format.parse(o2.getName().substring(0, 14))))
                            return 1;
                        else return -1;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            };
            Arrays.sort(allVideos,comparator);
            Map<String,Object> map;
            String name;
            int i=1;
            try {
                for (File temp : allVideos) {
                    map = new HashMap<>();
                    name = temp.getName();
                    name = name.substring(0, name.indexOf("."));
                    StringBuilder sb = new StringBuilder(name);
                    sb.insert(12, ":");
                    sb.insert(10, ":");
                    sb.insert(8, "\n");
                    sb.insert(6, "-");
                    sb.insert(4, "-");
                    map.put("videoName", sb.toString());
                    map.put("videoTimeLength", getVideoTimeLength(temp));
                    map.put("videoState", String.valueOf(i++));
                    videoList.add(map);
                }
                adapter.notifyDataSetChanged();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
            int keyCode=event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if(selectPosition>0)
                        videoListView.setSelection(--selectPosition);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if(videoList.size()==0)
                        return true;
                    if(selectPosition+1<videoList.size())
                        videoListView.setSelection(++selectPosition);
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(event.getRepeatCount()==0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return onKeyDown(KeyEvent.KEYCODE_BACK, event);
                case KeyEvent.KEYCODE_BACK:
                    finish();
                    overridePendingTransition(R.anim.push_left_in, R.anim.push_left_out);
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 开始播放录像
     * @param position 录像在List列表中的序号
     */
    private void openVideo(int position){
        File videoFile=allVideos[position];
        Intent intent=new Intent(VideoListActivity.this,VideoPlayActivity.class);
        intent.putExtra("path",videoFile.getPath());
        startActivity(intent);
    }

    /**
     * 获取录像时长
     * @param file 录像文件
     * @return 时长字符串HH:mm:ss
     * @throws IOException
     */
    private String getVideoTimeLength(File file)throws IOException{
        int frameNum, fps;
        RandomAccessFile raFile = new RandomAccessFile(file.getPath(), "rw");
        frameNum=readRandomInt(raFile,4);
        fps=readRandomInt(raFile,16);
        raFile.close();
        int timeLength=frameNum*1000/fps;
        return formatTime(timeLength);
    }

    /**
     * 读取指定位置的4字节int数据，高位在前
     * @param raFile 随机文件读取对象
     * @param ptr 读取起始位
     * @return int数据
     * @throws IOException
     */
    private int readRandomInt(RandomAccessFile raFile,int ptr)throws IOException{
        raFile.seek(ptr);
        int i=raFile.read();
        i<<=8;
        i|=raFile.read();
        i<<=8;
        i|=raFile.read();
        i<<=8;
        i|=raFile.read();
        return i;
    }
    private native String formatTime(int timeLength);
    static {
        System.loadLibrary("native-lib");
    }
}
