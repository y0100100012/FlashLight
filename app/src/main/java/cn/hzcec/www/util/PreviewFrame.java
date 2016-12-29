package cn.hzcec.www.util;

/**
 * 预览帧
 */
public class PreviewFrame {
    public byte[] data;
    public int frameNo;
    public PreviewFrame(byte[] data,int frameNo){
        this.data=data;
        this.frameNo=frameNo;
    }
}
