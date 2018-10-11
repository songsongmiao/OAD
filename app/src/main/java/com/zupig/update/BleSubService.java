package com.zupig.update;

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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;

public class BleSubService extends Service {

    private final String TAG  = "BlueService";
    private final boolean isDebug = false;
    private String mDeviceName;
    private static boolean isConnect = false;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private int size = 0;//扫描当前多少条数据

    /***
     * 当前的连接请求一次成功可能性较低
     * 可以做三次循环请求
     */
    private boolean isFirst  = false;

    private BluetoothAdapter.LeScanCallback mScanCallback= new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            String name = device.getName();
            String address = device.getAddress();
            if(name == null) return ;
            if(isDebug) Log.i(TAG,"name : "+ name + "\t address : "+ address);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(mCallBack!= null) mCallBack.onDevice(device,rssi);

                }
            });
        }
    };

    private BluetoothAdapter.LeScanCallback mScanConnectCallback= new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if(size < 0 ) size = 0;
            String name = device.getName();
            String address = device.getAddress();
            if(name == null) return ;
            if(isDebug) Log.i(TAG,"name : "+ name + "\t address : "+ address);
            size  +=1;
            if(name.equals(mDeviceName)) // 连接设备
            {
                if(mDeviceName == null || mBluetoothAdapter== null)
                    return ;
                mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
                mBluetoothGatt = mBluetoothDevice.connectGatt(BleSubService.this,false,mBluetoothGattCallback);//连接当前的设备,初始化蓝牙Gatt 对象
                mBluetoothAdapter.stopLeScan(mScanCallback);
                size = 0;
            }
        }
    };

    //常用命令特征值：
    private BluetoothGattCharacteristic mCommonCharaceristic;
    private BluetoothGattCharacteristic mHeadCharacteristic;
    private BluetoothGattCharacteristic mBodyCharacteristic;
    private BluetoothGattCharacteristic mFinishCharacteristic;
    private BluetoothGattCharacteristic mSoftCharacterisitc;
    private BluetoothGattCharacteristic mFireCharacteristic;

    private CallBack mCallBack;
    public void setCallback(CallBack mCallBack)
    {
        this.mCallBack = mCallBack;
    }

    public BluetoothDevice getDevice()
    {
        return mBluetoothDevice;
    }

    public void setmBluetoothDevice(BluetoothDevice mBluetoothDevice)
    {
        this.mBluetoothDevice = mBluetoothDevice;
    }


    //BLE参数信息
    public final String UUID_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public final String UUID_GATT = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public final String UUID_DESCRIPTOR="00002902-0000-1000-8000-00805f9b34fb";


    private final Lock mLock = new ReentrantLock();
    private Queue<BleQuest> orderQueue = new ArrayBlockingQueue<BleQuest>(30);


    /**
     * 蓝牙回调数据代码
     */
    /**
     * 蓝牙回调数据代码
     */

    private final byte[] setHeart = {0x30,0x00,0x00};
    private final int COMMAND = 0x900;
    private int index = -1;



    /**
     * 蓝牙命令
     */
    public final static String SET_CONNECT= "AT+BLEConnect";


    /***
     * OAD 文件处理
     */
    private final int OAD_BLOCK_SIZE = 16;
    private byte[] mFileBuffer ;
    private ImageA image = new ImageA();
    private String softVersion;
    private String hardVeriosn;

    public void setDeviceName(String deviceName)
    {
        if(deviceName == null)
        {
            this.mDeviceName = null;
            return ;
        }
        this.mDeviceName = deviceName;
    }

    public String  getDeviceName()
    {
        return  this.mDeviceName;
    }


    private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what)
            {
                case COMMAND:
                    if(!isConnect)
                    {
                        orderQueue.clear();
                        mHandler.removeMessages(COMMAND);
                        return ;
                    }
                    if(orderQueue.size() > 0 )
                    {
                        mLock.lock();
                        BleQuest mBleRuest = orderQueue.poll();
                        BluetoothGattCharacteristic mCharacteristic = mBleRuest.mCharacteristic;
                        boolean isWrite = mBleRuest.isWrite;
                        if(mBluetoothGatt != null && mCharacteristic != null)
                        {
                            if(isWrite)
                            {
                                mBluetoothGatt.writeCharacteristic(mCharacteristic);
                            }
                            else
                            {
                                mBluetoothGatt.readCharacteristic(mCharacteristic);
                            }
                            byte[] data = mCharacteristic.getValue();
                            int[] bleCode = arrayByteToInt(data);
                            String message  = intArrToString(bleCode);
                            Log.i(TAG,"当前的数据： "+ message + "\t order: "+new String(data));

                        }
//                        preQueue.remove(mBleRuest);
                        mLock.unlock();
                        mHandler.sendEmptyMessageDelayed(COMMAND,30);
                    }
                    else
                    {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if(orderQueue.size() > 0 )
                                    mHandler.sendEmptyMessageDelayed(COMMAND,300);
                                else
                                    mHandler.removeMessages(COMMAND);
                            }
                        },800);

                    }
                    break;
                case 0x902:
                    isConnect = false;
                    onConnect();
            }


        }
    };


    public BleBinder mBinder = new BleBinder();

    public class BleBinder extends Binder {

        public BleSubService getService()
        {
            return BleSubService.this;
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    private void addOrder(BleQuest request)
    {
        mLock.lock();
        orderQueue.add(request);
        mLock.unlock();
    }

    public void onConnect()
    {
        if(isConnect)
        {
            if(isDebug)Log.i(TAG,"onScan 当前连接已经成功");
            return;
        }
        if(mDeviceName == null)
        {
//            throw new NullPointerException("Please set the device name before scanning the device!");
            //弹一个提示框，退出本界面
            return ;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mCallBack!= null) mCallBack.onConnect(3);
            }
        });
        if(mBluetoothAdapter == null)
        {
            final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = manager.getAdapter();
        }
        boolean isAccess = mBluetoothAdapter.isEnabled();
        if(!isAccess)
            mBluetoothAdapter.enable();
        boolean success = mBluetoothAdapter.startLeScan(mScanConnectCallback);
        if(!success)
        {
            //提示用户扫描失败
            mBluetoothAdapter.stopLeScan(mScanConnectCallback);
            size = -1;
            if(!isConnect)
            {
                if(mCallBack!= null) mCallBack.onConnect(0);
            }
        }
        else
        {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mBluetoothAdapter == null)
                    {
                        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
                        mBluetoothAdapter = manager.getAdapter();
                    }
                    mBluetoothAdapter.stopLeScan(mScanConnectCallback); //5秒后停止扫描
                    if(!isConnect)
                    {
                        if(mCallBack!= null) mCallBack.onConnect(0);
                    }
                }
            },3000);
        }
    }



    public boolean isMatch()
    {
        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            return false;
        }
        return true;

    }

    public void onRestartConnect()
    {
        onDisConnected();
        closeAccess();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                openAccessBlue();
            }
        },1500);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                onConnect();
            }
        },8000);
    }


    public void openAccessBlue()
    {
        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        mBluetoothAdapter.enable();
    }

    public void closeAccess()
    {
        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        mBluetoothAdapter.disable();

    }


    public void onScann()
    {
        if(isConnect)
        {
            if(isDebug)Log.i(TAG,"onScan 当前连接已经成功");
            return;
        }
        if(mDeviceName == null)
        {
            return ;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mCallBack!= null) mCallBack.onConnect(3);
            }
        });
        if(mBluetoothAdapter == null)
        {
            BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = manager.getAdapter();
        }
        boolean success = mBluetoothAdapter.startLeScan(mScanConnectCallback);
        if(!success)
        {
            //提示用户扫描失败
            mBluetoothAdapter.stopLeScan(mScanConnectCallback);
            size = -1;
            if(!isConnect)
            {
                if(mCallBack!= null) mCallBack.onConnect(0);
            }
        }
        else
        {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mScanConnectCallback); //5秒后停止扫描
                    if(!isConnect)
                    {
                        if(mCallBack!= null) mCallBack.onConnect(0);
                    }
                }
            },3000);
        }

    }

    /**
     *  将时间转换成租期命令格式
     *  @param mDay 天数
     *  @param mHour 小时
     *  @param mMin 分钟
     *  @param mSecond 秒
     *  @return 租期命令
     */
    public static byte[] timeToRent(long mDay,long mHour,long mMin,long mSecond){

        byte[] rentTime={0x41,0x54,0x2B,0x44,0x65,0x61,0x64,0x4C,0x69,0x6E,0x65,0x3D,0x0,0x0,0x0,0x0,0x0};//H<=0x18 M<=3C S<=60
        rentTime[12]=(byte)(mDay>>8);
        rentTime[13]=(byte)mDay;
        rentTime[14]=(byte)mHour;
        rentTime[15]=(byte)mMin;
        rentTime[16]=(byte)mSecond;
        return rentTime;
    }


    /************************************ * 测试区域代码 begin * *************************************************/
    public void onScanAllDevice()
    {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        if(isDebug) Log.i(TAG,"扫描周围的设备！");
        boolean success = mBluetoothAdapter.startLeScan(mScanCallback);
        if(!success && mCallBack != null)
            mCallBack.onConnect(0);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mBluetoothAdapter != null )
                {
                    mBluetoothAdapter.stopLeScan(mScanCallback);
                }
            }
        },3000);
    }

    public void onStopScanAllDevice()
    {
        if(mBluetoothAdapter != null )
        {
            mBluetoothAdapter.stopLeScan(mScanCallback);
        }
    }

    public void onConnectDevice(BluetoothDevice mBluetoothDevice)
    {
        this.mBluetoothDevice = mBluetoothDevice;
        mBluetoothGatt = mBluetoothDevice.connectGatt(BleSubService.this,false,mBluetoothGattCallback);//连接当前的设备,初始化蓝牙Gatt 对象
    }

    /************************************ * 测试区域代码 end * *************************************************/



    public void onDisConnected()
    {
        isConnect = false;
        if (mBluetoothGatt == null) {
            return;
        }

        onStopScanAllDevice();
        orderQueue.clear();
        mBluetoothGatt.close();
        mBluetoothGatt.disconnect();
        mBluetoothGatt = null;
        mBluetoothAdapter = null;
        mBluetoothDevice = null;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCallBack.onConnect(0);
            }
        });
    }

    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback(){

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(isDebug) Log.i(TAG,"onConnectionStateChange 蓝牙连接状态改变： "+ newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnect = true;
                isFirst = false;
                mBluetoothGatt.discoverServices();//初始化服务
                if(mCallBack != null)
                {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCallBack.onConnect(2);
                        }
                    });

                }

            }
            else if(newState == BluetoothProfile.STATE_DISCONNECTED)
            {

                isConnect = false;
                if(mBluetoothGatt!= null) mBluetoothGatt.close();
                mBluetoothGatt = null;
                if(isDebug) Log.e(TAG,"onConnectionStateChange 连接失败");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallBack.onConnect(0);
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(isDebug) Log.i(TAG,"onServicesDiscovered 蓝牙连接状态改变： "+ status);
            initService();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {

            int[] bleCode = arrayByteToInt(characteristic.getValue());
            String message  = intArrToString(bleCode);
            if(isDebug) Log.i(TAG,"onCharacteristicRead  message + "+ bleCode.length + "\t "+ new String(characteristic.getValue()));
            if(characteristic.getUuid().toString().contains("2a28"))
            {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(mCallBack != null)
                        {
                            softVersion=  new String(characteristic.getValue());
                            mCallBack.onRead(softVersion,1);
                        }
                    }
                });

            }
            else if(characteristic.getUuid().toString().contains("2a26"))
            {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(mCallBack != null)
                        {
                            hardVeriosn=  new String(characteristic.getValue());
                            hardVeriosn = hardVeriosn.replace("A","10");
                            hardVeriosn = hardVeriosn.replace("B","11");
                            hardVeriosn = hardVeriosn.replace("C","12");
                            hardVeriosn = hardVeriosn.replace("D","13");
                            hardVeriosn = hardVeriosn.replace("E","14");
                            hardVeriosn = hardVeriosn.replace("F","15");

                            mCallBack.onRead(hardVeriosn,3);
//                            checkOADStatue();
                            boolean isCharacter = hardVeriosn.matches("^[a-zA-Z_0-9.]+$");
                            if(isDebug) Log.i(TAG,"onCharacteristicRead  包含字母：  "+ isCharacter);
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            try {
                byte[] info = characteristic.getValue();
                if(info == null) return ;
                int[] bleCode = arrayByteToInt(info);
                String message = intArrToString(bleCode);
                if (isDebug)
                    Log.i(TAG, "onCharacteristicWrite  message: " + message + " status:" + status);
            }catch(Exception e )
            {
                Log.e(TAG,"onCharacteristicWrite has occur :"+ e.getMessage());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            int[] bleCode = arrayByteToInt(characteristic.getValue());
            String message  = intArrToString(bleCode);
            if(isDebug) Log.i(TAG,"onCharacteristicChanged 蓝牙设备值设置变化： "+ message + "\t order: "+ new String(characteristic.getValue()));
            if(characteristic.getUuid().toString().contains("ffc4"))
            {
                if(isDebug) Log.i(TAG,"onCharacteristicChanged ffc4 ："+ intArrToString(bleCode)+ "\t 当前的返回值："+ characteristic.getValue());

            }
            else if(characteristic.getUuid().toString().contains("ffc2")){
                if(isDebug) Log.i(TAG,"onCharacteristicChanged ffc2 ："+ intArrToString(bleCode)+ "\t 当前的返回值："+ characteristic.getValue());

            }
            else if(characteristic.getUuid().toString().contains("ffc1"))
            {
                if(isDebug) Log.i(TAG,"onCharacteristicChanged ffc1 ："+ intArrToString(bleCode)+ "\t 当前的返回值："+ characteristic.getValue());

            }
            else
            {
                onReceiverData(characteristic.getValue());
            }

        }

    };

    /***
     * 初始化当前的特征值服务
     */
    private void initService()
    {

        List<BluetoothGattService> mGattServiceList = mBluetoothGatt.getServices();
        if(mGattServiceList == null) return ;
        for (final BluetoothGattService gattService : mGattServiceList) {
            if(gattService.getUuid().toString().equals(UUID_SERVICE)) {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                    /**UUID_KEY_DATA是可以跟蓝牙模块串口通信的Characteristic*/
                    if (gattCharacteristic.getUuid().toString().equals(UUID_GATT)) {

                        /**读取当前Characteristic数据，会触发mOnDataAvailable.onCharacteristicRead()*/
                        mCommonCharaceristic = gattCharacteristic;

                        /**接受Characteristic被写的通知,收到蓝牙模块的数据后会触发mOnDataAvailable.onCharacteristicWrite()*/
                        setNotificationForCharacteristic(gattCharacteristic, true);

                        if(isDebug)Log.e(TAG,"initService UUID_GATT " + gattCharacteristic.getWriteType() +"\t getProperties:"+gattCharacteristic.getProperties() +"\t getPermissions:"+gattCharacteristic.getPermissions());
                    }
                }
            }
            else if(gattService.getUuid().toString().contains("ffc0"))  //处理OAD固件升级
            {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                    /**UUID_KEY_DATA是可以跟蓝牙模块串口通信的Characteristic*/
                    if (gattCharacteristic.getUuid().toString().contains("ffc1")) {

                        mHeadCharacteristic = gattCharacteristic;
                        mHeadCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        setIndicationForCharacteristic(gattCharacteristic, true);
                        if(isDebug)Log.e(TAG,"ffc1 \t" + gattCharacteristic.getWriteType() +"\tgetProperties:"+gattCharacteristic.getProperties() +"\tgetPermissions:"+gattCharacteristic.getPermissions());

                    }
                    else if(gattCharacteristic.getUuid().toString().contains("ffc2"))
                    {
                        mBodyCharacteristic = gattCharacteristic;
                        mBodyCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        setIndicationForCharacteristic(gattCharacteristic, true);
                        if(isDebug)Log.e(TAG,"ffc2\t " + gattCharacteristic.getWriteType() +"\tgetProperties:"+gattCharacteristic.getProperties() +"\tgetPermissions:"+gattCharacteristic.getPermissions());
                    }
                    else if(gattCharacteristic.getUuid().toString().contains("ffc4"))
                    {
                        mFinishCharacteristic = gattCharacteristic;
                        setIndicationForCharacteristic(gattCharacteristic, true);

                        if(isDebug)Log.e(TAG,"ffc4 \t" + gattCharacteristic.getWriteType() +"\tgetProperties:"+gattCharacteristic.getProperties() +"\tgetPermissions:"+gattCharacteristic.getPermissions());
                    }
                }
            }
            else if(gattService.getUuid().toString().contains("180a"))
            {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                for ( BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    if (gattCharacteristic.getUuid().toString().contains("2a28")) {
//                        setNotificationForCharacteristic(gattCharacteristic, true);
                        //获取软件版本号：
                        final BluetoothGattCharacteristic mCharacter = gattCharacteristic;
                        mCharacter.setValue(ENABLE_NOTIFICATION_VALUE);
                        mSoftCharacterisitc = mCharacter;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if(mCharacter == null || mBluetoothGatt == null ) return ;
                                mBluetoothGatt.readCharacteristic(mCharacter);

                            }
                        },1200);
                    }
                    else if(gattCharacteristic.getUuid().toString().contains("2a26"))
                    {
                        final BluetoothGattCharacteristic mCharacter = gattCharacteristic;
                        mCharacter.setValue(ENABLE_NOTIFICATION_VALUE);
                        mFireCharacteristic = mCharacter;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if(mCharacter == null || mBluetoothGatt == null ) return ;
                                mBluetoothGatt.readCharacteristic(mCharacter);
                            }
                        },1500);
                    }

                }
            }
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendCommonData(SET_CONNECT);
            }
        },80);
    }



    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param ch Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     * @return true or false
     */
    private void setNotificationForCharacteristic(BluetoothGattCharacteristic ch, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) return;

        boolean success = mBluetoothGatt.setCharacteristicNotification(ch, enabled);

        final BluetoothGattDescriptor descriptor = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if(descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);

        }


    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void setIndicationForCharacteristic(BluetoothGattCharacteristic ch, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) return;

        boolean success = mBluetoothGatt.setCharacteristicNotification(ch, enabled);

        BluetoothGattDescriptor aa = ch.getDescriptor(UUID.fromString(UUID_DESCRIPTOR));
        if(aa != null)
        {

            if(isDebug)Log.e(TAG, ch.getUuid().toString()+":BluetoothGattDescriptor:" +success);
            aa.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(aa);
        }
    }



    /************************************** *  蓝牙常用通信 begin  * ***************************************************/

    public void sendCommonData(String order)
    {
        if(mBluetoothGatt == null || mCommonCharaceristic == null)
        {
            Log.e(TAG,"常用的命令特征服务丢失！----发送命令："+ order);
            //判断连接状态，如果连接标志为true 强制断开连接，重新扫描当前的设备，如果扫描失败，提示用户连接的设备未找到

            return ;
        }
        mCommonCharaceristic.setValue(order);
        BleQuest request = new BleQuest();
        request.isWrite = true;
        request.mCharacteristic = mCommonCharaceristic;
        addOrder(request);
        mHandler.sendEmptyMessage(COMMAND);
    }

    public void sendCommonData(String order,int time)
    {
        if(mBluetoothGatt == null || mCommonCharaceristic == null)
        {
            Log.e(TAG,"常用的命令特征服务丢失！----发送命令："+ order);
            //判断连接状态，如果连接标志为true 强制断开连接，重新扫描当前的设备，如果扫描失败，提示用户连接的设备未找到

            return ;
        }
        mCommonCharaceristic.setValue(order);
        BleQuest request = new BleQuest();
        request.isWrite = true;
        request.mCharacteristic = mCommonCharaceristic;
        addOrder(request);
        mHandler.sendEmptyMessageDelayed(COMMAND,time);
    }


    public void sendCommonData(byte[] order)
    {
        if(mBluetoothGatt == null || mCommonCharaceristic == null)
        {
            Log.e(TAG,"常用的命令特征服务丢失！");
            //判断连接状态，如果连接标志为true 强制断开连接，重新扫描当前的设备，如果扫描失败，提示用户连接的设备未找到

            return ;
        }
        mCommonCharaceristic.setValue(order);
        BleQuest request = new BleQuest();
        request.isWrite = true;
        request.mCharacteristic = mCommonCharaceristic;
        addOrder(request);
        mHandler.sendEmptyMessage(COMMAND);
    }

    public void readComman(String key)
    {
        if(mBluetoothGatt == null )
        {
            Log.e(TAG,"常用的命令特征服务丢失！");
            //判断连接状态，如果连接标志为true 强制断开连接，重新扫描当前的设备，如果扫描失败，提示用户连接的设备未找到

            return ;
        }
        BleQuest request = new BleQuest();
        request.isWrite = false;
        addOrder(request);
        mHandler.sendEmptyMessage(COMMAND);
    }

    public void getInformation()
    {
        if(mFireCharacteristic != null)
        {
            BleQuest request = new BleQuest();
            request.isWrite = false;
            request.mCharacteristic = mFireCharacteristic;
            addOrder(request);
        }
        if(mSoftCharacterisitc != null)
        {
            BleQuest request = new BleQuest();
            request.isWrite = false;
            request.mCharacteristic = mSoftCharacterisitc;
            addOrder(request);
            mHandler.sendEmptyMessage(COMMAND);
        }
    }

    private void readOADData()
    {
        if(mBluetoothGatt == null || mFinishCharacteristic == null)
        {
            Log.e(TAG,"常用的命令特征服务丢失！");
            //判断连接状态，如果连接标志为true 强制断开连接，重新扫描当前的设备，如果扫描失败，提示用户连接的设备未找到

            return ;
        }
        mFinishCharacteristic.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        BleQuest request = new BleQuest();
        request.isWrite = false;
        request.mCharacteristic = mFinishCharacteristic;
        addOrder(request);
    }

    public boolean isConnect()
    {
        return isConnect;
    }

    /***
     * 蓝牙数据接收
     * @param code
     */
    private void onReceiverData(final byte[] code)
    {

        if(isDebug)Log.e(TAG,"onReceiverData\t " + code[0]);
        if(mCallBack == null)
        {
            throw new NullPointerException("please implement interface of CallBack!");
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mCallBack.onReceive(code);
            }
        });
        if(code[0] == 48)
        {
            sendCommonData(setHeart);
        }

    }




    /**
     * 将数组转换成字符串输出
     * @param intArray
     * @return
     */
    public static String intArrToString(int[] intArray){
        String msg="";
        for (int i= 0; i< intArray.length; i++)
        {
            if(i==(intArray.length-1)){
                msg=msg+Integer.toHexString(intArray[i]);
            }
            else{
                msg=msg+Integer.toHexString(intArray[i])+",";
            }

        }
        return msg;
    }

    /**
     * 将byte类型数组转为int类型数组
     * @params mByte数组
     * @return
     */
    public static int[] arrayByteToInt(byte[] mByte){

        int[] mInt=new int[mByte.length];
        for (int i= 0; i< mByte.length; i++)
        {
            mInt[i]=unsignedByteToInt(mByte[i]);
        }
        return mInt;
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    public static int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }


    /************************************** *  蓝牙常用通信 end   * ***************************************************/








    /************************************** * OAD 文件在线下载begin * *********************************************/
    private final String BaseURLOAD = "http://app.zupig.com/bcoadbin/";
    //判断接入点方式（net/wap），来建立连接
    private HttpURLConnection getConnection(String address) throws Exception {
        HttpURLConnection conn = null;

        @SuppressWarnings("deprecation")
        String proxyHost = android.net.Proxy.getDefaultHost();
        if (proxyHost != null) {
            // wap方式，要加网关
            @SuppressWarnings("deprecation")
            java.net.Proxy p = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new InetSocketAddress(android.net.Proxy.getDefaultHost(),
                            android.net.Proxy.getDefaultPort()));
            conn = (HttpURLConnection) new URL(address).openConnection(p);
        } else {
            conn = (HttpURLConnection) new URL(address).openConnection();
        }
        return conn;
    }


    /***
     * 检查是否需要更新
     */
    public void checkOADStatue()
    {
        if(softVersion == null || hardVeriosn == null)
        {
            return ;
        }
        Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                try{
                    String address = BaseURLOAD + softVersion + ".txt";
                    String newFileInformation = onLoadData(address);
                    String[] fileList = newFileInformation.split("=");
                    String newVersion = fileList[0];
                    Log.e(TAG,"比较当前的硬件版本号： "+ newVersion + "\t hardVersion: "+ hardVeriosn);
                    if (newVersion.contains(hardVeriosn)) {
                        //当前已经是最新版本
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(mCallBack != null)
                                {
                                    mCallBack.onRead("",5);
                                    if(isDebug) Log.i(TAG,"当前的OAD 为最新版本！");
                                }
                            }
                        });
                    }
                    else
                    {
                        //可以经进行更新
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(mCallBack != null)
                                {
                                    mCallBack.onRead("",4);
                                    if(isDebug) Log.i(TAG,"当前的OAD 需要升级！");
                                }
                            }
                        });
                    }
                }catch(Exception e)
                {
                    if(isDebug) Log.e(TAG,"检测OAD升级文件失败！"+ e.getMessage());
                }


            }
        };
        Thread thread = new Thread(mRunnable);
        thread.start();
    }


    /***
     * 检查是否需要更新
     */
    public void checkOADStatue(final String verion)
    {
        Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                try{
                    if(softVersion == null) return ;
                    String address = BaseURLOAD + verion;
                    String newFileInformation = onLoadData(address);
                    String[] fileList = newFileInformation.split("=");
                    String newVersion = fileList[1];
                    Log.e(TAG,"比较当前的硬件版本号： "+ newVersion + "\t hardVersion: "+ hardVeriosn + "\t adress: "+ address+ "\t text: "+ newFileInformation);
                    if (newVersion.contains(softVersion)) {
                        //当前已经是最新版本
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(mCallBack != null)
                                {
                                    mCallBack.onRead("",5);
                                    if(isDebug) Log.i(TAG,"当前的OAD 为最新版本！");
                                }
                            }
                        });
                    }
                    else
                    {
                        //可以经进行更新
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(mCallBack != null)
                                {
                                    mCallBack.onRead("",4);
                                    if(isDebug) Log.i(TAG,"当前的OAD 需要升级！");
                                }
                            }
                        });
                    }
                }catch(Exception e)
                {
                    if(isDebug) Log.e(TAG,"检测OAD升级文件失败！"+ e.getMessage());
                }


            }
        };
        Thread thread = new Thread(mRunnable);
        thread.start();
    }


    /***
     * 进行OAD 在线升级
     */
    public void onUpgradeFile(String version)
    {
        this.softVersion = version;
        Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                try{
                    String address = BaseURLOAD + softVersion ;
                    if(isDebug) Log.e(TAG,"当前地址："+ address);
                    String newFileInformation = onLoadData(address);
                    String[] fileList = newFileInformation.split("=");
                    //可以进行更新
                    String updateVersion = fileList[1];
                    updateOAD(updateVersion);
                }catch(Exception e)
                {
                    if(isDebug) Log.e(TAG,"检测OAD升级文件失败！"+ e.getMessage());
                }

            }
        };

        Thread thread = new Thread(mRunnable);
        thread.start();
    }


    //加载在线OAD文件的文件名
    private String onLoadData(String address)
    {
        HttpURLConnection conn = null;
        String response = null;
        try{
            conn = getConnection(address);
            conn.setConnectTimeout(6 * 1000);
            conn.setDoInput(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
                response = buffer.readLine();
                buffer.close();
                is.close();
            }
        }
        catch(Exception e)
        {
            if(isDebug)Log.e(TAG,"加载文件失败： "+ e.getMessage());
        }
        return response;
    }



    //加载在线OAD 文件的文件内容
    private InputStream onDownLoadOAD(String address)
    {
        HttpURLConnection conn = null;
        InputStream response = null;
        try{
            conn = getConnection(address);
            conn.setConnectTimeout(6 * 1000);
            conn.setDoInput(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                response = conn.getInputStream();
            }
        }
        catch(Exception e)
        {
            if(isDebug)Log.e(TAG,"加载文件失败： "+ e.getMessage());
        }
        return response;
    }


    //初始化OAD 文件中的内容
    private void initOADFile(InputStream inputStream)
    {
        //读取文件
        try {
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream() ;
            // 开始读取数据
            int len = 0;// 每次读取到的数据的长度
            while ((len = inputStream.read(buffer)) != -1) {// len值为-1时，表示没有数据了
                // append方法往sb对象里面添加数据
                outputStream.write(buffer, 0, len);
            }
            // 输出字符串
            mFileBuffer = outputStream.toByteArray();
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            Log.e("BleActivity", "" + e.getMessage());
            return;
        }
        Log.i(TAG,"initA 当前的数组长度： "+ mFileBuffer.length);
        image.initTotal(mFileBuffer);
    }


    //OAD 文件传输到蓝牙终端
    private void updateOAD(String updateVersion)
    {
        if (isDebug) Log.i(TAG, "onUpgradeFile address :  " +BaseURLOAD+ updateVersion);
        InputStream mInputStream = onDownLoadOAD( BaseURLOAD +  updateVersion);
        initOADFile(mInputStream);

        byte[] data = null;
        index = -1;
        while (index <  image.nBlocks)
        {
            if(mBluetoothGatt == null || mHeadCharacteristic == null)
            {
                Log.e(TAG,"常用的命令特征服务丢失！");
                //判断连接状态，如果连接标志为true 强制断开连接，重新扫描当前的设备，如果扫描失败，提示用户连接的设备未找到
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallBack.onRead("OAD upgrade failed!", 2);
                    }
                });
                return ;
            }
            index = index + 1;
            boolean isStatue;
            if(index == 0 )
            {
                data = getByte(true,0);
                mHeadCharacteristic.setValue(data);
                isStatue = mBluetoothGatt.writeCharacteristic(mHeadCharacteristic);
            }
            else
            {
                data = getByte(false,index);
                mBodyCharacteristic.setValue(data);
                isStatue = mBluetoothGatt.writeCharacteristic(mBodyCharacteristic);
            }
            //计算百分比
            if(!isStatue)
            {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(mCallBack != null) {
                            mCallBack.onRead("OAD upgrade failed!", 2);
                        }
                    }
                });
                break;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String percentage = index+ "/"+ image.nBlocks;
                    if(index == image.nBlocks )
                    {
                        percentage = "Wait for Bluetooth to automatically disconnect and restart...";
                        //手动检测,12秒后自动连接断开蓝牙扫描
                    }
                    if(mCallBack != null)
                    {
                        mCallBack.onProgress(percentage);
                        Log.i(TAG,"百分比： "+ percentage+"\t degree: "+ percentage + "\t "+ image.nBlocks);
                    }

                }
            });
            try {
                Thread.sleep(30);
            }catch(Exception e)
            {
                Log.e(TAG,"OAD升级异常 : " + e.getMessage());
            }
        }
        readOADData();

    }



    private byte[] getByte(Boolean isHead,int index)
    {
        byte[] fileByte  = null;
        fileByte = isHead? new byte[16]: new byte[18];
        fileByte = fillData(fileByte);
        if(isHead)
        {
//            System.arraycopy(mFileBuffer,image.iBytes,fileByte,0,16);
            for(int i = 0;i<16;i++)
            {
                fileByte[i] = mFileBuffer[i+image.iBlocks];
            }
        }
        else
        {
            image.iBlocks = index;
            image.iBytes =  image.iBlocks* 16;
            int currentIndex = image.iBlocks -1;

            fileByte[0] =Conversion.loUint16((short) currentIndex);
            fileByte[1] =  Conversion.hiUint16((short) currentIndex);
            for (int i = 0; i<16 ; i++) {

                if(i + image.iBytes <mFileBuffer.length)
                {
                    fileByte[i+2] = mFileBuffer[i +  image.iBytes];
                }

            }


        }
        return fileByte;
    }

    private byte[] fillData(byte[] data)
    {
        for(int i=0;i<data.length;i++)
        {
            data[i] = (byte) 0xff;
        }
        return data;
    }

    private class ImageA{
        public int iBytes = 0; // Number of bytes programmed
        public int iBlocks = 0; // Number of blocks programmed
        public int nBlocks = 0; // Total number of blocks

        public void initTotal(byte[] mFileBuffer)
        {
            if(mFileBuffer == null) return ;
            nBlocks = mFileBuffer.length/OAD_BLOCK_SIZE;
            Log.e("BlueService", "nBlocks:" + nBlocks + "mFileBuffer.length:"+mFileBuffer.length);
        }
    }

    /************************************** * OAD 文件在线下载 end* *********************************************/

}
