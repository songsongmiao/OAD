package com.zupig.oad;

import android.Manifest;
import android.app.Dialog;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zupig.update.BleService;
import com.zupig.update.BleSubService;
import com.zupig.update.CallBack;
import com.zupig.update.CustomProgress;
import com.zupig.update.ScanDevice;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements CallBack{

    private String TAG = "MainActivity";
    private final String BaseURLOAD = "http://app.zupig.com/bcoadbin/";
    private final boolean isDebug = true;
    public static final String DEVCENAME  = "DeviceName";
    public static final String UPGRADEVERSION = "UpgradeVersion";
    public static final String UPGRADEHARD = "UpgradeHard";

    private TextView tvOnlineVersion,tvOnlineHard,
            tvLocationVersion,tvLocationHard,
            tvVersion,tvHard,tvStatus,tvName;
    private Button btnDevice,btnConnect,btnDisConnect,
            btnLocationSelect,btnLocation,btnUpdateSelect,btnUpdate;
    private ScrollView mUpdateAction;

    private HashMap<String,ScanDevice> mScanMap = new HashMap<>();

    private BleService mBlueService;
    private Handler mHandler = new Handler();
//    private Dialog mDialog;
    private PopupWindow mPopDialog;

    private CustomProgress mCustomProgress;

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            boolean isAccess = false;
            Intent intent;
            String mUpdateVersion;
            String mDeviceName;
            String mUpdateHard;
            switch(v.getId())
            {
                case R.id.main_connect_select: // devices
                    //检测权限
//                    mDialog = mCustomProgress.showDialog();
                    mPopDialog = mCustomProgress.showPopWindow(v);
                    isAccess = mBlueService.isMatch();
                    if(!isAccess)
                    {
                        mBlueService.openAccessBlue();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                onShowDevice();
                            }
                        },4500);
                    }
                    else
                        onShowDevice();
                    break;

                case R.id.main_connect_online: // connect devices
                    mPopDialog = mCustomProgress.showPopWindow(v);

                    isAccess = mBlueService.isMatch();
                    if(!isAccess)
                    {
                        mBlueService.openAccessBlue();
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                onConnectDevice();
                            }
                        },4500);
                    }
                    else
                        onConnectDevice();
                    break;

                case R.id.main_connect_disconnect: //disconnect devices

                    if(mBlueService!= null)
                    {
                        mBlueService.onDisConnected();
                    }
                    onResetStatus();
                    break;

                case R.id.main_location_select: //location select devices
                    onShowVersion();
                    break;

                case R.id.main_location_update: //jump another activity
                    String currentVersion = tvVersion.getText().toString();
                    String currentHard = tvHard.getText().toString();
                    String localVersion = tvLocationVersion.getText().toString();
                    String localHard = tvLocationHard.getText().toString();
                    if(currentVersion.equals(localVersion) && currentHard.equals(localHard))
                    {
                        mCustomProgress.onToast("重复升级文件请重新选择");
                        return ;
                    }
                    mUpdateVersion = tvLocationVersion.getText().toString();
                    mDeviceName = tvName.getText().toString();
                    mUpdateHard = tvLocationHard.getText().toString();
                    intent = new Intent(MainActivity.this,LocalUpdateActivity.class);
                    intent.putExtra(UPGRADEVERSION,mUpdateVersion);
                    intent.putExtra(DEVCENAME,mDeviceName);
                    intent.putExtra(UPGRADEHARD,mUpdateHard);
                    startActivityForResult(intent,0X01);
                    onResetStatus();
