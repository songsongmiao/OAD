package com.zupig.oad;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.zupig.update.BleSubService;
import com.zupig.update.CallBack;
import com.zupig.update.CircleView;
import com.zupig.update.CustomProgress;
import com.zupig.update.ScanDevice;

import java.util.ArrayList;

public class LocalUpdateActivity extends AppCompatActivity implements CallBack{

    private final String TAG = "MainActivity";
    private final boolean isDebug = false;

     private BleSubService mBlueService;
     private TextView tvVersion,tvHard,tvProgress;
     private Button btnSumbit;
     private CircleView mCircleView;

//     private Dialog mDialog;
    private PopupWindow mLocalPopDialog;
     private CustomProgress mCustomProgram;
     private String mVersion,mBluetooth,mHard;

     private Handler mHandler = new Handler(){};


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        mCustomProgram = CustomProgress.getInstance(LocalUpdateActivity.this);

        init();
    }

    private void init()
    {
        tvVersion = findViewById(R.id.update_version);
        tvHard = findViewById(R.id.update_hard);
        tvProgress = findViewById(R.id.update_process_text);
        btnSumbit = findViewById(R.id.update_update);
        mCircleView = findViewById(R.id.update_process);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                onShow();
            }
        },1000);
        Intent intent = getIntent();
        mVersion = intent.getStringExtra(MainActivity.UPGRADEVERSION);
        mBluetooth = intent.getStringExtra(MainActivity.DEVCENAME);
        mHard = intent.getStringExtra(MainActivity.UPGRADEHARD);

        btnSumbit.setEnabled(false);

        btnSumbit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mCircleView.startCircle();
                btnSumbit.setEnabled(false);
                btnSumbit.setText("蓝牙升级中");
                if(mBlueService== null)
                {
                    mCustomProgram.onToast("蓝牙连接初始化失败，请退出重试！");
                    return ;
                }
                String mUpdateVersion = mVersion+ ".txt";
                mBlueService.onUpgradeFile(mUpdateVersion);
            }
        });

        if(isDebug) Log.i(TAG,"receiver ->  Version:"+ mVersion + "\tHard:"+ mHard + "\tBluetooth:"+ mBluetooth);

        //设置返回键
        ActionBar mActionBar = getSupportActionBar();
        mActionBar.setDisplayHomeAsUpEnabled(true);//添加一个返回菜单

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                String mVersion = tvVersion.getText().toString();
                if(mVersion.equals("-- --"))
                {
                    //弹框提示用户重新选择
                    final PopupWindow mPopWindow = new PopupWindow(LocalUpdateActivity.this);
                    Drawable mDrawable = getResources().getDrawable(android.R.color.transparent);
                    mPopWindow.setBackgroundDrawable(mDrawable);
                    mPopWindow.setFocusable(true);
                    mPopWindow.setOutsideTouchable(true);
                    WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                    int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
                    int screenHeight = mWindowManager.getDefaultDisplay().getHeight();
                    mPopWindow.setWidth(screenWidth);
                    mPopWindow.setHeight(screenHeight);

                    View view = LayoutInflater.from(LocalUpdateActivity.this).inflate(R.layout.layout_rescan,null);
                    TextView tvMessage = view.findViewById(R.id.re_scan_content);
                    tvMessage.setText("连接失败，需要重新连接吗?");
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
                            mBlueService.onRestartConnect();
                        }
                    });

                    mPopWindow.setContentView(view);
                    mPopWindow.showAtLocation(tvVersion,Gravity.NO_GRAVITY,0,0);
