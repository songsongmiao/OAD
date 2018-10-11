package com.zupig.update;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.zupig.oad.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class CustomProgress {

    private Context mContext;
    private static CustomProgress mProgress;
    private final String TAG = "CustomProgress";
    private Dialog mDialog;
    private Handler mHandler = new Handler();
    private StringBuilder mBuilder = new StringBuilder();

    private CustomProgress(Context mContext)
    {
        this.mContext = mContext;
    }

    public static CustomProgress getInstance(Context mContext)
    {
        if(mProgress == null)
        {
            mProgress = new CustomProgress(mContext);
        }
        return mProgress;
    }

    public void getMessage(final String path, final onResponse backcall)
    {
//        mDialog = new Dialog(mContext);
//        mDialog.setCanceledOnTouchOutside(true);
//        final Window mDialogWindow = mDialog.getWindow();
//        WindowManager.LayoutParams mParams = mDialogWindow.getAttributes();
//        int width = 350;
//        int height = 350;
//        mParams.width  = width;
//        mParams.height = height;
//        mDialogWindow.setAttributes(mParams);
//        mDialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
//        if(mDialogWindow != null)
//        {
//            mDialogWindow.setGravity(Gravity.CENTER);
//        }
//        mDialog.show();
//        View mRootView = LayoutInflater.from(mContext).inflate(R.layout.layout_progressbar,null);
//        mDialog.setContentView(mRootView);

        Runnable mRequstRun = new Runnable() {
            @Override
            public void run() {
                if(path== null)
                {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            backcall.onFailure(-1);
                            if(mDialog != null)
                            {
                                mDialog.dismiss();
                            }
                        }
                    });
                    return ;
                }
                HttpURLConnection conn = null;
                try{
                    conn = getConnection(path);
                    conn.setConnectTimeout(6 * 1000);
                    conn.setDoInput(true);
                    conn.setRequestMethod("GET");
                    final int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        String response;
                        mBuilder = new StringBuilder();
                        InputStream is = conn.getInputStream();
                        BufferedReader buffer = new BufferedReader(new InputStreamReader(is));

                        while ((response = buffer.readLine())!= null)
                        {
                            mBuilder.append(response + ",");
                            Log.i(TAG,response);
                        }
                        if(mBuilder.length() > 0 )
                        {
                            mBuilder.deleteCharAt(mBuilder.length() -1);
                        }
                        buffer.close();
                        is.close();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                backcall.onSuccess(mBuilder.toString());
                                if(mDialog != null)
                                {
                                    mDialog.dismiss();
                                }
                            }
                        });

                    }
                    else
                    {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                backcall.onFailure(code);
                                if(mDialog != null)
                                {
                                    mDialog.dismiss();
                                }
                            }
                        });
                    }
                }
                catch(Exception e)
                {
                    Log.e(TAG,"加载文件失败： "+ e.getMessage());
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            backcall.onFailure(0);
                            if(mDialog != null)
                            {
                                mDialog.dismiss();
                            }
                        }
                    });

                }
            }
        };

        new Thread(mRequstRun).start();
    }


    public void onToast(String text)
    {
        Toast mToast = new Toast(mContext);
        TextView tvMessage = new TextView(mContext);
        ViewGroup.LayoutParams mParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tvMessage.setLayoutParams(mParams);
        tvMessage.setGravity(Gravity.CENTER);
        tvMessage.setText(text);
//        tvMessage.setText(id);
        tvMessage.setPadding(15,15,15,15);
        int mMessageColor = mContext.getResources().getColor(android.R.color.black);
        tvMessage.setTextColor(mMessageColor);
        mToast.setView(tvMessage);
        tvMessage.setBackgroundResource(R.drawable.shape_toast);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.show();

    }


    public Dialog showDialog()
    {
        Dialog  mDialog = new Dialog(mContext);
        mDialog.setCanceledOnTouchOutside(true);
        final Window mDialogWindow = mDialog.getWindow();
        WindowManager.LayoutParams mParams = mDialogWindow.getAttributes();
        int width = 350;
        int height = 350;
        mParams.width  = width;
        mParams.height = height;
        mDialogWindow.setAttributes(mParams);
        mDialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
        if(mDialogWindow != null)
        {
            mDialogWindow.setGravity(Gravity.CENTER);
        }
        mDialog.show();
        View mRootView = LayoutInflater.from(mContext).inflate(R.layout.layout_progressbar,null);
        mDialog.setContentView(mRootView);
        return mDialog;
    }

    public PopupWindow showPopWindow(View parent)
    {
        PopupWindow mPopWindow = new PopupWindow(mContext);
        mPopWindow.setOutsideTouchable(true);
        mPopWindow.setFocusable(true);
        View mRootView = LayoutInflater.from(mContext).inflate(R.layout.layout_progressbar,null);
        mPopWindow.setContentView(mRootView);
        Drawable mDrawable = mContext.getResources().getDrawable(android.R.color.transparent);
        mPopWindow.setBackgroundDrawable(mDrawable);
        WindowManager mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int screenWidth = mWindowManager.getDefaultDisplay().getWidth();
        int screenHeight = mWindowManager.getDefaultDisplay().getHeight();
        mPopWindow.setWidth(screenWidth);
        mPopWindow.setHeight(screenHeight);
        mPopWindow.showAtLocation(parent, Gravity.CENTER,0,0);
        mPopWindow.showAsDropDown(parent,100,100);
        return mPopWindow;
    }



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


    public interface onResponse{

        public void onSuccess(String response);

        public void onFailure(int code);
    }




}