//                    startActivity(intent);
//                    MainActivity.this.finish();
                    if(mBlueService!= null)
                    {
                        mBlueService.onDisConnected();
                    }

                    break;

                case R.id.main_online_refresh: // get last version
                    onRefreshVerison();
                    break;

                case R.id.main_online_update: // jump another activity  --- online

                    String mCurrentVersion = tvVersion.getText().toString();
                    String mCurrentHard = tvHard.getText().toString();
                    String mOnlineVersion = tvOnlineVersion.getText().toString();
                    String mOnlineHard = tvOnlineHard.getText().toString();
                    if(mCurrentVersion.equals(mOnlineVersion))
                    {
                        boolean isUpdate = isCompare(mCurrentHard,mOnlineHard);
                        if(!isUpdate)
                        {
                            mCustomProgress.onToast("车辆蓝牙已经更新到最新的版本！");
                            return ;
                        }

                    }
                    //断开连接
                    if(mBlueService!= null)
                    {
                        mBlueService.onDisConnected();
                    }
                    mUpdateVersion = tvOnlineVersion.getText().toString();
                    mDeviceName = tvName.getText().toString();
                    mUpdateHard = tvOnlineHard.getText().toString();
                    intent = new Intent(MainActivity.this,LocalUpdateActivity.class);
                    intent.putExtra(UPGRADEVERSION,mUpdateVersion);
                    intent.putExtra(DEVCENAME,mDeviceName);
                    intent.putExtra(UPGRADEHARD,mUpdateHard);
                    startActivityForResult(intent,0X01);
                    onResetStatus();
                    break;
            }

        }
    };

    private boolean isCompare(String oldHard,String newHard)
    {
        try{
            String[] oldSpilt = oldHard.split(".");
            String[] newSpilt = newHard.split(".");
            for(int i=0;i<oldSpilt.length;i++)
            {
                Integer oldNumer = Integer.valueOf(oldSpilt[i]);
                Integer newNumber = Integer.valueOf(newSpilt[i]);
                if(newNumber == oldNumer)
                    continue;
                else if(newNumber > oldNumer)
                    return true;
                else
                    return false;
            }
        }catch(Exception e)
        {
            return false;
        }
        return false;
    }


    private void onShowDevice()
    {
        mScanMap = new HashMap<>();
        if(mBlueService== null)
        {
            mCustomProgress.onToast("应用数据丢失，请退出后，重新初始化数据");
            return ;
        }
        mBlueService.onScanAllDevice();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
//                if(mDialog!= null)
//                    mDialog.dismiss();
                  if(mPopDialog != null)
                  {
                      mPopDialog.dismiss();
                  }
                ArrayList<ScanDevice> mDeviceList = new ArrayList<>();
                for(String key: mScanMap.keySet())
                {
                    ScanDevice mScan = mScanMap.get(key);
                    mDeviceList.add(mScan);
                    Log.i("BlueService","near -> name:"+ mScan.getmDevice());
                }
                //检测是否有设备：
                if(mDeviceList.size() == 0 )
                {
                    onPopNoDevice();
                    if(isDebug)Log.i(TAG,"当前的设备数量： "+ mDeviceList.size());
                    return ;
                }
                onPopDevice(mDeviceList);
            }
        },3000);
    }


    private void onPopDevice(ArrayList<ScanDevice> mDeviceList)
    {
        //弹框
        final PopupWindow mPopWindow = new PopupWindow(MainActivity.this);
        Drawable mDrawable = getResources().getDrawable(android.R.color.transparent);
        mPopWindow.setBackgroundDrawable(mDrawable);
        mPopWindow.setFocusable(true);
        mPopWindow.setOutsideTouchable(true);
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int windowWidth = mWindowManager.getDefaultDisplay().getWidth();
        int windowHeight = mWindowManager.getDefaultDisplay().getHeight();
        mPopWindow.setWidth(windowWidth);
        mPopWindow.setHeight(windowHeight);
        View mView = LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_select,null);
        ListView mListView = mView.findViewById(R.id.main_pop_list);
        Button btnCancel = mView.findViewById(R.id.main_pop_cancel);
        TextView tvTitle = mView.findViewById(R.id.main_pop_title);
        tvTitle.setText("选择设备");
        DeviceAdapter mAdapter = new DeviceAdapter();
        mListView.setAdapter(mAdapter);
        mAdapter.setData(mDeviceList);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ScanDevice mDevice = (ScanDevice) parent.getItemAtPosition(position);
                String mAddress =  mDevice.getmDevice();
                tvName.setText(mAddress);
                mPopWindow.dismiss();

            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopWindow.dismiss();
            }
        });
        mPopWindow.setContentView(mView);
        mPopWindow.showAtLocation(btnDevice,Gravity.NO_GRAVITY,0,0);

    }

    private void onPopNoDevice()
    {
        //弹框提示用户重新选择
        final PopupWindow mPopWindow = new PopupWindow(MainActivity.this);
        Drawable mDrawable = getResources().getDrawable(android.R.color.transparent);
        mPopWindow.setBackgroundDrawable(mDrawable);
        mPopWindow.setFocusable(true);
        mPopWindow.setOutsideTouchable(true);
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
        int screenHeight = mWindowManager.getDefaultDisplay().getHeight();
        mPopWindow.setWidth(screenWidth);
        mPopWindow.setHeight(screenHeight);

        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_rescan,null);
        Button btnOk = view.findViewById(R.id.re_scan_ok);
        Button btnCancel = view.findViewById(R.id.re_scan_cancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopWindow.dismiss();
            }
        });
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopWindow.dismiss();
                //重新扫描
                mPopDialog = mCustomProgress.showPopWindow(v);
                mBlueService.onReScan();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(mPopDialog != null)
                        {
                            mPopDialog.dismiss();
                        }
                        ArrayList<ScanDevice> mDeviceList = new ArrayList<>();
                        for(String key: mScanMap.keySet())
                        {
                            ScanDevice mScan = mScanMap.get(key);
                            mDeviceList.add(mScan);
                        }
                        if(mDeviceList.size() == 0  )
                        {
                            mCustomProgress.onToast("本次扫描暂时未找到设备！");
                            return ;
                        }
                        if(isDebug)Log.i(TAG,"二次扫描的设备数量： "+ mDeviceList.size());
                        onPopDevice(mDeviceList);
                    }
                },11000);
            }
        });

        mPopWindow.setContentView(view);
        mPopWindow.showAtLocation(btnDevice,Gravity.NO_GRAVITY,0,0);
    }

    private void onShowVersion()
    {
        mCustomProgress.getMessage(BaseURLOAD, new CustomProgress.onResponse() {

            @Override
            public void onSuccess(String response) {
                if(isDebug)Log.i(TAG,"Response : "+ response);
                if(mPopDialog!= null)
                    mPopDialog.dismiss();
                //绘制弹框
                final PopupWindow mPopWindow  = new PopupWindow(MainActivity.this);
                Drawable mDrawableBack = getResources().getDrawable(android.R.color.transparent);
                mPopWindow.setBackgroundDrawable(mDrawableBack);
                mPopWindow.setOutsideTouchable(true);
                WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                int windowWidth = mWindowManager.getDefaultDisplay().getWidth();
                int windowHeight = mWindowManager.getDefaultDisplay().getHeight();
                mPopWindow.setWidth(windowWidth);
                mPopWindow.setHeight(windowHeight);
                View mView = LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_select,null);
                ListView mListView = mView.findViewById(R.id.main_pop_list);
                Button btnCancel = mView.findViewById(R.id.main_pop_cancel);
                TextView tvTitle = mView.findViewById(R.id.main_pop_title);
                tvTitle.setText("选择文件");
                btnCancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPopWindow.dismiss();
                    }
                });
                String[] mDevices = response.split(",");
                ArrayList<String> mDeviceList = new ArrayList<>();
                for(int i=0;i<mDevices.length;i++)
                {
                    mDeviceList.add(mDevices[i]);
                }
                VersionAdapter mAdapter = new VersionAdapter();
                mListView.setAdapter(mAdapter);
                mAdapter.setData(mDeviceList);
                mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        mPopWindow.dismiss();
                        String index = (String) parent.getItemAtPosition(position);
                        //设置请求
                        mPopDialog = mCustomProgress.showPopWindow(tvVersion);
                        mCustomProgress.getMessage(BaseURLOAD + index,new CustomProgress.onResponse(){
                            @Override
                            public void onSuccess(String response) {
                                if(mPopWindow!= null)
                                    mPopDialog.dismiss();
                                if(isDebug) Log.i(TAG,"" + response);
                                if(response.length() <0)
                                    return ;
                                String[] devices = response.split(",");
                                String firstDevice = devices[0];
                                if(firstDevice == null)
                                    return ;
                                try {
                                    String[] deviceInfo = firstDevice.split("=");
                                    String version = deviceInfo[1];
                                    if(version.length()> 8 ) {
                                        version = version.substring(0, version.length() - 8);
                                    }
                                    tvLocationVersion.setText(version);
                                    tvLocationHard.setText(deviceInfo[0]);
                                    btnLocation.setEnabled(true);
                                }catch(Exception e)
                                {

                                }

                            }

                            @Override
                            public void onFailure(int code) {
                                if(mPopDialog!= null)
                                    mPopDialog.dismiss();
                            }
                        });
                    }
                });
                mPopWindow.setContentView(mView);
                mPopWindow.setFocusable(true);
                mPopWindow.showAtLocation(btnLocationSelect,Gravity.NO_GRAVITY,0,0);

            }

            @Override
            public void onFailure(int code) {
                if(isDebug) Log.i(TAG,"code : " + code);
                if(mPopDialog!= null)
                    mPopDialog.dismiss();
            }

        });
    }

    private void onConnectDevice()
    {
        String mDeviceName = tvName.getText().toString();
        if(mDeviceName == null)
        {
           mCustomProgress.onToast("请选择设备！");
            if(mPopDialog!= null)
                mPopDialog.dismiss();
            return ;
        }
        if(mDeviceName.isEmpty())
        {
            mCustomProgress.onToast("请选择设备！");
            if(mPopDialog!= null)
                mPopDialog.dismiss();
            return ;
        }
        mBlueService.setDeviceName(mDeviceName);
        mBlueService.onScann();
    }


    private void onRefreshVerison()
    {
        mPopDialog = mCustomProgress.showPopWindow(tvOnlineVersion);
        String updateVersion = tvVersion.getText().toString()+ ".txt";
        mCustomProgress.getMessage(BaseURLOAD + updateVersion, new CustomProgress.onResponse() {
            @Override
            public void onSuccess(String response) {
                if(mPopDialog != null)
                    mPopDialog.dismiss();
                if(isDebug) Log.i(TAG,"" + response);
                if(response.length() <0)
                    return ;
                String[] devices = response.split(",");
                String firstDevcie = devices[0];
                if(firstDevcie == null)
                    return ;
                try {
                    String[] deviceInfo = firstDevcie.split("=");
                    String version = deviceInfo[1];
                    if(version.length()> 8 ) {
                        version = version.substring(0, version.length() - 8);
                    }
                    tvOnlineVersion.setText(version);
                    tvOnlineHard.setText(deviceInfo[0]);
                    btnUpdate.setEnabled(true);
                }catch(Exception e)
                {

                }
            }

            @Override
            public void onFailure(int code) {
                if(mPopDialog != null)
                    mPopDialog.dismiss();
                mCustomProgress.onToast("请连接设备后在查看！");
            }
        });
    }

    private void onResetStatus()
    {
        tvName.setText("");
        tvHard.setText("");
        tvVersion.setText("");
        tvLocationVersion.setText("");
        tvLocationHard.setText("");
        tvOnlineVersion.setText("");
        tvOnlineHard.setText("");
        tvStatus.setText("");
        btnUpdate.setEnabled(false);
        btnLocation.setEnabled(false);
        //隐藏升级界面
        mUpdateAction.setVisibility(View.GONE);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

    }

    private void init()
    {
        tvOnlineVersion = findViewById(R.id.main_online_version);
        tvOnlineHard = findViewById(R.id.main_online_hard);
        tvLocationVersion = findViewById(R.id.main_location_version);
        tvLocationHard = findViewById(R.id.main_location_hard);
        tvVersion = findViewById(R.id.main_connect_version);
        tvHard = findViewById(R.id.main_connect_hard);
        tvStatus = findViewById(R.id.main_connect_status);
        tvName = findViewById(R.id.main_connect_device);

        btnDevice = findViewById(R.id.main_connect_select);
        btnConnect = findViewById(R.id.main_connect_online);
        btnDisConnect = findViewById(R.id.main_connect_disconnect);
        btnLocationSelect = findViewById(R.id.main_location_select);
        btnLocation = findViewById(R.id.main_location_update);
        btnUpdateSelect = findViewById(R.id.main_online_refresh);
        btnUpdate = findViewById(R.id.main_online_update);
        mUpdateAction = findViewById(R.id.main_update);

        btnDevice.setOnClickListener(onClickListener);
        btnConnect.setOnClickListener(onClickListener);
        btnDisConnect.setOnClickListener(onClickListener);
        btnLocationSelect.setOnClickListener(onClickListener);
        btnLocation.setOnClickListener(onClickListener);
        btnUpdateSelect.setOnClickListener(onClickListener);
        btnUpdate.setOnClickListener(onClickListener);

        onResetStatus();
        mCustomProgress =  CustomProgress.getInstance(MainActivity.this);
    }

    private ServiceConnection mServiceConnect = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if(service instanceof BleService.BleBinder)
            {
                BleService.BleBinder mBinder = (BleService.BleBinder) service;
                mBlueService = mBinder.getService();
                mBlueService.setCallback(MainActivity.this);
                //显示按钮
                btnDevice.setEnabled(true);
                btnConnect.setEnabled(true);
                btnDisConnect.setEnabled(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        //绑定服务
        Intent intent = new Intent(MainActivity.this, BleService.class);
        bindService(intent,mServiceConnect,Service.BIND_AUTO_CREATE);
        if(isDebug)Log.i(TAG,"调用了Start方法");
    }



    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //处理6.0以上权限获取问题
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // TODO request success

                }
                break;
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConnect);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0X01)
        {
            if(isDebug)Log.i(TAG,"返回了选择界面");
            if(mBlueService== null) //重新绑定
            {
                btnDevice.setEnabled(false);
                btnConnect.setEnabled(false);
                btnDisConnect.setEnabled(false);
                Intent intent = new Intent(MainActivity.this, BleService.class);
                bindService(intent,mServiceConnect,Service.BIND_AUTO_CREATE);
                if(isDebug)Log.i(TAG,"进行了重新绑定");
            }
            if(mPopDialog != null)
            {
                if(isDebug)Log.i(TAG,"关闭了弹框");
                mPopDialog.dismiss();
                mPopDialog = null;
            }
        }
    }

    @Override
    public void onConnect(int statue) {
        if(statue == 0 )
        {
            tvStatus.setText("断开连接");
            tvStatus.setTextColor(Color.RED);

            if(mPopDialog!= null)
            {
                mPopDialog.dismiss();
                mPopDialog = null;
            }
            //隐藏升级界面
            mUpdateAction.setVisibility(View.GONE);
        }
        else if(statue == 2)
        {
            tvStatus.setText("连接成功");
            tvStatus.setTextColor(Color.GREEN);
            mUpdateAction.setVisibility(View.VISIBLE);
        }
        else if(statue == 3 )
        {
            tvStatus.setText("正在连接");
            tvStatus.setTextColor(Color.YELLOW);
        }
    }

    @Override
    public void onReceive(byte[] message) {
        int[] bleCode = BleSubService.arrayByteToInt(message);
        String code  = BleSubService.intArrToString(bleCode);
        Log.i("BlueService","" + code);
    }

    @Override
    public void onRead(String message, int state) {
        if(mPopDialog!= null)
        {
            mPopDialog.dismiss();
        }
        if(state == 1)
        {
            tvVersion.setText(message);
        }
        else if(state == 3)
        {
            tvHard.setText(message);
        }
    }

    @Override
    public void onDevice(BluetoothDevice mDevice, int rss) {
        String mAddress = mDevice.getName();
        if(mAddress == null)
            return ;
        if(!mScanMap.containsKey(mAddress))
        {
            ScanDevice mDeviceObject = new ScanDevice();
            mDeviceObject.setSign(rss);
            mDeviceObject.setmDevice(mAddress);
            mScanMap.put(mAddress,mDeviceObject);
            if(isDebug) Log.i("BlueService","connect -> name : "+ mAddress + "\t address : "+ mDevice.getAddress());
        }
    }

    @Override
    public void onProgress(String percent) {

    }


    private class DeviceAdapter extends BaseAdapter{

        private ArrayList<ScanDevice> mScanList = new ArrayList<>();

        public DeviceAdapter() {}

        public void setData(ArrayList<ScanDevice> mScanList)
        {
            if(mScanList != null)
            {
                this.mScanList = mScanList;
            }
            this.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return this.mScanList.size();
        }

        @Override
        public Object getItem(int position) {
            return this.mScanList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder mHolder ;
            if(convertView == null)
            {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_devcie,null);
                int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,40F,MainActivity.this.getResources().getDisplayMetrics());
                LinearLayout.LayoutParams mParams =  new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,height);
                convertView.setLayoutParams(mParams);
                mHolder = new ViewHolder(convertView);
                convertView.setTag(mHolder);
            }
            else
            {
                mHolder = (ViewHolder) convertView.getTag();
            }
            ScanDevice mScanObject =  this.mScanList.get(position);
            String mAddress = mScanObject.getmDevice();
            int sign =  mScanObject.getSign();
            mHolder.tvDevice.setText(mAddress);
            mHolder.tvSign.setText(sign + "dp");
            return convertView;

        }

        class ViewHolder{

            public TextView tvDevice;
            public TextView tvSign;
            public ViewHolder(View view)
            {
                tvDevice = view.findViewById(R.id.item_device_address);
                tvSign = view.findViewById(R.id.item_device_sign);
            }


        }

    }


    private class VersionAdapter extends BaseAdapter{

        private ArrayList<String> mDeviceList = new ArrayList<>();

        public VersionAdapter()
        {

        }

        public void setData(ArrayList<String> mDeviceList)
        {
            if(mDeviceList != null)
            {
                this.mDeviceList = mDeviceList;
            }
            this.notifyDataSetChanged();
        }



        @Override
        public int getCount() {
            return this.mDeviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return this.mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {


            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_version,null);
                int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,40F,MainActivity.this.getResources().getDisplayMetrics());
                ViewGroup.LayoutParams mParams =  new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height);
                convertView.setLayoutParams(mParams);
            }
            TextView mDeviceView = (TextView) convertView;
            mDeviceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14F);
            mDeviceView.setTextColor(Color.BLACK);
            String mDevice = this.mDeviceList.get(position);
            if (mDevice.length() > 4)
            {
                mDevice = mDevice.substring(0,mDevice.length()-4);
            }
            mDeviceView.setText(mDevice);
            return mDeviceView;
        }



    }

}