//
                }
            }
        },8000);

    }

    private void onShow()
    {
        Context mContext = LocalUpdateActivity.this;
        mLocalPopDialog  = new PopupWindow(mContext);
        mLocalPopDialog.setOutsideTouchable(true);
        mLocalPopDialog.setFocusable(true);
        View mRootView = LayoutInflater.from(mContext).inflate(R.layout.layout_progressbar,null);
        mLocalPopDialog.setContentView(mRootView);
        Drawable mDrawable = mContext.getResources().getDrawable(android.R.color.transparent);
        mLocalPopDialog.setBackgroundDrawable(mDrawable);
        WindowManager mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
        int screenHeight = mWindowManager.getDefaultDisplay().getHeight();
        mLocalPopDialog.setWidth(screenWidth);
        mLocalPopDialog.setHeight(screenHeight);
        try {

            mLocalPopDialog.showAtLocation(tvVersion,Gravity.NO_GRAVITY,0,0);
        }catch(Exception e)
        {
            if(isDebug)Log.i(TAG,"创建弹框失败!");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if(mBlueService!= null)
            {
                mBlueService.onDisConnected();
            }
//            Intent intent = new Intent(LocalUpdateActivity.this,MainActivity.class);
//            startActivity(intent);
            this.finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home)
        {
            if(mBlueService!= null)
            {
                mBlueService.onDisConnected();
            }
//            Intent intent = new Intent(LocalUpdateActivity.this,MainActivity.class);
//            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if(service instanceof BleSubService.BleBinder)
            {
                BleSubService.BleBinder mBinder = (BleSubService.BleBinder) service;
                mBlueService = mBinder.getService();
                mBlueService.setCallback(LocalUpdateActivity.this);
                //检查当前的权限
                if(mVersion == null || mBluetooth == null)
                {
//                    if(mLocalPopDialog != null)
//                        mLocalPopDialog.dismiss();
                    mCustomProgram.onToast("未获配置信息，请退出重试!");
                    return ;
                }
                boolean isAccess = mBlueService.isMatch();
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

            }
            else
            {
                if(mLocalPopDialog != null)
                    mLocalPopDialog.dismiss();
                mCustomProgram.onToast("蓝牙连接初始化失败，请退出重试!");
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    private void onConnectDevice()
    {
        //开始连接
        mBlueService.setDeviceName(mBluetooth);
        mBlueService.onScann();
    }


    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(LocalUpdateActivity.this, BleSubService.class);
        bindService(intent,mServiceConnection, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        unbindService(mServiceConnection);
    }



    @Override
    public void onConnect(int statue) {

    }

    @Override
    public void onReceive(byte[] message) {

    }

    @Override
    public void onRead(String message, int state) {
        if(mLocalPopDialog!= null)
            mLocalPopDialog.dismiss();
        if(state == 1)
        {
            tvVersion.setText(message);
        }
        else if(state == 2 )
        {
            btnSumbit.setEnabled(false);
            tvProgress.setText("写入失败，请重启蓝牙后再次尝试!");
            btnSumbit.setText("升级失败");
            mCircleView.stopCircle();
            mBlueService.onDisConnected();
        }
        else if(state == 3)
        {
            tvHard.setText(message);
            //检查当前的版本信息
            String mCurrentVersion = tvVersion.getText().toString();
            String mCurrentHard = message;
            if(mCurrentVersion.equals(mVersion))
            {
                boolean isUpdate = isCompare(mCurrentHard,mHard);
                if(!isUpdate)
                {
                    btnSumbit.setEnabled(false);
                    tvProgress.setText("已经升级到最新版");
                    btnSumbit.setText("升级完成");
                    return ;
                }

            }
            btnSumbit.setEnabled(true);
            btnSumbit.setText("升级蓝牙");

        }
    }


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


    @Override
    public void onDevice(BluetoothDevice mDevice, int rss) {

    }

    @Override
    public void onProgress(String percent) {
        tvProgress.setText(percent);
        if(percent.contains("Bluetooth"))
        {
            mCircleView.stopCircle();
            tvProgress.setText("蓝牙文件上传成功，一分钟后\n将连接设备获取最新的版本号");
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                     if(!mBlueService.isConnect())
                     {
                         mBlueService.onRestartConnect();// 重新连接
//                         mCustomProgram.onToast("重新连接！");
                         Log.i(TAG,"重新连接！");
                     }
                     else
                     {
                         Log.i(TAG,"连接OK！");
                     }
                }
            },10000);
        }
    }
}
