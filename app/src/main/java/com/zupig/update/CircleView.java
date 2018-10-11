package com.zupig.update;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.zupig.oad.R;


/**
 * Created by song on 2018/5/11.
 */

public class CircleView extends View {

    private final String TAG = "CircleView";
    private final boolean isDebug  = false;
    private int color ;
    private int unenableColor;
    private float linewidth;
    private float radius;
    private int width;
    private int height;
    private double currentAngle= -1;

    private Paint mCirclePaint;
    private Paint mLinePaint;
    private float offset;

    private ValueAnimator animator;

    private boolean isStart = false;


    public CircleView(Context context) {
        super(context);
        init(context,null);
    }

    public CircleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs);
    }

    public CircleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context,attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CircleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context,attrs);
    }

    /****
     * 初始化当前的变量
     * @param mContext
     * @param attrs
     */
    private void init(Context mContext, AttributeSet attrs)
    {
        if(attrs == null)
        {
            color = mContext.getResources().getColor(R.color.yellow);
            linewidth = 2;
            radius = 5;
        }
        else
        {
            TypedArray mArray = mContext.obtainStyledAttributes(attrs, R.styleable.circle_view);
            color = mArray.getColor(R.styleable.circle_view_linecolor,0xFFFF00);
            linewidth = mArray.getDimension(R.styleable.circle_view_linewidth,2);
            radius = mArray.getDimension(R.styleable.circle_view_circleRadius,2);
            offset = mArray.getDimension(R.styleable.circle_view_offset,5);
            mArray.recycle();
        }


        //初始化画笔
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setAntiAlias(true);
        mLinePaint.setColor(color);
        mLinePaint.setStrokeWidth(linewidth);
        mLinePaint.setStyle(Paint.Style.STROKE);


        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setColor(color);
        mCirclePaint.setStyle(Paint.Style.FILL);

        //unenableColor
        unenableColor = getResources().getColor(R.color.gray);

    }

    /***
     * 启动动画开始循环
     */
    public void startCircle()
    {
//        Log.i(TAG,"");
        animator = ValueAnimator.ofFloat(90f, 450f);
        animator.setDuration((long) 2000).setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float angle = (Float) animation.getAnimatedValue();
                currentAngle = angle * Math.PI /180;
                invalidate();
            }
        });
        animator.setInterpolator(new TimeInterpolator() {

            public float getInterpolation(float input) {
                float output;
                if (input < 0.5) {
                    output = (float) Math.sin(input * Math.PI) / 2;
                } else {
                    output = 1 - (float) Math.sin(input * Math.PI) / 2;
                }
                return output;
            }
        });

        animator.start();
        isStart = true;
    }

    /***
     * 关闭动画
     */
    public void stopCircle()
    {
        if(animator != null)
        {
            animator.cancel();
        }
        animator = null;
        invalidate();
        isStart = false;
    }






    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);
        if(isDebug) Log.i(TAG,"onMeasure width :"+ width + "\t height:" + height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //绘制背景的圆形轨迹
        float currentX = width/2  - offset;
        if(isStart)
        {
            mLinePaint.setColor(color);
        }
        else
        {
            mLinePaint.setColor(unenableColor);
        }
        canvas.drawCircle(width/2,height/2,currentX,mLinePaint);

        //绘制原点
//        if(currentAngle == -1)
//        {
//            startCircle();
//        }
        if(isStart)
        {
            float cx = (float) (currentX + currentX* Math.cos(currentAngle)) + offset;
            float cy = (float) (currentX +currentX* Math.sin(currentAngle))+ offset;
            canvas.drawCircle(cx,cy,radius,mCirclePaint);
        }

    }
}
