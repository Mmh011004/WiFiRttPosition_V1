package com.example.wifirttposition.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.example.wifirttposition.entity.ApInfo;
import com.example.wifirttposition.entity.LocationInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 作者　: mmh
 * 时间　: 2024/1/28
 * 描述　: 用于绘制图片的view，可以绘制定位点，wifi点，参考点
 */
public class PrintImageView extends AppCompatImageView {
    private static final String TAG = "PrintImageView";

    private static final float AXIS_TEXT_SIZE = 15f;
    private static final float LOCATION_RADIUS = 15f;
      private static final float AP_RADIUS = 15f;

    // 点集合
    private List<LocationInfo> location_points = new CopyOnWriteArrayList<>();
    private List<ApInfo> wifi_points = new CopyOnWriteArrayList<>();

    // 参考点
    private List<LocationInfo> reference_point = new CopyOnWriteArrayList<>();

    // 油漆
    private Paint myPaint;
    private Paint apPaint;
    private Paint locationPaint;
    private Paint axisPaint;
    private Paint textPaint;


    /*
    * 设置真实的X轴长度 单位m
    * 设置图片的横轴有多少米
    * */
    public static float trueLengthX = 22.85f;

    /*
    * 宽度划分
    * 设置X轴的单位长度，坐标轴与真实距离相对应，单位 m*/
    public static float dividedX = 22.85f;

    /*
    * y轴长度
    * */
    public static float dividedY;

    /*
    * 每一个单位换算到像素点的长度*/
    public static float pixelDivided; // 也就是说1m对应多少个像素点

    public PrintImageView(@NonNull Context context) {
        super(context);
    }

    public PrintImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PrintImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();

    }

    private void init() {
        Log.i(TAG, "init: axisPaint");
        myPaint.setColor(Color.RED);
        myPaint.setAntiAlias(true);
        myPaint.setStyle(Paint.Style.FILL);

        apPaint = new Paint();
        apPaint.setColor(Color.BLUE);
        apPaint.setStyle(Paint.Style.FILL);

        locationPaint = new Paint();
        locationPaint.setColor(Color.RED);
        locationPaint.setStyle(Paint.Style.FILL);

        axisPaint = new Paint();
        axisPaint.setColor(Color.BLACK);
        axisPaint.setStrokeWidth(2f);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(AXIS_TEXT_SIZE);

    }

    public void setApPositions(List<ApInfo> apPositions) {
        this.wifi_points = apPositions;
        // 刷新图像
        invalidate();
    }

    public void setLocationPositions(LocationInfo locationInfo) {
        if (locationInfo != null) {
            this.location_points.add(locationInfo);
        }
        // 刷新图像
        invalidate();
    }

    /*
    * 获得轨迹的最后一个点
    * */
    public LocationInfo getLastLocationPoint() {
        if (location_points.size() > 0) {
            return location_points.get(location_points.size() - 1);
        }
        return null;
    }

    /*
    * 清除地图上的点
    * */
    public void clearPoints() {
        location_points.clear();
        wifi_points.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 绘制坐标轴
//        drawAxis(canvas);
        // 绘制wifi
        drawWifiPoints(canvas);
        // 绘制定位点
        drawLocation(canvas);
    }

    private void drawAxis(Canvas canvas) {
        // 绘制X轴
        canvas.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2, axisPaint);
        // 绘制Y轴
        canvas.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight(), axisPaint);
        // 绘制X轴箭头
        canvas.drawLine(getWidth(), getHeight() / 2, getWidth() - 20, getHeight() / 2 - 10, axisPaint);
        canvas.drawLine(getWidth(), getHeight() / 2, getWidth() - 20, getHeight() / 2 + 10, axisPaint);
        // 绘制Y轴箭头

        // 绘制x轴文字
        canvas.drawText("X", getWidth() - 20, getHeight() / 2 - 20, textPaint);
        // 绘制y轴文字
        canvas.drawText("Y", getWidth() / 2 + 20, 20, textPaint);
    }

    private void drawWifiPoints(Canvas canvas) {
        for (ApInfo apInfo : wifi_points) {
            // 取出ap坐标
            PointF apPoint = apInfo.getPointF();
            // 绘制ap
            canvas.drawCircle(apPoint.x, apPoint.y, AP_RADIUS, apPaint);
        }
    }

    private void drawLocation(Canvas canvas) {
        // 绘制定位点 取出最后一个点
        if (location_points.size() == 0) {
            return;
        }
        PointF location_Point =  getLastLocationPoint().getPointF();
        canvas.drawCircle(location_Point.x, location_Point.y, LOCATION_RADIUS, locationPaint);
    }
}
