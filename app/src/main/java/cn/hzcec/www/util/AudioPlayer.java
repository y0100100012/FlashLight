package cn.hzcec.www.util;


import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import cn.hzcec.www.flashlight.R;

/**
 * 提示音播放类
 * 单例，在第一次创建时加载音效
 */
public class AudioPlayer {
    /** 单例对象 */
    private static AudioPlayer audioPlayer=null;
    /** 声音池对象 */
    private SoundPool soundPool;
    /** 音效序号 按键声
     * @see #playSound(int) */
    public static final int SOUND_BUTTON=1;
    /** 音效序号 正确提示声
     * @see #playSound(int) */
    public static final int SOUND_CORRECT=2;
    /** 音效序号 错误提示声
     * @see #playSound(int) */
    public static final int SOUND_WRONG=3;

    /**
     * AudioPlayer构造方法
     * 加载所有音效
     * @param context 上下文
     */
    private AudioPlayer(Context context){
        soundPool=new SoundPool(2, AudioManager.STREAM_SYSTEM,0);
        soundPool.load(context, R.raw.msg,1);
        soundPool.load(context,R.raw.thankyou,2);
        soundPool.load(context,R.raw.wrong,3);
    }

    /**
     * 获取单例对象
     * @param context 上下文
     * @return AudioPlayer单例对象
     */
    public static AudioPlayer getAudioPlayer(Context context){
        if(audioPlayer==null){
            synchronized (AudioPlayer.class){
                if(audioPlayer==null)
                    audioPlayer=new AudioPlayer(context);
            }
        }
        return audioPlayer;
    }

    /**
     * 播放指定序号的音效
     * @param soundID 音效的序号
     */
    public void playSound(int soundID){
        soundPool.play(soundID,1,1,0,0,1);
    }
}
