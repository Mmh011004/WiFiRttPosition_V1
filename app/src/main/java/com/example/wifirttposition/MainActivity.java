package com.example.wifirttposition;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wifirttposition.utils.MyAdapter.OnScanResultListener;
import static com.example.wifirttposition.AP_RangingResultActivity.SCAN_RESULT_EXTRA;
import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.net.wifi.rtt.WifiRttManager;
import android.widget.Toast;

import com.example.wifirttposition.utils.MyAdapter;
import com.example.wifirttposition.utils.RequestPermissionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements OnScanResultListener {

    private static final String TAG = "MainActivity";
    private boolean mLocationPermissionApproved = false;

    //支持802.11mc的接入点 用于放进recyclerView的适配器中
    List<ScanResult> mAccessPointsSupporting80211mc;

    //通过广告宣传+非广播但支持FTM测量的AP 都可行的 后续主要使用这个！
    private static List<ScanResult> mAPsFTMCapable80211mc;
    private int num_of_advertised_aps; // 即仅通过广告宣传支持FTM的AP数量


    private WifiManager mWifiManager;
    private WifiScanReceiver mWifiScanReceiver;
    private WifiRttManager mWifiRttManager;

    private TextView mOutputTextView;
    private RecyclerView mRecyclerView;

    private MyAdapter mAdapter;

    private void logToTextView(String message){
        if (!message.isEmpty()){
            Log.d(TAG, message);
            mOutputTextView.setText(message);
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputTextView = findViewById(R.id.access_point_summary_text_view);
        mRecyclerView = findViewById(R.id.recycler_view);

        //固定recyclerView的视图大小
        mRecyclerView.setHasFixedSize(true);// TODO: 2023/2/10 扫描WiFi时按rssi大小排序
        //设置线性布局管理器
        RecyclerView.LayoutManager layoutManager= new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        mAccessPointsSupporting80211mc = new ArrayList<>();
        mAPsFTMCapable80211mc = new ArrayList<>();

        mAdapter = new MyAdapter(mAccessPointsSupporting80211mc, this);
        mRecyclerView.setAdapter(mAdapter);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new WifiScanReceiver();
        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        Log.d(TAG, "onCreate: mWifiRttManager:" + mWifiRttManager);
        //权限请求
        RequestPermissionUtils.requestPermission(this);
        //检查是否支持FTM
        PackageManager pm = getPackageManager();
        boolean isDeviceSupportFTM = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
        if (!isDeviceSupportFTM) {
            Toast.makeText(getApplicationContext(), ":( 对不起，当前设备不支持FTM ", Toast.LENGTH_LONG).show();
            Log.e(TAG, "onCreate: 对不起，当前设备不支持FTM :(");
            //finish();
        } else {
            String msg = ":) 当前设备支持FTM. Have fun ！";
            Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
            toast.setText(msg);//解决Toast提示信息携带应用程序名称的现象
            toast.show();
            //logToTextView("Copyright © 2022 THU. All rights reserved.️ ");
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        //是否已授权
        mLocationPermissionApproved =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        //注册接收器
        registerReceiver(
                mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

    }


    @Override
    protected void onPause() {
        super.onPause();
        
        //取消注册
        unregisterReceiver(mWifiScanReceiver);
    }



    public void onClickScanWifi(View view){
        if (mLocationPermissionApproved){
            logToTextView("Search access point...");
            mWifiManager.startScan();
        }else {
            //未授权 提醒
            RequestPermissionUtils.requestPermission(this);
        }
    }

    public void onClickPositionTest(View view) {
        if (mLocationPermissionApproved) {
            // 跳转到定位测试activity
            Intent intent = new Intent(this, PositionTestActivity.class);
            // 数据传递
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("FTMCapableAPs", (ArrayList<? extends Parcelable>) mAPsFTMCapable80211mc);
            intent.putExtras(bundle);

            startActivity(intent);
        }else {
            //未授权 提醒
            RequestPermissionUtils.requestPermission(this);
        }
    }

    public void onClickApLocationVisualization(View view){
        if (mLocationPermissionApproved){
            // TODO: 2023/2/11 点击事件：跳转可视化定位activity 完成
            Log.d(TAG, "onClickApLocationVisualization");
            //显式启动1，class跳转
            Intent intent = new Intent(this,APLocationVisualizationActivity.class);



            //隐式启动写法：
            //Intent intent2 = new Intent("action.apLocVisualActivity");

            //将支持FTM的AP列表传入APLocVisualActivity
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("FTMCapableAPs", (ArrayList<? extends Parcelable>) mAPsFTMCapable80211mc);

//            intent2.putExtras(bundle);
            intent.putExtras(bundle);
//            startActivity(intent2);
            this.startActivity(intent);

        }else{
            RequestPermissionUtils.requestPermission(this);
        }
    }

    @Override
    public void onScanResultItemClick(ScanResult scanResult) {
        Log.d(TAG, "onScanResultItemClick(): ssid: " + scanResult.SSID);

        Intent intent = new Intent(this, AP_RangingResultActivity.class);
        intent.putExtra(SCAN_RESULT_EXTRA, scanResult);
        startActivity(intent);
    }

    static class SortbyRSSI implements Comparator {
        public int compare(ScanResult a, ScanResult b) {
            return b.level - a.level;
        }

        @Override
        public int compare(Object arg1, Object arg2) {
            return this.compare(((ScanResult) arg1), ((ScanResult) arg2));
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver{

        private List<ScanResult> find80211mcSupportedAccessPoints(
                @NonNull List<ScanResult> originalList) {
            List<ScanResult> newList = new ArrayList<>();

            for (ScanResult scanResult : originalList) {

                if (scanResult.is80211mcResponder()) {
                    newList.add(scanResult);
                }
            }
            return newList;
        }


        //检查这些ap rtt受否可用
        public void ftm_rtt_check(Context context, List<ScanResult> scanResults){
            scanResults.sort(new SortbyRSSI());
            //设置仅通过广播的数量为0
            num_of_advertised_aps = 0;
            for (final ScanResult result : scanResults) {
                Log.d(TAG, "ftm_rtt_check_ScanResult: " + result.toString());
                //计算仅通过广播的数量
                num_of_advertised_aps += result.is80211mcResponder() ? 1 : 0;
                //使用Java反射修改扫描结果中的“flag”，以便找到响应但不广告的AP
                final Class cls = result.getClass();
                try {
                    Field flags = cls.getDeclaredField("flags");
                    flags.set(result, 0x0000000000000002);
                }catch (NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }

                //这里需要对每一个result进行一次ranging request 查看返回对结果对Status是否=0，如果=0，说明才是RTT capable的AP
                // 构建测距请求
                //https://iot-book.github.io/17_WiFi%E6%84%9F%E7%9F%A5/S3_%E6%A1%88%E4%BE%8B%EF%BC%9AWiFI%20ToF%E6%B5%8B%E8%B7%9D/
                RangingRequest.Builder builder =new RangingRequest.Builder();
                builder.addAccessPoint(result);
                RangingRequest req = builder.build();
                Executor executor = getApplication().getMainExecutor();
                RangingResultCallback rangingResultCallback = new RangingResultCallback() {
                    @Override
                    public void onRangingFailure(int code) {
                        Log.d("onRangingFailure", "Fail in ranging:" + Integer.toString(code));
                        try {
                            //把flags还原
                            Field flags = cls.getDeclaredField("flags");
                            flags.setAccessible(true);
                            flags.set(result, 0);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "onRangingResults: callback failure finished.");
                    }

                    @Override
                    public void onRangingResults(@NonNull List<RangingResult> results) {
                        Log.d("onRangingResults:", ""+results);
                        // 处理数据
                        if (results.size() == 1) {
                            RangingResult rangingResult = results.get(0);
                            //Log.d("onRangingResults", "rangingResult.getStatus():"+rangingResult.getStatus());
                            if (rangingResult.getStatus() != RangingResult.STATUS_SUCCESS) {
                                try {
                                    //把flags还原
                                    Field flags = cls.getDeclaredField("flags");
                                    flags.set(result, 0);
                                } catch (NoSuchFieldException | IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                            } else if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS && result.is80211mcResponder()) {
                                if(!mAPsFTMCapable80211mc.contains(result)){ //如果不包括再添加
                                    mAPsFTMCapable80211mc.add(result);
                                    Log.i(TAG, "FTM_result: " + result);
                                }
                            }
                        }
                        //Log.d("onRangingResults", "Callback success finished. mAPsFTMCapable80211mc：" + mAPsFTMCapable80211mc.size());
                        //刷新屏幕
                        if (scanResults.size() != 0) {
                            mAdapter.swapData(mAPsFTMCapable80211mc);
                            if (mLocationPermissionApproved) {
                                logToTextView(
                                        scanResults.size()
                                                + " APs, "
                                                + mAPsFTMCapable80211mc.size()
                                                + " RTT capable."
                                                + num_of_advertised_aps
                                                + " advertise RTT.");
                            } else {
                                // TODO (jewalker): Add Snackbar regarding permissions
                                Log.d(TAG, "Permissions not allowed.");
                            }
                        }
                    }
                };
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
                mWifiRttManager.startRanging(req, executor, rangingResultCallback);

            }
            Log.i(TAG, "ftm_rtt_check: " + mAPsFTMCapable80211mc);
            Log.i(TAG, "ftm_rtt_check: mAPsFTMCapable80211mc.size = " + mAPsFTMCapable80211mc.size());
            mAPsFTMCapable80211mc.clear();

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> scanResults = mWifiManager.getScanResults();
            ftm_rtt_check(context,scanResults);
        }
    }
}