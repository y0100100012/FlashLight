package cn.hzcec.www.io;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import cn.hzcec.www.protocol.ProtocolPhotoOperateReply;
import cn.hzcec.www.protocol.ProtocolSetTimeNotice;
import cn.hzcec.www.protocol.ProtocolTimeSynchronizeRequire;
import cn.hzcec.www.util.FlashlightTools;
import cn.hzcec.www.protocol.ProtocolAnalysis;
import cn.hzcec.www.protocol.ProtocolHeartbeat;
import cn.hzcec.www.util.LocalTime;

/**
 * 网络通讯服务
 * 使用UDP协议
 * 发送活动性检测帧、时间同步请求、时间同步应答
 * 接收活动性应答、照片操作指令、时间同步
 */
public class NetService extends Service {
    /** 用于与绑定的Activity通信 */
    private final IBinder binder = new MyBinder();
    /** 活动检测帧中用到的序列号 */
    private char serialNum;
    /** 标示NetService服务是否存活，用于停止服务下属线程 */
    private boolean quit;
    /** 源端口号 UDP */
    private int sourceUDPPort;
    /** 目的端口号 UDP */
    private int destinationUDPPort;
    /** 目的地址 */
    private InetAddress address;
    /** 偏好设置，用于存取端口、地址等信息 */
    private SharedPreferences preferences;
    /** 活动性检测帧发送间隔时间 */
    private int sendDelayTime = 5000;
    /** 发送活动性检测帧后没有收到回复的次数 */
    private int unReceiveTime = 0;
    /** 发送UDP帧的对象，所有线程共用，需要进行调度 */
    private DatagramSocket client;
    /** 是否与显示终端连接 */
    private boolean isServerOnline=false;
    /** 文件传输对象，对应一张照片，若为null说明目前没有在传照片 */
    private FileUpload fileUpload;
    /** 活动性检测帧发送线程 */
    private Heartbeat heartbeat=null;
    /** 命令接收线程 */
    private CommandReceiver commandReceiver=null;
    private RedoUploadThread redoUploadThread=null;
    /**
     * 接收网络状态改变时的系统广播
     * 在网络状态改变时复活因网络不可用而死亡的两个线程heartbeat和commandReceiver、
     * 照片上传失败时将其加入重传队列
     * @see Heartbeat
     * @see CommandReceiver
     */
    private BroadcastReceiver netReceiver=new BroadcastReceiver() {
        /**
         * 当网络状况改变时，若活动检测、命令接收线程死亡，则复活之
         * @param context 上下文
         * @param intent 收到的广播的意图
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)){
                if(heartbeat==null){
                    heartbeat=new Heartbeat();
                    heartbeat.start();
                }
            }else if(FlashlightTools.Action_PHOTO_UPLOAD_FINISHED.equals(action)){
                fileUpload=null;
            }else if(FlashlightTools.Action_PHOTO_NETERROR.equals(action)){
                if(redoUploadThread!=null){
                    redoUploadThread.addItem(intent.getStringExtra("photoName"));
                }
            }
        }
    };

    /**
     * 用于与绑定的Activity通信
     */
    public class MyBinder extends Binder {
        /**
         * 使用FileUpload上传照片
         * @param photoName 照片名
         * @see FileUpload
         */
        public void uploadPhoto(String photoName) {
            try{
                fileUpload=new FileUpload(getApplicationContext(),photoName,preferences);
                fileUpload.startSend();
            }catch (IOException e){
                Intent errIntent=new Intent(FlashlightTools.Action_PHOTO_NETERROR);
                errIntent.putExtra("photoName",photoName);
                sendBroadcast(errIntent);
                e.printStackTrace();
            }
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * 启动线程Heartbeat，CommandReceiver，注册广播接收器netReceiver
     * @see Heartbeat
     * @see CommandReceiver
     * @see #netReceiver
     */
    @Override
    public void onCreate() {
        super.onCreate();
        quit = false;
        serialNum = 0;
        preferences = getSharedPreferences("setting", MODE_PRIVATE);
        sourceUDPPort = preferences.getInt("sourceUDPPort", -1);
        destinationUDPPort = preferences.getInt("destinationUDPPort", -1);
        String destinationIP = preferences.getString("destinationIP", null);//目的IP
        try{
            address = InetAddress.getByName(destinationIP);
        }catch (Exception e){
            e.printStackTrace();
        }
        try {
            client = new DatagramSocket(destinationUDPPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
        heartbeat=new Heartbeat();
        heartbeat.start();
        IntentFilter intentFilter=new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction("Action_PHOTO_UPLOAD_FINISHED");
        intentFilter.addAction("Action_PHOTO_NETERROR");
        registerReceiver(netReceiver,intentFilter);
        if(redoUploadThread==null) {
            redoUploadThread = new RedoUploadThread(this);
            redoUploadThread.start();
        }
    }

    /**
     * 关闭本服务下属线程，注销广播接收器netReceiver
     *
     */
    @Override
    public void onDestroy() {
        if(redoUploadThread!=null) {
            redoUploadThread.stopThread();
            redoUploadThread=null;
        }
        quit = true;
        client.close();
        unregisterReceiver(netReceiver);
        super.onDestroy();
        System.exit(0);
    }
    /**
     * 活动性检测帧发送线程
     */
    public class Heartbeat extends Thread {
        /** 协议帧中的“系统版本”字段 */
        private String version = "v1.000";
        /** 协议帧中的“工作状态”字段 */
        private byte workState = 0;
        /** 协议帧中的“保留”字段 */
        private String remain = "1";
        /**
         * 定时发送活动性检测帧
         * 若发出3次未收到应答，则延长发送周期
         */
        @Override
        public void run() {
            try {
                Thread.sleep(1000);
                SharedPreferences.Editor editor=preferences.edit();
                editor.putString("sourceIP",getIP());
                Log.e("test",getIP());
                editor.apply();
                if(commandReceiver==null) {
                    commandReceiver = new CommandReceiver();
                    commandReceiver.start();
                }
                sendTimeRequire();
                ProtocolHeartbeat ph =
                        new ProtocolHeartbeat(preferences, version, workState, remain);
                byte[] sendBuf;
                while (!quit) {
                    if(commandReceiver==null) {
                        commandReceiver = new CommandReceiver();
                        commandReceiver.start();
                    }
                    ph.setSerialNumAndCrc(serialNum);//设置序列号，更新crc
                    sendBuf = ph.outputData();
                    serialNum++;
                    Thread.sleep(sendDelayTime);
                    unReceiveTime++;
                    sendUDPPacket(sendBuf);
                    if (unReceiveTime > 2) {
                        sendDelayTime = 10000;
                        isServerOnline=false;
                        sendBroadcast(new Intent(FlashlightTools.Action_SERVER_OFFLINE));
                    }
                }
            } catch (Exception e) {
                //empty
            }
            heartbeat=null;
        }
    }
    /**
     * 显示终端的命令接收线程
     */
    public class CommandReceiver extends Thread {
        /**
         * 每接收到一个命令后，开一个SolveCommand线程进行解析处理
         * @see SolveCommand
         */
        @Override
        public void run() {
            try {
                SharedPreferences.Editor editor=preferences.edit();
                editor.putString("sourceIP",getIP());
                editor.apply();
                // 接收的字节大小，客户端发送的数据不能超过这个大小
                byte[] message = new byte[1024];
                // 建立Socket连接
                DatagramSocket datagramSocket = new DatagramSocket(sourceUDPPort);
                DatagramPacket datagramPacket = new DatagramPacket(message, message.length);
                byte[] receivedByte;
                int receivedLength;
                while (!quit) {
                    // 准备接收数据
                    datagramSocket.receive(datagramPacket);
                    receivedByte = datagramPacket.getData();
                    receivedLength = datagramPacket.getLength();
                    byte[] proByte = new byte[receivedLength];
                    System.arraycopy(receivedByte, 0, proByte, 0, receivedLength);
                    //测试
                    FlashlightTools.protocolOut("receive",proByte);
                    new SolveCommand(proByte).start();
                }
                datagramSocket.close();
            } catch (Exception e) {
                //empty
            }
            commandReceiver=null;
        }
    }
    /**
     * 命令处理线程
     */
    public class SolveCommand extends Thread {
        /** 协议帧全体字节 */
        private byte[] proByte;
        /** 协议帧中“数据”字段 */
        private byte[] data;

        /**
         *  SolveCommand构造方法
         * @param proByte 协议帧全体字节
         */
        SolveCommand(byte[] proByte) {
            this.proByte = proByte;
        }

        /**
         * 从CommandReceiver线程接收命令并根据命令要求进行处理
         * @see CommandReceiver
         */
        @Override
        public void run() {
            ProtocolAnalysis pa = new ProtocolAnalysis(proByte);
            //报文是否正确
            if (!pa.correct)
                return;
            //活动性检测帧的应答
            if (pa.serviceType == (byte) 0xB1 && pa.command == (byte) 0x81) {
                if(!isServerOnline) {
                    sendBroadcast(new Intent(FlashlightTools.Action_SERVER_ONLINE));
                    sendTimeRequire();
                }
                unReceiveTime = 0;
                sendDelayTime = 5000;
                isServerOnline=true;
            }
            //照片操作指令
            else if (pa.serviceType == (byte) 0xB5 && pa.command == (byte) 0x01) {
                data = pa.data;
                char serNum = (char)(0x00ff & data[0]);
                serNum <<= 8;
                serNum |= data[1];
                boolean qua = (data[2] == 0x00);
                int length = data[3];
                byte[] nameOfPhoto = new byte[length];
                System.arraycopy(data, 4, nameOfPhoto, 0, length);
                Intent opeIntent = new Intent();
                opeIntent.putExtra("photoName", FlashlightTools.photoNameMap(nameOfPhoto));
                if (qua) {
                    opeIntent.setAction(FlashlightTools.Action_PHOTO_CHECK_SUCCESS);
                    Log.e("test","合格");
                }
                else {
                    opeIntent.setAction(FlashlightTools.Action_PHOTO_CHECK_FAILED);
                    Log.e("test", "不合格");
                }
                sendBroadcast(opeIntent);
                //发送照片操作指令的应答
                ProtocolPhotoOperateReply ppor=new ProtocolPhotoOperateReply(preferences,serNum);
                byte[] sendBuf=ppor.outputData();
                try{
                    sendUDPPacket(sendBuf);
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
            //系统时间修改
            else if (pa.serviceType == (byte) 0xB4 && pa.command == (byte) 0x01) {
                data = pa.data;
                int dateTemp;
                dateTemp = 2000 + data[0];
                StringBuilder dateStr = new StringBuilder();
                dateStr.append(dateTemp);
                for (int i = 1; i < 6; i++)
                    dateStr.append(0x00ff & data[i]);
                LocalTime localTime = new LocalTime(preferences);
                localTime.setTime(dateStr.toString());
                ProtocolSetTimeNotice pstn=new ProtocolSetTimeNotice(preferences);
                try{
                    sendUDPPacket(pstn.outputData());
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 发送UDP包到远程显示终端
     * 调度线程间对UDP端口的使用
     * @param sendBuf 发送的UDP报文的内容
     * @throws IOException
     */
    private void sendUDPPacket(byte[] sendBuf)throws IOException{
        DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, address, destinationUDPPort);
        synchronized (this){
            client.send(sendPacket);
        }
        FlashlightTools.protocolOut("send",sendBuf);
    }

    /**
     * 获取wifi网关的ip地址
     * @return wifi网关的ip地址
     */
    private String getIP() {
        WifiManager wifiService = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiinfo = wifiService.getConnectionInfo();
        return intToIp(wifiinfo.getIpAddress());
    }

    /**
     * 将int格式的ip地址转为String格式
     * @param i int格式的ip地址
     * @return String格式的ip地址
     */
    private String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF)
                + "." + (i >> 24 & 0xFF);
    }
    private void sendTimeRequire(){
        ProtocolTimeSynchronizeRequire ptsr=new ProtocolTimeSynchronizeRequire(preferences);
        try{
            sendUDPPacket(ptsr.outputData());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}