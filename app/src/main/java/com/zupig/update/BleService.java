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

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;

public class BleService extends Service {
    private final String TAG  = "BlueService";
    private final boolean isDebug = true;
    private String mDeviceName;
//    private boolean isConnect = false;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;


    private BluetoothAdapter.LeScanCallback mScanCallback= new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            String name = device.getName();
            if(name == null) return ;
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
            String name = device.getName();
            String address = device.getAddress();
            if(name == null) return ;
            if(name.equals(mDeviceName)) // 连接设备
            {
                if(mDeviceName == null || mBluetoothAdapter== null)
                    return ;
                if(isDebug) Log.i(TAG,"connect -> name : "+ name + "\t address : "+ address);
                mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(address);
                mBluetoothGatt = mBluetoothDevice.connectGatt(BleService.this,false,mBluetoothGattCallback);//连接当前的设备,初始化蓝牙Gatt 对象
                mBluetoothAdapter.stopLeScan(mScanConnectCallback);
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



    /**
     * 蓝牙命令
     */
    public final static String SET_CONNECT= "AT+BLEConnect";

    /***
     * OAD 文件处理
     */
    private String softVersion;
    private String hardVersion;

    public void setDeviceName(String deviceName)
    {
        if(deviceName == null)
        {
            this.mDeviceName = null;
            return ;
        }
        this.mDeviceName = deviceName;
    }

    private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what)
            {
                case COMMAND:
//                    if(!isConnect)
//                    {
//                        orderQueue.clear();
//                        mHandler.removeMessages(COMMAND);
//                        return ;
//                    }
                    if(orderQueue.size() > 0 )
                    {
                        mLock.lock();
                        BleQuest mBleRuest = orderQueue.poll();//拿到头部数据，并删除爱数据
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
                        }else{
                            orderQueue.clear();
                            mHandler.removeMessages(COMMAND);
                            mLock.unlock();
                            return;
                        }
                        mLock.unlock();
                        mHandler.sendEmptyMessageDelayed(COMMAND,30);
                    }
                    else
                    {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mHandler.removeMessages(COMMAND);
                            }
                        },800);

                    }
                    break;
                case 0x902:
//                    isConnect = false;
                    onConnect();
            }


        }
    };


    private BleBinder mBinder = new BleBinder();

    public class BleBinder extends Binder {

        public BleService getService()
        {
            return BleService.this;
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
//        if(isConnect)
//        {
//            if(isDebug)Log.i(TAG,"onScan 当前连接已经成功");
//            return;
//        }
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
        boolean success = mBluetoothAdapter.startLeScan(mScanConnectCallback);
        if(!success)
        {
            //提示用户扫描失败
            mBluetoothAdapter.stopLeScan(mScanConnectCallback);
            if(mCallBack!= null) mCallBack.onConnect(0);
//            if(!isConnect)
//            {
//                if(mCallBack!= null) mCallBack.onConnect(0);
//            }
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
//                    if(!isConnect)
//                    {
//                        if(mCallBack!= null) mCallBack.onConnect(0);
//                    }
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
//        if(isConnect)
//        {
//            if(isDebug)Log.i(TAG,"onScan 当前连接已经成功");
//            return;
//        }
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
            isMatch();
        }
        boolean success = mBluetoothAdapter.startLeScan(mScanConnectCallback);
        if(!success)
        {
            //提示用户扫描失败
            mBluetoothAdapter.stopLeScan(mScanConnectCallback);
            if(mCallBack!= null) mCallBack.onConnect(0);
//            if(!isConnect)
//            {
//                if(mCallBack!= null) mCallBack.onConnect(0);
//            }
        }
        else
        {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mScanConnectCallback); //5秒后停止扫描
//                    if(!isConnect)
//                    {
//                        if(mCallBack!= null) mCallBack.onConnect(0);
//                    }
                }
            },3000);
        }

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
        if(mBluetoothAdapter == null )
        {
            BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = manager.getAdapter();
        }
        mBluetoothAdapter.stopLeScan(mScanCallback);

    }

    public void onReScan()
    {
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
                onScanAllDevice();
            }
        },6500);

    }

    /************************************ * 测试区域代码 end * *************************************************/



    public void onDisConnected()
    {
//        isConnect = false;
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
//                isConnect = true;
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

//                isConnect = false;
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
                            hardVersion =  new String(characteristic.getValue());
                            hardVersion = hardVersion.replace("A","10");
                            hardVersion = hardVersion.replace("B","11");
                            hardVersion = hardVersion.replace("C","12");
                            hardVersion = hardVersion.replace("D","13");
                            hardVersion = hardVersion.replace("E","14");
                            hardVersion = hardVersion.replace("F","15");

                            mCallBack.onRead(hardVersion,3);
//                            checkOADStatue();
                            boolean isCharacter = hardVersion.matches("^[a-zA-Z_0-9.]+$");
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
//                        if(isDebug)Log.e(TAG,"ffc1 \t" + gattCharacteristic.getWriteType() +"\tgetProperties:"+gattCharacteristic.getProperties() +"\tgetPermissions:"+gattCharacteristic.getPermissions());

                    }
                    else if(gattCharacteristic.getUuid().toString().contains("ffc2"))
                    {
                        mBodyCharacteristic = gattCharacteristic;
                        mBodyCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        setIndicationForCharacteristic(gattCharacteristic, true);
//                        if(isDebug)Log.e(TAG,"ffc2\t " + gattCharacteristic.getWriteType() +"\tgetProperties:"+gattCharacteristic.getProperties() +"\tgetPermissions:"+gattCharacteristic.getPermissions());
                    }
                    else if(gattCharacteristic.getUuid().toString().contains("ffc4"))
                    {
                        mFinishCharacteristic = gattCharacteristic;
                        setIndicationForCharacteristic(gattCharacteristic, true);

//                        if(isDebug)Log.e(TAG,"ffc4 \t" + gattCharacteristic.getWriteType() +"\tgetProperties:"+gattCharacteristic.getProperties() +"\tgetPermissions:"+gattCharacteristic.getPermissions());
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
        },8000);
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

}
