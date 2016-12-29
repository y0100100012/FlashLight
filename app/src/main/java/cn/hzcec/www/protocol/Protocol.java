package cn.hzcec.www.protocol;

/**
 * 协议帧接口
 */
interface Protocol {
    /**
     * 返回协议帧的字节数组
     * @return 协议帧的字节数组
     */
    byte[] outputData();
}
