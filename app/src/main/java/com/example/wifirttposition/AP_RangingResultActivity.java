package com.example.wifirttposition;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wifirttposition.entity.ApInfo;
import com.example.wifirttposition.utils.LogToFile;
import com.example.wifirttposition.widgets.DialogCollectData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AP_RangingResultActivity extends AppCompatActivity {

    private static final String TAG = "APRRActivity";

    public static final String SCAN_RESULT_EXTRA =
            "com.example.android.wifirttscan.extra.SCAN_RESULT";

    //样本数量默认值
    private static final int SAMPLE_SIZE_DEFAULT = 50;
    //新测距请求默认值之前的毫秒延迟
    private static final int MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT = 1000;

    // UI Elements.
    private TextView mSsidTextView;
    private TextView mBssidTextView;

    private TextView mRangeTextView;
    private TextView mRangeMeanTextView;
    private TextView mRangeSDTextView;
    private TextView mRangeSDMeanTextView;
    private TextView mRssiTextView;
    private TextView mSuccessesInBurstTextView;
    private TextView mSuccessRatioTextView;
    private TextView mNumberOfRequestsTextView;

    private EditText mSampleSizeEditText;
    private EditText mMillisecondsDelayBeforeNewRangingRequestEditText;
    private EditText mApLongitudeEditText;
    private EditText mApLatitudeEditText;
    private EditText mApAltitudeEditText;

    // Non UI variables.
    private ScanResult mScanResult;
    private String mMAC;

    private int mNumberOfRangeRequests;
    private int mNumberOfSuccessfulRangeRequests;


    private int mMillisecondsDelayBeforeNewRangingRequest;

    // Max sample size to calculate average for 计算平均值的最大样本数量用于
    // 1. Distance to device (getDistanceMm) over time  随时间变化的设备距离
    // 2. Standard deviation of the measured distance to the device (getDistanceStdDevMm) over time 一段时间内到设备的测量距离（getDistanceStdDevMm）的标准偏差
    // Note: A RangeRequest result already consists of the average of 7 readings from a burst,
    // so the average in (1) is the average of these averages.
    private int mSampleSize;

    //用户设置的AP的经纬度高度信息
    private double mApLongtitude;
    private double mApLatitude;
    private double mApAltitude;
    // Used to loop over a list of distances to calculate averages (ensures data structure never
    // get larger than sample size).
    //用于遍历距离列表以计算平均值
    private int mStatisticRangeHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeHistory;

    // Used to loop over a list of the standard deviation of the measured distance to calculate
    // averages  (ensures data structure never get larger than sample size).
    //用于循环测量距离的标准偏差列表以计算平均值（确保数据结构永远不会大于样本大小）。
    private int mStatisticRangeSDHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeSDHistory;

    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;

    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    //触发器附带的测距请求有延迟
    final Handler mRangeRequestDelayHandler = new Handler();
    private int logForStartOrEnd; // 为了在踩数据的过程中，log日志中出现分隔符以表示所需要的数据在什么区间

    // Dialog
    private DialogCollectData dialogCollectData;

    private boolean isCollecting ;
    private String LogFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ap_ranging_result);
        //初始化UI控件
        mSsidTextView = findViewById(R.id.ssid);
        mBssidTextView = findViewById(R.id.bssid);

        mRangeTextView = findViewById(R.id.range_value);
        mRangeMeanTextView = findViewById(R.id.range_mean_value);
        mRangeSDTextView = findViewById(R.id.range_sd_value);
        mRangeSDMeanTextView = findViewById(R.id.range_sd_mean_value);
        mRssiTextView = findViewById(R.id.rssi_value);
        mSuccessesInBurstTextView = findViewById(R.id.successes_in_burst_value);
        mSuccessRatioTextView = findViewById(R.id.success_ratio_value);
        mNumberOfRequestsTextView = findViewById(R.id.number_of_requests_value);

        mSampleSizeEditText = findViewById(R.id.stats_window_size_edit_value);
        mSampleSizeEditText.setText(SAMPLE_SIZE_DEFAULT+"");

        //测距间隔时间
        mMillisecondsDelayBeforeNewRangingRequestEditText = findViewById(R.id.ranging_period_edit_value);
        mMillisecondsDelayBeforeNewRangingRequestEditText.setText(
                MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT + "");

        mApLongitudeEditText = findViewById(R.id.ap_longitude_label_edit_value);
        mApLatitudeEditText = findViewById(R.id.ap_latitude_label_edit_value);
        mApAltitudeEditText = findViewById(R.id.ap_altitude_label_edit_value);
        // 初始化自定义dialog
        dialogCollectData = new DialogCollectData(this);


        //从Intent中取得扫描结果 ScanResult
        Intent intent = getIntent();
        mScanResult = intent.getParcelableExtra(SCAN_RESULT_EXTRA);

        if (mScanResult == null){
            finish();
        }

        mMAC = mScanResult.BSSID;

        mSsidTextView.setText(mScanResult.SSID);
        mBssidTextView.setText(mScanResult.BSSID);

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE );
        mRttRangingResultCallback = new RttRangingResultCallback();

        // Used to store range (distance) and rangeSd (standard deviation of the measured distance)
        // history to calculate averages.
        mStatisticRangeHistory = new ArrayList<>();
        mStatisticRangeSDHistory = new ArrayList<>();

        LogToFile.init(this);
        logForStartOrEnd = 0;
        resetData();

        startRangingRequest();
    }

    private void startRangingRequest(){
        //ACCESS_FINE_LOCATION权限在MainActivity应该已经被授权，不然踢出
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED){
            finish();
        }
        mNumberOfRangeRequests++;

        //将扫ScanResult的设备添加到要测量范围的设备列表中
        RangingRequest rangingRequest = new RangingRequest.Builder().addAccessPoint(mScanResult).build();

        mWifiRttManager.startRanging(rangingRequest,getApplication().getMainExecutor(), mRttRangingResultCallback);

    }

    private void resetData(){
        mSampleSize = Integer.parseInt(mSampleSizeEditText.getText().toString());

        mMillisecondsDelayBeforeNewRangingRequest =
                Integer.parseInt(
                        mMillisecondsDelayBeforeNewRangingRequestEditText.getText().toString());

        mNumberOfSuccessfulRangeRequests = 0;
        mNumberOfRangeRequests = 0;

        mStatisticRangeHistoryEndIndex = 0;
        mStatisticRangeHistory.clear();

        mStatisticRangeSDHistoryEndIndex = 0;
        mStatisticRangeSDHistory.clear();
    }

    // Calculates average distance based on stored history.
    private float getDistanceMean() {
        float distanceSum = 0;

        for (int distance : mStatisticRangeHistory) {
            distanceSum += distance;
        }

        return distanceSum / mStatisticRangeHistory.size();
    }

    //增加mStatisticRangeHistory。如果大于样本数量值，则循环回并替换列表中最早的距离记录。
    private void addDistanceToHistory(int distance){

        if (mStatisticRangeHistory.size() >= mSampleSize){
            //或者取余操作
            if (mStatisticRangeHistoryEndIndex >= mSampleSize){
                mStatisticRangeHistoryEndIndex = 0;
            }

            mStatisticRangeHistory.set(mStatisticRangeHistoryEndIndex, distance);
            mStatisticRangeHistoryEndIndex++;

        }else {
            mStatisticRangeHistory.add(distance);
        }
    }

    // Calculates standard deviation of the measured distance based on stored history.标准差
    private float getStandardDeviationOfDistanceMean() {
        float distanceSdSum = 0;

        for (int distanceSd : mStatisticRangeSDHistory) {
            distanceSdSum += distanceSd;
        }

        return distanceSdSum / mStatisticRangeHistory.size();
    }

    // 将测量距离的标准差添加到历史记录中. If larger than sample size
    // value, loops back over and replaces the oldest distance record in the list.
    private void addStandardDeviationOfDistanceToHistory(int distanceSd) {

        if (mStatisticRangeSDHistory.size() >= mSampleSize) {

            if (mStatisticRangeSDHistoryEndIndex >= mSampleSize) {
                mStatisticRangeSDHistoryEndIndex = 0;
            }

            mStatisticRangeSDHistory.set(mStatisticRangeSDHistoryEndIndex, distanceSd);
            mStatisticRangeSDHistoryEndIndex++;

        } else {
            mStatisticRangeSDHistory.add(distanceSd);
        }
    }


    //按钮
    public void onResetButtonClick(View view){
        resetData();
//        if (logForStartOrEnd % 2 == 0) {
//            Log.i(TAG, "onRangingResults(): --------------Start Log--------------");
//            LogToFile.i(TAG, "--------------Start Log--------------");
//        }
//        else {
//            Log.i(TAG, "onRangingResults(): --------------End Log--------------");
//            LogToFile.i(TAG, "---------------End Log---------------");
//        }
//        logForStartOrEnd ++;
    }

    public void onLogToFileButtonClick(View view){
        // 弹出
        dialogCollectData.show();
        isCollecting = dialogCollectData.collectingData;

    }

    //按钮
    public void onSaveButtonClick(View view) {
        saveSettings();
    }


    private void saveSettings(){
        mApLongtitude = Double.parseDouble(mApLongitudeEditText.getText().toString());
        mApLatitude = Double.parseDouble(mApLatitudeEditText.getText().toString());
        mApAltitude = Double.parseDouble(mApAltitudeEditText.getText().toString());
        String ssid = mScanResult.SSID;
        String bssid = mScanResult.BSSID;
        ApInfo ap_info = new ApInfo(ssid, bssid, mApLongtitude, mApLatitude, mApAltitude);
        Bundle ap_info_bundle = new Bundle();
        ap_info_bundle.putSerializable("ap_info", (Serializable) ap_info); //这里的字符串由receiver中的onReceive方法取用

        //往下继续
        Intent intent = new Intent();
        // 设置Action 到时候接收器可以接收到这个广播利用过滤器IntentFilter
        intent.setAction("broadcast_ap_info");//这里的字符串由注册接收器的时候取用，表明指定哪一个接收器接收
        intent.putExtras(ap_info_bundle);

        sendBroadcast(intent);

        Toast.makeText(getApplicationContext(),"保存设置成功",Toast.LENGTH_SHORT).show();
    }




    // 处理所有范围请求的回调并发出新的范围请求的类。
    public class RttRangingResultCallback extends RangingResultCallback{

        //连续请求
        private void queueNextRangingRequest() {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRangingRequest();
                        }
                    }, mMillisecondsDelayBeforeNewRangingRequest
            );
        }

        //请求失败
        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();

        }

        //请求成功
        //仅一个ap
        @SuppressLint("SetTextI18n")
        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            Log.i(TAG, "onRangingResults(): " + list);
