package cn.hzcec.www.util;

/**
 * 照片信息类
 */
public class PhotoInfo {
    /** 照片名 */
    public String name;
    /** 照片路径 */
    public String path;
    /** 照片状态，"q"合格， "u"不合格， "w"待确认 */
    public String state;
    /**
     * PhotoInfo构造方法
     * @param name 照片名
     * @param path 照片路径
     */
    public PhotoInfo(String name,String path,String state){
        this.name=name;
        this.path=path;
        if(state!=null)
            this.state=state;
    }
}
