package com.omni.ble.library.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.TextUtils;
import android.util.Log;

import com.omni.ble.library.model.GattAttributes;
import com.omni.ble.library.order.BLEOrderManager;
import com.omni.ble.library.utils.BikeLockCommand;
import com.omni.ble.library.utils.CRCUtil;
import com.omni.ble.library.utils.CommandUtil;
import com.omni.lib.utils.PrintUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * Description:蓝牙服务 <br />
 */
public class HorseLockService extends Service {

    private final static String TAG = "HorseLockService";
    //蓝牙连接成功广播
    public final static String ACTION_BLE_CONNECTED = "com.pgt.lockble.ACTION_BLE_CONNECTED";
    //蓝牙连接失败广播
    public final static String ACTION_BLE_DISCONNECTED = "com.pgt.lockble.ACTION_BLE_DISCONNECTED";
    public final static String ACTION_BLE_SERVICE_NO_FIND = "com.pgt.lockble.ACTION_BLE_SERVICE_NO_FIND";
    //写完通知广播
    public final static String ACTION_BLE_WRITE_NOTIFY = "com.pgt.lockble.ACTION_BLE_WRITE_NOTIFY";
    //获得key广播
    public final static String ACTION_BLE_GET_KEY = "com.pgt.lockble.ACTION_BLE_GET_KEY";
    //开锁广播
    public final static String ACTION_BLE_LOCK_OPEN_STATUS = "com.pgt.lockble.ACTION_BLE_LOCK_OPEN_STATUS";
    //锁的状态广播
    public final static String ACTION_BLE_LOCK_STATUS = "com.pgt.lockble.ACTION_BLE_LOCK_STATUS";
    //上锁广播
    public final static String ACTION_BLE_LOCK_CLOSE_STATUS = "com.pgt.lockble.ACTION_BLE_LOCK_CLOSE_STATUS";
    //清除旧数据广播
    public final static String ACTION_BLE_LOCK_CLEAR_DATA = "com.pgt.lockble.ACTION_BLE_LOCK_CLEAR_DATA";
    //旧数据广播
    public final static String ACTION_BLE_HAVE_OLD_DATA = "com.pgt.lockble.ACTION_BLE_HAVE_OLD_DATA";
    //获得设备数据超时广播(其中包含：key，锁状态，旧数据)
    public final static String ACTION_BLE_GET_DATA_TIME_OUT = "com.pgt.lockble.ACTION_BLE_GET_DATA_TIME_OUT";
    //开锁扫描
    public static final String ACTION_BLE_SCAN_START = "com.pgt.pedelec.ACTION_BLE_SCAN_START";
    //停止扫描
    public static final String ACTION_BLE_SCAN_STOP = "com.pgt.pedelec.ACTION_BLE_SCAN_STOP";
    // 扫描超时
    public static final String ACTION_BLE_SCAN_TIMEOUT = "com.pgt.pedelec.ACTION_BLE_SCAN_TIMEOUT";
    //扫描到指定的设备
    public static final String ACTION_BLE_SCAN_DEVICE = "com.pgt.pedelec.ACTION_BLE_SCAN_DEVICE";
    //体统蓝牙开关打开广播
    public static final String ACTION_BLE_STATE_ON = "com.pgt.lock.ble.gprs.ACTION_BLE_STATE_ON";
    //获取固件信息完毕
    public static final String ACTION_BLE_HORSE_LOCK_FW_INFO = "com.pgt.lock.ble.gprs.ACTION_BLE_HORSE_LOCK_FW_INFO";
    // 分包获取固件信息
    public static final String ACTION_BLE_HORSE_LOCK_FW_INFO_ING = "com.pgt.lock.ble.gprs.ACTION_BLE_HORSE_LOCK_FW_INFO_ING";
    // 固件升级传输
    public static final String ACTION_BLE_HORSE_LOCK_FW_UPGRADE = "com.pgt.lock.ble.gprs.ACTION_BLE_HORSE_LOCK_FW_UPGRADE";
    // 锁信息修改
    public static final String ACTION_BLE_HORSE_LOCK_INFO_MODIFY = "com.pgt.lock.ble.gprs.ACTION_BLE_HORSE_LOCK_INFO_MODIFY";

    //连接蓝牙设备时的 连接状态
    public static final int STATE_DISCONNECTED = 0;//断开连接
    public static final int STATE_CONNECTING = 1;//正在连接
    public static final int STATE_CONNECTED = 2;//找到蓝牙连接
    public static final int STATE_FIND_SERVICE = 3;//找到服务
    public static final int STATE_BIND_CONNECTED = 4;//已经连接过蓝牙的状态

    //蓝牙断开连接，重新连接设备
    public static final int STATE_CLOSE_GATT_RECONNECT = 16;
    // 用来标识是否自动连接
    private boolean autoConnect = false;

    //handler 手机蓝牙开启时连接设备
    private final static int HANDLER_WRITE_KEY_ERROR = 0;
    private final static int HANDLER_BLE_ON_CONNECT = 2;
    private final static int HANDLER_DISCOVER_SERVICES = 30;
    private final static int HANDLER_WHAT_DISCONNECT_BLE = 55;

    //检测当前连接状态，并查看是否重新连接
    private final static int HANDLER_STATE_CONNECT = 3;
    private final static int HANDLER_STATE_CONNECT_DELAYED_TIME = 10_000;//间隔时间5s

    private final IBinder mBinder = new LocalBinder();

    private BLEOrderManager orderManager;
    private BluetoothManager mBLEManager;
    private BluetoothAdapter mBLEAdapter;
    private BluetoothGatt mBLEGatt = null;

    private BluetoothGattCharacteristic mBLEGCWrite; // 基本 写
    private BluetoothGattCharacteristic mBLEGCRead; // 基本 读