//            LogToFile.i(TAG, "onRangingResults(): " + list.get(0).getMacAddress() + "," + list.get(0).getDistanceMm()/1000f + "," + list.get(0).getDistanceStdDevMm()/1000f+ ","+ list.get(0).getRssi());
//            LogToFile.i(TAG, "onRangingResults(): " + list);
            if (dialogCollectData.collectingData){
                LogToFile.i(TAG,"onRangingResults(): " + list, dialogCollectData.logName);
                Log.i(TAG, "onRangingResults(): " + list);
            }
            if (list.size() == 1){
                RangingResult rangingResult = list.get(0);

                if (mMAC.equals(rangingResult.getMacAddress().toString())){


                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS){

                        //成功请求次数
                        mNumberOfSuccessfulRangeRequests++;
                        //测距文本
                        mRangeTextView.setText((rangingResult.getDistanceMm()/1000f)+"");
                        addDistanceToHistory(rangingResult.getDistanceMm());

                        //测距平均值
                        mRangeMeanTextView.setText((getDistanceMean() / 1000f) + "");
                        //测距标准差
                        mRangeSDTextView.setText(
                                (rangingResult.getDistanceStdDevMm() / 1000f) + "");

                        addStandardDeviationOfDistanceToHistory(
                                rangingResult.getDistanceStdDevMm());
                        //测距标准差的平均值
                        mRangeSDMeanTextView.setText(
                                (getStandardDeviationOfDistanceMean() / 1000f) + "");

                        //RSSI
                        mRssiTextView.setText(rangingResult.getRssi() + "");
                        mSuccessesInBurstTextView.setText(
                                rangingResult.getNumSuccessfulMeasurements()
                                        + "/"
                                        + rangingResult.getNumAttemptedMeasurements());

                        //成功率
                        float successRatio =
                                ((float) mNumberOfSuccessfulRangeRequests
                                        / (float) mNumberOfRangeRequests)
                                        * 100;
                        mSuccessRatioTextView.setText(successRatio + "%");

                        mNumberOfRequestsTextView.setText(mNumberOfRangeRequests + "");
                    } else if (rangingResult.getStatus()
                            == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC) {
                        Log.d(TAG, "RangingResult failed (AP doesn't support IEEE80211 MC.");

                    } else {
                        Log.d(TAG, "RangingResult failed.");
                    }
                }else {
                    //出现不匹配信息.
                    Toast.makeText(
                            getApplicationContext(),
                            R.string
                                    .mac_mismatch_message_activity_access_point_ranging_results,
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
            queueNextRangingRequest();
        }
    }
}