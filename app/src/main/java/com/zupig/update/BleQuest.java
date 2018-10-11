package com.zupig.update;

import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by song on 2018/4/23.
 * 创建一个蓝牙请求的对象
 */

public class BleQuest {

    public int id;
    public boolean isWrite = true;
    public BluetoothGattCharacteristic mCharacteristic;
}