    public int mConnectionState = 0; //蓝牙连接的状态
    public byte BLECkey = 0;//获得蓝牙的key
    private boolean isGetKey = false;//是否获得到key标记
    private boolean isLockStatus = false;//是否获得到锁的状态标记
    private boolean isOldData = false;//是否获得到旧数据标记
    private static final int HANDLER_GET_KEY = 2001;
    private static final int HANDLER_GET_LOCK_STATUS = 2002;
    private static final int HANDLER_GET_OLD_DATA = 2003;
    private static final int HANDLER_GET_UNLOCK = 2004;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLER_GET_UNLOCK:
                    // 解锁
                    if (isUnlock) {
                        Log.i(TAG, "handleMessage: 已解锁");
                    } else {
                        sendLocalBroadcast(ACTION_BLE_GET_DATA_TIME_OUT);//发广播给开锁界面处理(本地广播)
                        sendBroadcast(new Intent(ACTION_BLE_GET_DATA_TIME_OUT));//发广播给主界面处理
                    }
                    break;
                case HANDLER_GET_KEY://检查是否获得到key
                    // 5s 后没有收到key提示用户重新扫描
                    if (isGetKey == false) {
                        sendLocalBroadcast(ACTION_BLE_GET_DATA_TIME_OUT);//发广播给开锁界面处理(本地广播)
                        sendBroadcast(new Intent(ACTION_BLE_GET_DATA_TIME_OUT));//发广播给主界面处理
                    } else {
                        Log.i(TAG, "handleMessage: 已经获取到KEY");
                    }
                    break;
                case HANDLER_GET_LOCK_STATUS://检查是否获得到锁状态
                    // 5s 后没有收到锁状态提示用户重新扫描
                    if (isLockStatus == false) {
                        sendLocalBroadcast(ACTION_BLE_GET_DATA_TIME_OUT);//发广播给开锁界面处理(本地广播)
                        sendBroadcast(new Intent(ACTION_BLE_GET_DATA_TIME_OUT));//发广播给主界面处理
                    } else {
                        Log.i(TAG, "handleMessage: 已经获取到锁状态");
                    }
                    break;
                case HANDLER_GET_OLD_DATA://检查是否获得到旧数据
                    // 5s 后没有收到旧数据提示用户重新扫描
                    if (isOldData == false) {
                        sendLocalBroadcast(ACTION_BLE_GET_DATA_TIME_OUT);//发广播给开锁界面处理(本地广播)
                        sendBroadcast(new Intent(ACTION_BLE_GET_DATA_TIME_OUT));//发广播给主界面处理
                    } else {
                        Log.i(TAG, "handleMessage: 已经获取到旧数据");
                    }
                    break;
                case HANDLER_WHAT_DISCONNECT_BLE://关锁
                    Log.d(TAG, "关锁, 断开连接");
//                    disconnect();
                    break;
                case HANDLER_WRITE_KEY_ERROR:
                    Log.i(TAG, "handleMessage:连接蓝牙时，key 写错误");
                    break;
                case HANDLER_DISCOVER_SERVICES://发现服务
                    Log.i(TAG, "connect: 发现服务");
                    discoverServices(mBLEGatt);//去找服务
                    break;
                case HANDLER_BLE_ON_CONNECT://系统蓝牙开关打开
                    //在app中，开启蓝牙时去自动连接
                    if (!TextUtils.isEmpty(scanForStemMAC)) {
                        startLoopScan(scanForStemMAC);
                    }
                    break;
                case HANDLER_STATE_CONNECT://检测蓝牙的连接状态
                    if (mConnectionState != STATE_BIND_CONNECTED) {
                        int preConnectionState = msg.arg1; //之前的连接状态
                        if (preConnectionState == mConnectionState) {
                            //5s中后还没有改变原来的连接状态
                            //还没有绑定连接
                            int state = 0;
                            try {
                                state = mBLEManager.getConnectionState(mBLEAdapter.getRemoteDevice(scanForStemMAC), BluetoothProfile.GATT);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (mBLEGatt != null) {
                                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                    mBLEGatt.disconnect();
                                    mBLEGatt.close();
                                    mBLEGatt = null;
                                    Log.d(TAG, "handleMessage: 184");
                                    startLoopScan(scanForStemMAC);
                                } else {
                                    Log.i(TAG, "handleMessage: 192 断开连接");
                                    mBLEGatt.disconnect();
                                }
                            }
                        } else if (preConnectionState == STATE_CLOSE_GATT_RECONNECT) {//蓝牙断开连接
                            Log.d(TAG, "连接超时, 扫描蓝牙");
                            startLoopScan(scanForStemMAC);
                        }
                    }
                    break;
            }
        }
    };

    //标记当前是否在扫描状态
    private boolean mScanning = false;
    //标记是否扫描到设备
    private boolean isFindStem = false;
    //标记是否循环的去扫描
    private boolean isLoopScan = false;
    //mac地址
    public String scanForStemMAC = "";
    private boolean isUnlock;

    private UUID service;
    private UUID write;
    private UUID notify;

    public void setService(UUID uuid) {
        service = uuid;
    }

    public void setWrite(UUID uuid) {
        write = uuid;
    }

    public void setNotify(UUID uuid) {
        notify = uuid;
    }

    /**
     * 开始循环扫描，并扫描把立
     */
    public void startLoopScan(String mac) {
        Log.i(TAG, "startLoopScan: 自动重连  扫描把立设备");
        //此方法是获取所有已经连接的设备，如果传过来的mac地址已经连接就直接返回
        List<BluetoothDevice> connectedDevices = mBLEManager.getConnectedDevices(BluetoothProfile.GATT);
        for (BluetoothDevice bd : connectedDevices) {
            if (mac.equals(bd.getAddress())) {
                Log.i(TAG, "startLoopScan:  当前设备已经连接 mac=" + mac);
                return;
            }
        }
        isFindStem = false;
        scanForStemMAC = mac;
        // 不需要循环扫描
        isLoopScan = false;
        scanLeDeviceS(true);
    }

    /**
     * 每次扫描扫10s,每隔5s 扫描一次
     *
     * @param enable
     */
    public void scanLeDeviceS(final boolean enable) {
        if (enable) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    scanForStemMAC = "";
                    mBLEAdapter.stopLeScan(serviceLescanCallback);
                    sendLocalBroadcast(ACTION_BLE_SCAN_TIMEOUT);
                }
            }, 10000);
            if (!mScanning) {
                //没有在扫描设备，则扫描设备
                mScanning = true;
                mBLEAdapter.startLeScan(serviceLescanCallback);
                sendLocalBroadcast(ACTION_BLE_SCAN_START);
            }
        } else {
            Log.d(TAG, "scanLeDeviceS 停止扫描");
            mScanning = false;
            mBLEAdapter.stopLeScan(serviceLescanCallback);
            sendLocalBroadcast(ACTION_BLE_SCAN_STOP);
        }
    }

    public void stopScan() {

        if (mBLEAdapter != null && mScanning) {
            Log.d(TAG, "停止扫描");
            mScanning = false;
            scanForStemMAC = "";
            handler.removeCallbacksAndMessages(null);
            mBLEAdapter.stopLeScan(serviceLescanCallback);
            sendLocalBroadcast(ACTION_BLE_SCAN_STOP);
        }

        if (mBLEGatt != null && !isConnected) {
            Log.d(TAG, "连接失败并停止扫描, 断开gatt连接");
            disconnect();
        }
    }

    /**
     * 扫描附近蓝牙回调类
     */
    private final BluetoothAdapter.LeScanCallback serviceLescanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            String address = device.getAddress();
            Log.d(TAG, "扫描到设备: " + address);
            if (scanForStemMAC.equals(address)) {
                // 找到了设备 把立设备
                isFindStem = true;
                isLoopScan = false; // 扫描到设备，停止 循环扫描
                Log.i(TAG, "onLeScan: 扫描到自动重连的设备");
                sendLocalBroadcast(ACTION_BLE_SCAN_DEVICE);
            }
        }
    };

    /**
     * 扫描蓝牙时广播监听类
     */
    private final BroadcastReceiver sanDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BLE_SCAN_STOP.equals(intent.getAction())) {
                if (!mScanning) {
                    return;
                }
                // 停止扫描时
                Log.i(TAG, "onReceive: 停止扫描设备");
                Log.i(TAG, "isLoopScan=" + isLoopScan);
                Log.i(TAG, "isFindStem: " + isFindStem);
                if (!isLoopScan) {
                    Log.i(TAG, "onReceive: 停止 循环扫描设备");
                    return;
                }
                if (!isFindStem) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // 停止扫描后，5秒后，继续扫描
                            Log.i(TAG, "run: 自动重连，停止扫描后，5s后继续开始");
                            startLoopScan(scanForStemMAC);
                        }
                    }, 5000);
                }
            } else if (ACTION_BLE_SCAN_START.equals(intent.getAction())) {
                // 开始扫描时
                Log.i(TAG, "onReceive: 开始扫描设备的广播");
            } else if (ACTION_BLE_SCAN_DEVICE.equals(intent.getAction())) {
                Log.i(TAG, "onReceive: 扫描到 把立 设备");
                // 已经扫描到设备，停止扫描
                if (mScanning) scanLeDeviceS(false);
                SystemClock.sleep(100);
                //开始连接设备
                autoConnect(scanForStemMAC);
            }
        }
    };

    private boolean isConnected;
    /**
     * 连接蓝牙回调类
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /**当连接上设备或者失去连接时会回调该函数**/
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {//蓝牙连接成功
                if (mConnectionState == STATE_BIND_CONNECTED) {//说明已经连接过了
                    //已经配对过设备了，这个是为了防止连接一个设备的时候同时多次发现设备
                    return;
                }
                //设备连接
                mConnectionState = STATE_CONNECTED;
                isConnected = true;

                Message msg = new Message();
                msg.what = HANDLER_STATE_CONNECT;
                msg.arg1 = STATE_CONNECTED;
                handler.sendMessageDelayed(msg, HANDLER_STATE_CONNECT_DELAYED_TIME);
                sendLocalBroadcast(ACTION_BLE_CONNECTED);

                //延迟600ms 发送 发现服务的handler ,
                handler.sendEmptyMessageDelayed(HANDLER_DISCOVER_SERVICES, 600);
                Log.i(TAG, "connect: 蓝牙连接成功");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//蓝牙连接失败
                isConnected = false;
                //设备断开连接
                //保存设备断开连接状态
                mConnectionState = STATE_DISCONNECTED;
                //发送断开连接的广播
                sendLocalBroadcast(ACTION_BLE_DISCONNECTED);
                gatt.close();
                Log.d(TAG, "断开连接gatt是否与当前gatt一致:" + (mBLEGatt == gatt));
                if (mBLEGatt == gatt) {
                    mBLEGatt = null;
                }
                Log.d(TAG, "onConnectionStateChange:  蓝牙断开连接");
                //TODO 自动连接蓝牙设备
                if (autoConnect) {
                    Log.i(TAG, "onConnectionStateChange:  蓝牙断开连接，并自动连接");
                    Message msg = new Message();
                    msg.what = HANDLER_STATE_CONNECT;
                    msg.arg1 = STATE_CLOSE_GATT_RECONNECT;
                    handler.sendMessageDelayed(msg, HANDLER_STATE_CONNECT_DELAYED_TIME);
                }
            }
        }

        /**当设备是否找到服务时，会回调该函数**/
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            BluetoothGattService bleGattService = gatt.getService(service);
            if (bleGattService != null) {
                Log.i(TAG, "onServicesDiscovered: 找到并发现蓝牙服务");
                mConnectionState = STATE_FIND_SERVICE;
                Message msg = new Message();
                msg.what = HANDLER_STATE_CONNECT;
                msg.arg1 = STATE_FIND_SERVICE;
                handler.sendMessageDelayed(msg, HANDLER_STATE_CONNECT_DELAYED_TIME);

                //控制功能
                mBLEGCWrite = bleGattService.getCharacteristic(write);
                //参数配置
                mBLEGCRead = bleGattService.getCharacteristic(notify);
                orderManager = new BLEOrderManager(gatt);
                setCharacteristicNotification(mBLEGCRead);
            } else {
                Log.i(TAG, "onServicesDiscovered: 没有发现服务: " + service);
                sendLocalBroadcast(ACTION_BLE_SERVICE_NO_FIND);
            }
        }

        /**设备发出通知时会调用到该接口**/
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] values = characteristic.getValue();
            if (values.length == 2 && values[0] == 32 && values[1] == 0) return;
            if (values.length == 2 && values[0] == 0) return; // 提示
            Log.i(TAG, "onCharacteristicChanged: Recv: " + getCommForHex(values));

            if (isGetInfo && values.length == 20) {
                // 读取锁信息
                if (CRCUtil.CheckFirstCRC16(values)) {
                    byte[] command = new byte[values.length - 2];
                    System.arraycopy(values, 2, command, 0, values.length - 2);
                    onHandFirmwareDataCommand(command);
                }
                return;
            }


            int start = 0;
            int copyLen = 0;
            for (int i = 0; i < values.length; i++) {
                if ((values[i] & 0xFF) == 0xFE) {
                    start = i;
                    int randNum = (values[i + 1] - 0x32) & 0xFF; //BF
                    int len = ((values[i + 4]) & 0xFF) ^ randNum;
                    copyLen = len + 7; //16+
                    // 提前出当前指令
                    byte[] real = new byte[copyLen];
                    System.arraycopy(values, start, real, 0, copyLen);
                    Log.i(TAG, "onCharacteristicChanged: real 0x= " + getCommForHex(real));
                    handRealCommand(real);
                    i = start + 4 + len + 2; // start+ 4 是长度字节所在位置，+len + 2个字节CRC校验值
                    continue;
                }
            }
            if (copyLen == 0) return;
        }

        private void handRealCommand(byte[] realCommand) {
            byte[] command = new byte[realCommand.length - 2];
            command[0] = realCommand[0]; // 包头
            if (CRCUtil.CheckCRC(realCommand)) {// crc校验成功
                byte head = (byte) (realCommand[1] - 0x32);
                command[1] = head;
                for (int i = 2; i < realCommand.length - 2; i++) {
                    command[i] = (byte) (realCommand[i] ^ head);
                }
                handCommand(command);
            } else {// CRC校验失败
                Log.i(TAG, "onCharacteristicChanged: CRC校验失败！！！");
                if (writeType == TYPE_WRITE_GET_KEY) {
                    Log.i(TAG, "onCharacteristicChanged: 获取KEY失败,断开蓝牙连接并重连！！！");
                    if (mBLEGatt != null) {
                        mBLEGatt.disconnect();//断开蓝牙连接
                    }
                    autoConnect = true;//标志可重连
                }
            }
        }


        /**当向Characteristic写数据时会回调该函数**/
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "write recv: " + getCommForHex(characteristic.getValue()));
            // 写成功
            orderManager.removeFirst();
            if (writeType == TYPE_WRITE_LOCK_RESPONSE) {
                // 是写 收到上锁指令的返回
                // 收到上锁指令，关闭蓝牙
                autoConnect = false;
                scanForStemMAC = "";//防止自动连接
                handler.sendEmptyMessageDelayed(HANDLER_WHAT_DISCONNECT_BLE, 1000);
            }
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            orderManager.removeFirst();
            //读成功
        }

        /**当写完通知时会回调该函数**/
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //写完移动数据的通知
            gatt.setCharacteristicNotification(mBLEGCRead, true);
            mConnectionState = STATE_BIND_CONNECTED;
            sendLocalBroadcast(ACTION_BLE_WRITE_NOTIFY);//发送本地广播(开锁界面用到)
            Intent intent = new Intent(ACTION_BLE_WRITE_NOTIFY);
            sendBroadcast(intent);//发送系统广播(主界面用到，因为划掉App或退出登录再重新进来要连接蓝牙的时候(仅限于骑行中))
            Log.i(TAG, "connect: 通知写成功");
        }
    };


    private String getCommForHex(byte[] values) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(String.format("%02X,", values[i]));
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }

    /**
     * 处理设备发出的所有数据通知
     *
     * @param command
     */
    private void handCommand(byte[] command) {
        Log.i(TAG, "handCommand: command CODE= " + String.format("0x%02X", command[3]));
        switch (command[3]) {
            case 0x11://获得key
                handKey(command);
                break;
            case 0x21://开锁
                handLockOpen(command);
                break;
            case 0x22://关锁
                handLockClose(command);
                break;
            case 0x31://获得锁的状态
                handLockStatus(command);
                break;
            case 0x51://获得旧数据
                handOldData(command);
                break;
            case 0x52://清除旧数据
                handClearData(command);
            case (byte) 0xFA:// 固件信息
                handFirmwareInfoData(command);
                break;
            case (byte) 0xF1:
                // 固件升级
                handFwUpgradeData(command);
                break;
            case (byte) 0xFD:
                // 锁信息修改
                handLockInfoModifyData(command);
                break;
            case (byte) 0xFF:
                // 完成
                handFinishData(command);
                break;
        }
    }

    /**
     * 收到0xFF, 需要回复
     * @param command
     */
    private void handFinishData(byte[] command) {
        byte cmd = command[5];
        Log.d(TAG, "获取信息的指令: " + cmd);

        byte[] crcOrder = CommandUtil.getFinishCommand(BLECkey, cmd);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
    }

    /**
     * 锁信息修改
     */
    private void handLockInfoModifyData(byte[] command) {
        int pack = (command[5] << 8 & 0xFF) | (command[6] & 0xFF);
        int deviceType = command[7] & 0xFF;

        Intent intent = new Intent(ACTION_BLE_HORSE_LOCK_INFO_MODIFY);
        intent.putExtra("pack", pack);
        intent.putExtra("deviceType", deviceType);
        sendLocalBroadcast(intent);
    }

    /**
     * 固件升级
     *
     * @param command
     */
    private void handFwUpgradeData(byte[] command) {
        int high = command[5] & 0xFF;
        int low = command[6] & 0xFF;


        int currentPack = (high << 8) | low;
        int deviceType = command[7] & 0xFF;

        Intent intent = new Intent(ACTION_BLE_HORSE_LOCK_FW_UPGRADE);
        intent.putExtra("currentPack", currentPack);
        intent.putExtra("deviceType", deviceType);
        sendLocalBroadcast(intent);
    }

    /**
     * 已经获得到key,处理key
     *
     * @param command
     */
    private void handKey(byte[] command) {
        isGetKey = true;
        BLECkey = command[5];
        Log.i(TAG, "handKey: key=0x" + String.format("%02X", BLECkey));
        sendLocalBroadcast(ACTION_BLE_GET_KEY);//发送本地广播(开锁界面用到)
        Intent intent = new Intent(ACTION_BLE_GET_KEY);
        sendBroadcast(intent);//发送系统广播(主界面用到，因为划掉App或退出登录再重新进来要连接蓝牙的时候(仅限于骑行中))
        Log.i(TAG, "connect: key获取成功");
    }

    /**
     * 处理开锁成功
     *
     * @param command
     */
    private void handLockOpen(byte[] command) {
        isUnlock = true;
        int status = command[5];
        long timestamp = ((command[6] & 0xFF) << 24) | ((command[7] & 0xff) << 16) | ((command[8] & 0xFF) << 8) | (command[9] & 0xFF);
        Intent intent = new Intent(ACTION_BLE_LOCK_OPEN_STATUS);
        intent.putExtra("status", status);
        intent.putExtra("timestamp", timestamp);
        sendLocalBroadcast(intent);
        Log.i(TAG, "handLockOpen: 开锁成功");
    }


    public static final String EXTRA_OPEN_STATUS = "openStatus";
    public static final String EXTRA_POWER = "power";
    public static final String EXTRA_HAS_OLD = "hasOld";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    /**
     * 处理锁的状态
     *
     * @param command
     */
    private void handLockStatus(byte[] command) {
        isLockStatus = true;
        int openStatus = command[5];
        int power = command[6];
        int hasOld = command[7];
        long timestamp = ((command[8] & 0xFF) << 24) | ((command[9] & 0xff) << 16) | ((command[10] & 0xFF) << 8) | (command[11] & 0xFF);
        Intent intent = new Intent(ACTION_BLE_LOCK_STATUS);
        intent.putExtra(EXTRA_OPEN_STATUS, openStatus);
        intent.putExtra(EXTRA_POWER, power);
        intent.putExtra(EXTRA_HAS_OLD, hasOld);
        intent.putExtra(EXTRA_TIMESTAMP, timestamp);
        sendLocalBroadcast(intent);//发送本地广播(开锁界面用到)
        sendBroadcast(intent);//发送系统广播(主界面用到，因为划掉App或退出登录再重新进来要连接蓝牙的时候(仅限于骑行中))
        Log.i(TAG, "connect: 获取锁的状态成功");
    }

    /**
     * 处理关锁
     *
     * @param command
     */
    private void handLockClose(byte[] command) {
        isUnlock = false;
        autoConnect = false;
        scanForStemMAC = "";//防止自动连接
        int status = command[5];
        long timestamp = ((command[6] & 0xFF) << 24) | ((command[7] & 0xff) << 16) | ((command[8] & 0xFF) << 8) | (command[9] & 0xFF);
        int runTime = ((command[10] & 0xFF) << 24) | ((command[11] & 0xff) << 16) | ((command[12] & 0xFF) << 8) | (command[13] & 0xFF);
        Intent intent = new Intent(ACTION_BLE_LOCK_CLOSE_STATUS);
        intent.putExtra("status", status);
        intent.putExtra("runTime", runTime);
        intent.putExtra("timestamp", timestamp);
        sendLocalBroadcast(intent);//发送本地广播(开锁界面用到)
        sendBroadcast(intent);//发广播给主界面处理
        Log.i(TAG, "handLockClose: service接收到设备关锁指令，发广播给主界面处理或开锁界面处理");
    }

    /**
     * 处理旧数据
     *
     * @param command
     */
    private void handOldData(byte[] command) {
        isOldData = true;
        int timestamp1 = command[5] & 0xFF;
        int timestamp2 = command[6] & 0xFF;
        int timestamp3 = command[7] & 0xFF;
        int timestamp4 = command[8] & 0xFF;
        long timestamp = timestamp4 | (timestamp3 << 8) | (timestamp2 << 16) | (timestamp1 << 24);
        int runTime1 = command[9] & 0xFF;
        int runTime2 = command[10] & 0xFF;
        int runTime3 = command[11] & 0xFF;
        int runTime4 = command[12] & 0xFF;
        int runTime = runTime4 | (runTime3 << 8) | (runTime2 << 16) | (runTime1 << 24);
        int uid1 = command[13] & 0xFF;
        int uid2 = command[14] & 0xFF;
        int uid3 = command[15] & 0xFF;
        int uid4 = command[16] & 0xFF;
        int uid = uid4 | (uid3 << 8) | (uid2 << 16) | (uid1 << 24);
        Intent intent = new Intent(ACTION_BLE_HAVE_OLD_DATA);
        intent.putExtra("uid", uid);
        intent.putExtra("runTime", runTime);
        intent.putExtra("timestamp", timestamp);
        sendLocalBroadcast(intent);//发送本地广播(开锁界面用到)
        sendBroadcast(intent);//发广播给主界面处理
        Log.i(TAG, "handOldData: 获得旧数据成功");
    }

    /**
     * 处理清除旧数据
     *
     * @param command
     */
    private void handClearData(byte[] command) {
        int status = command[5];
        Intent intent = new Intent(ACTION_BLE_LOCK_CLEAR_DATA);
        intent.putExtra("status", status);
        sendLocalBroadcast(intent);
        Log.i(TAG, "handClearData: 清除旧数据成功");
    }

    int firmwareInfoCRC16 = 0;

    /**
     * 处理固件总包数信息
     *
     * @param command
     */
    private void handFirmwareInfoData(byte[] command) {
        int len = command[4];
        // 系统信息
        totalPack = ((command[5] & 0xff) << 8) | (command[6] & 0xff);
        firmwareInfoCRC16 = ((command[7] & 0xff) << 8) | (command[8] & 0xff);
        byte deviceType = command[9];
        Log.i(TAG, "handFirmwareInfoData: totalPack=" + totalPack);
        Log.i(TAG, "handFirmwareInfoData: firmwareInfoCRC16 =" + String.format("0x%02X", firmwareInfoCRC16));
        Log.i(TAG, "handFirmwareInfoData: deviceType=" + deviceType);
        // 获取第一包 固件信息
        sendGetFirmwareInfoDetail(0, deviceType);
    }

    private List<Byte> dataBytes = new ArrayList<>();
    int infoCurSavePack = -1;
    protected int totalPack = 0;

    /**
     * 处理获取固件信息, 一包一包传
     *
     * @param command
     */
    public void onHandFirmwareDataCommand(byte[] command) {
        int nPack = ((command[0] & 0xFF) << 8) | (command[1] & 0xFF);
        Log.i(TAG, "onHandFirmwareDataCommand: 第几包数据=" + nPack);
        Log.i(TAG, "onHandFirmwareDataCommand:  固件信息=" + PrintUtil.toHexString(command));
        if (infoCurSavePack != nPack) {
            for (int i = 2; i < command.length; i++) {
                dataBytes.add(command[i]);
            }
            infoCurSavePack = nPack;
        }

        if (nPack == totalPack - 1) {
            // 最后一包数据
            // 将接收到的数据转换成String
            isGetInfo = false;
            int len = 0;
            for (int i = dataBytes.size() - 1; i >= 0; i--) {
                byte item = dataBytes.get(i);
                if (item != (byte) 0x00) {
                    len = i + 1;
                    break;
                }
            }

            byte[] allFirmwareData = new byte[len];
            for (int i = 0; i < len; i++) {

                allFirmwareData[i] = dataBytes.get(i);
            }

            int calcCRC16 = CRCUtil.calcCRC16(allFirmwareData);
            Log.i(TAG, "onHandFirmwareDataCommand: 计算的CRC16=" + String.format("0x%02X", calcCRC16));

            // 将byte[] 数组转化成String
            String firmwareData = new String(allFirmwareData);
            Log.i(TAG, "onHandFirmwareDataCommand: 获取到的固件信息=" + firmwareData);

            Intent intent = new Intent(ACTION_BLE_HORSE_LOCK_FW_INFO);
            intent.putExtra("firmwareData", firmwareData);
            sendLocalBroadcast(intent);

            if (calcCRC16 == firmwareInfoCRC16) {
                Log.i(TAG, "onHandFirmwareDataCommand: 获取到的固件信息=" + firmwareData);
            } else {
                Log.i(TAG, "onHandFirmwareDataCommand:获取到的CRC 和计算CRC 不一样");
            }
        } else {
            // 将获取固件信息的过程 回调出去。
            Intent intent = new Intent(ACTION_BLE_HORSE_LOCK_FW_INFO_ING);
            // 当前返回的是第几包数据
            intent.putExtra("nPack", nPack);
            intent.putExtra("totalPack", totalPack);
            sendLocalBroadcast(intent);
        }
    }

    /**
     * 发送 本地广播
     */
    private void sendLocalBroadcast(final String action) {
        final Intent intent = new Intent(action);
        sendLocalBroadcast(intent);
    }

    /**
     * 发送 本地广播
     */
    private void sendLocalBroadcast(final Intent intent) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * 获取KEY
     */
    public void getOpenKey(String bleKey) {
        isGetKey = false;
        writeType = TYPE_WRITE_GET_KEY;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized: " + this);
            return;
        }
        byte[] crcOrder = CommandUtil.getCRCKeyCommand2(bleKey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        // 5s 后检查是否接受到key
        handler.sendEmptyMessageDelayed(HANDLER_GET_KEY, HANDLER_STATE_CONNECT_DELAYED_TIME);
        Log.i(TAG, "getOpenKey: 去获得key");
    }

    private int writeType = 0;
    private static int TYPE_WRITE_LOCK_STATUS = 1;// 获取锁状态 写类型
    private static int TYPE_WRITE_LOCK_RESPONSE = 0; // 收到上锁数据的回复 写类型
    private static int TYPE_WRITE_GET_KEY = 2; // 获取 KEY 的写类型
    private static int TYPE_WRITE_CLEAR_OLD_DATA = 3; // 写 清除 旧数据   写类型
    private static int TYPE_WRITE_OPEN_RESPONSE = 4; // 写 清除 旧数据   写类型

    /**
     * 关锁成功回复锁
     */
    public void setLockResponse() {
        writeType = TYPE_WRITE_LOCK_RESPONSE;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getCRCLockCommand(BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.i(TAG, "sendCloseResponse: 主界面关锁成功，发指令回复锁");
    }

    /**
     * 开锁成功回复锁
     */
    public void sendOpenResponse() {
        writeType = TYPE_WRITE_OPEN_RESPONSE;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getCRCOpenResCommand(BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.i(TAG, "sendOpenResponse: 回复设备开锁成功");
    }

    /**
     * 整车关机
     *
     * @return
     */
    public void sendShutDown() {
        Log.d(TAG, "整车关机");
        byte[] command = BikeLockCommand.getCRCShutDown(BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, command);
    }

    /**
     * 发送开锁指令
     *
     * @param timestamp
     */
    public void setOpenLock(long timestamp, byte openType) throws RuntimeException {
        if (mBLEAdapter == null) {
            throw new RuntimeException("BluetoothAdapter not initialized");
        }
        if (mBLEGatt == null) {
            throw new RuntimeException("device not connected");
        }

        byte[] crcOrder = CommandUtil.getCRCOpenCommand(0, BLECkey, timestamp, openType);
        Log.d(TAG, "open lock 时间戳" + timestamp);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);

        handler.sendEmptyMessageDelayed(HANDLER_GET_UNLOCK, HANDLER_STATE_CONNECT_DELAYED_TIME);
        Log.i(TAG, "setOpenLock: 发送开锁指令");
    }

    /**
     * 获得锁的状态
     */
    public void getLockStatus() {
        isLockStatus = false;
        writeType = TYPE_WRITE_LOCK_STATUS;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getCRCLockStatusCommand(BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);

        // 5s 后检查是否获得到锁状态
        handler.sendEmptyMessageDelayed(HANDLER_GET_LOCK_STATUS, HANDLER_STATE_CONNECT_DELAYED_TIME);
        Log.i(TAG, "getLockStatus: 去获得锁的状态");
    }

    /**
     * 获得旧数据
     */
    public void getOldData() {
        isOldData = false;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getCRCOldDataCommand(BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);

        // 5s 后检查是否获得到旧数据
        handler.sendEmptyMessageDelayed(HANDLER_GET_OLD_DATA, HANDLER_STATE_CONNECT_DELAYED_TIME);
        Log.i(TAG, "getOldData: 去获得旧数据");
    }

    /**
     * 清除旧数据
     */
    public void clearData() {
        writeType = TYPE_WRITE_CLEAR_OLD_DATA;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getCRCClearDataCommand(BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.i(TAG, "clearData: 清除旧数据");
    }

    private boolean isGetInfo = false;

    /**
     * 获取固件信息
     * 返回固件总包数
     */
    public void getFwInfo() {
        dataBytes.clear();
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getFwInfoCommand(0, BLECkey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);

        Log.i(TAG, "getLockStatus: 获取固件信息");
    }

    /**
     * 获取固件信息，一包一包获取
     *
     * @param npack
     * @param deviceType
     * @return
     */
    public void sendGetFirmwareInfoDetail(int npack, byte deviceType) {
        isGetInfo = true;
        if (mBLEAdapter == null) {
            Log.i(TAG, "writeFindBicycle: BluetoothAdapter not initialized");
            return;
        }
        if (mBLEGatt == null) {
            Log.i(TAG, "writeFindBicycle: mBLEGatt not initialized");
            return;
        }
        byte[] crcOrder = CommandUtil.getFwInfoPackCommand(BLECkey, npack, deviceType);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.i(TAG, "getLockStatus: 获取固件信息: pack = " + npack + ", deviceType = " + deviceType);
    }

    /**
     * 启动固件升级
     */
    public void sendUpdateFirmwareCommand(int nPack, int crc, byte deviceType, String updateKey) {
        byte[] crcOrder = CommandUtil.getBikeUpgradeFwCommand(BLECkey, nPack, crc, deviceType, updateKey);
        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.i(TAG, "发送开始升级固件指令");
    }

    /**
     * 发固件升级包
     */
    public void sendFwUpdatePackCommand(int pack, byte[] data) {
        byte[] crcOrder = CommandUtil.getBikeUpgradeFwPackCommand(pack, data);

        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.d(TAG, "发包: pack = " + pack + ", data = " + getCommForHex(crcOrder));
    }

    /**
     * 修改车锁信息
     */
    public void sendConfigModifyCommand(int pack, int crc, byte deviceType) {
        byte[] crcOrder = CommandUtil.getModifyConfigCommand(BLECkey, pack, crc, deviceType);

        orderManager.addWriteOrder(mBLEGCWrite, crcOrder);
        Log.d(TAG, "修改车锁信息");
    }

    /**
     * 设置设备是否发送通知
     *
     * @param characteristic
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic) {
        if (mBLEAdapter == null || mBLEGatt == null) {
            Log.i(TAG, "setCharacteristicNotification: BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(GattAttributes.UUID_NOTIFICATION_DESCRIPTOR);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBLEGatt.writeDescriptor(descriptor);//当写完通知时会回调onDescriptorWrite
        Log.i(TAG, "connect: 写通知");
    }

    /**
     * 找服务
     *
     * @param gatt
     */
    private void discoverServices(BluetoothGatt gatt) {
        if (gatt != null) {
            gatt.discoverServices();//找到服务会回调onServicesDiscovered
            Log.i(TAG, "connect: 找服务");
        }
    }

    /**
     * 连接蓝牙设备，断开时自动重连。
     *
     * @param address 要连接的蓝牙设置地址
     * @return 连接成功返回true，否则返回false
     */
    public boolean autoConnect(final String address) {
        Log.i(TAG, "autoConnect: 自动连接 蓝牙");
        autoConnect = true;
        scanForStemMAC = address;
        return connect(address);
    }

    public boolean connect(final String address) {
        if (mBLEAdapter == null) {
            Log.i(TAG, "connect: BluetoothAdapter 未初始化");
            return false;
        }
        if (TextUtils.isEmpty(address)) {
            Log.i(TAG, "connect: 连接蓝牙的Mac地址为空！");
            return false;
        }
        scanForStemMAC = address;
        //第一次连接设备
        BluetoothDevice device = null;
        try {
            device = mBLEAdapter.getRemoteDevice(address.toUpperCase());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (device == null) {
            Log.i(TAG, "connect: 找不到蓝牙设备，无法连接");
            return false;
        }

        isConnected = false;

        mBLEGatt = device.connectGatt(this, false, mGattCallback);
        orderManager = new BLEOrderManager(mBLEGatt);//初始化指令读写管理器

        mConnectionState = STATE_CONNECTING;

        Message message = new Message();//5s后检测是否连接并配对上设备
        message.what = HANDLER_STATE_CONNECT;
        message.arg1 = STATE_CONNECTING;
        handler.sendMessageDelayed(message, HANDLER_STATE_CONNECT_DELAYED_TIME);
        Log.i(TAG, "connect: 调用了连接蓝牙方法, gatt: " + mBLEGatt);
        return true;
    }

    /**
     * 断开蓝牙连接，并不重连
     */
    public void disconnect() {
        if (mBLEAdapter == null || mBLEGatt == null) {
            Log.i(TAG, "connect: BluetoothAdapter 未初始化");
            return;
        }
        autoConnect = false;
        mConnectionState = STATE_DISCONNECTED;
        scanForStemMAC = "";//防止自动连接
        mBLEGatt.disconnect();
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "connect: 调用了蓝牙断开连接方法");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: 进入服务的初始化方法");
        if (init()) Log.i(TAG, "onCreate: 初始化成功");
        //设置广播监听
        registerReceiver(bleStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        //注册本地广播，主要是扫描设备的时候用到
        IntentFilter ifScanDevice = new IntentFilter();
        ifScanDevice.addAction(ACTION_BLE_SCAN_STOP);
        ifScanDevice.addAction(ACTION_BLE_SCAN_START);
        ifScanDevice.addAction(ACTION_BLE_SCAN_DEVICE);
        LocalBroadcastManager.getInstance(this).registerReceiver(sanDeviceReceiver, ifScanDevice);
    }

    /**
     * 初始化对本地蓝牙适配器的引用
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return true if the initialization is successful.
     */
    public boolean init() {
        if (mBLEManager == null) {
            mBLEManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBLEManager == null) {
                Log.i(TAG, "init: Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBLEAdapter = mBLEManager.getAdapter();
        if (mBLEAdapter == null) {
            Log.i(TAG, "init: Unable to obtain a BluetoothAdapter.");
            return false;
        }
        Log.i(TAG, "init: mBleAdapter=" + mBLEAdapter.toString());
        return true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public HorseLockService getService() {
            return HorseLockService.this;
        }
    }

    @Override
    public boolean stopService(Intent name) {
        close();
        return super.stopService(name);
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     */
    private void close() {
        if (mBLEGatt == null) return;
        mBLEGatt.close();
        mBLEGatt = null;
        Log.d(TAG, "close");
    }

    public boolean isConnectedDevice(String mac) {

        List<BluetoothDevice> deviceList = mBLEManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        Log.i(TAG, "isConnectedDevice: deviceList = " + deviceList.size());
        for (BluetoothDevice bluetoothDevice : deviceList) {
            Method isConnectedMethod = null;
            try {
                isConnectedMethod = BluetoothDevice.class.getDeclaredMethod("isConnected", (Class[]) null);
                isConnectedMethod.setAccessible(true);
                boolean isConnected = (boolean) isConnectedMethod.invoke(bluetoothDevice, (Object[]) null);
                Log.i(TAG, "isConnectedDevice: 放射 返回获取连接状态");
                Log.i(TAG, "isConnectedDevice: isConnected=" + isConnected);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mac.equals(bluetoothDevice.getAddress())) {
                return true;
            }
        }
        Log.i(TAG, "isConnectedDevice: isConnected =false");
        return false;
    }

    /**
     * 用来连接系统蓝牙开关
     */
    private final BroadcastReceiver bleStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                Log.i(TAG, "onReceive: ble  开关状态改变");
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Log.i(TAG, "onReceive: ble  state=" + state);
                    Log.i(TAG, "onReceive: ble  当前状态为关");
                    if (mBLEGatt != null) {
                        mBLEGatt.close();
                        mBLEGatt = null;
                        Log.d(TAG, "ACTION_STATE_CHANGED gatt = null");
                    }
                } else if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "onReceive: ble  state=" + state);
                    Log.i(TAG, "onReceive: ble  当前状态为开");
                    handler.sendEmptyMessageDelayed(HANDLER_BLE_ON_CONNECT, 1000);
                    sendLocalBroadcast(ACTION_BLE_STATE_ON);
                } else if (state == BluetoothAdapter.STATE_TURNING_ON) {
                    Log.i(TAG, "onReceive: ble  state=" + state);
                    Log.i(TAG, "onReceive: ble  当前状态为正在开");
                    autoConnect = true;
                } else {
                    Log.i(TAG, "onReceive: ble  state=" + state);
                    Log.i(TAG, "onReceive: ble  当前状态为正在关");
                    autoConnect = false;
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bleStateReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sanDeviceReceiver);
    }

}
