package com.example.wifirttposition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.wifirttposition.entity.ApInfo;
import com.example.wifirttposition.entity.ApRangingHistoryInfo;
import com.example.wifirttposition.utils.LocationAlgorithm;
import com.example.wifirttposition.utils.LogToFile;
import com.example.wifirttposition.view.PrintImageView;
import com.example.wifirttposition.widgets.DialogCollectData;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PositionTestActivity extends AppCompatActivity {
    private static final String TAG = "PositionTestActivity";

    private PrintImageView piMap;
    private AlertDialog initPositionDialog;

    private int mNumberOfRangeRequests;
    protected WifiManager mWifiManager;
    private WifiRttManager mWifiRttManager;
    private WifiScanReceiver mWifiScanReceiver;
    private List<ScanResult> mFTMCapableAPs;
    private List<ScanResult> old_mFTMCapableAPs;
    private int mMillisecondsDelayBeforeNewRangingRequest = 400;//每200ms进行一组测距定位
    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    final Handler mRangeRequestDelayHandler = new Handler();
    private Timer timer;//每隔一段时间,就重复执行扫描AP代码
    private Handler mHandler;
    private ExecutorService mExecutorService; // 线程池
    private ScheduledExecutorService scheduler; // 定时器
    private int SNAPSHOT_POINTS_NUM = 8; // 参与shift mean 的个数
    List<double[]> snapshot_location_points; //存储连续SNAPSHOT_POINTS_NUM次定位的快照点集合 用于传入算法得到这一时段的最优定位点
    private Map<String, ApRangingHistoryInfo> map = new HashMap(); // key:AP的MAC地址，value：AP测距历史信息对象
    private Map<String, ApInfo> ap_list = new HashMap(); // key:AP的MAC地址，value：AP测距历史信息对象
    private Map<String, PointF> ap_pointF_list = new HashMap(); // key:AP的MAC地址，value：AP的坐标
    private int max_allowed_offset = 10; //定位点最大位移 10m

    private PointF mLocationMarkerRealTimeCoord;
    private PointF mLocationMarkerLastTimeCoord;

    private DialogCollectData dialogCollectData; // log弹窗
    private boolean isCollectingData; // 是否正在收集数据

    //注册用户在APRangingResultActivity中输入AP经纬度等信息的广播接收器
    private ApInfoReceiver mApInfoReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_position_test);
        Log.i(TAG, "onCreate: ");
        intiResource();
    }


    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
        //取消注册广播接收器
//        unregisterReceiver(mApInfoReceiver);
//        unregisterReceiver(mWifiScanReceiver);
        //注销定时事件
        if(timer!=null) timer.cancel();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }



    private void intiResource() {
        piMap = findViewById(R.id.pi_Map);
        dialogCollectData = new DialogCollectData(this);
        LogToFile.init(this);
        //用于存储连续定位的
        snapshot_location_points = new ArrayList<>();
        //初始化wifi
        Bundle receive = this.getIntent().getExtras();
        mFTMCapableAPs = receive.getParcelableArrayList("FTMCapableAPs");//首页中所扫描到可用的WiFi

        Log.d(TAG, "onCreate: " + mFTMCapableAPs);
        old_mFTMCapableAPs = new ArrayList<>();

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        mApInfoReceiver = new ApInfoReceiver();
        //注册广播接收器
//        registerReceiver(mApInfoReceiver, new IntentFilter("broadcast_ap_info"));
//
//        mWifiScanReceiver = new WifiScanReceiver();
//        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // 初始化线程池
        mExecutorService = Executors.newFixedThreadPool(1);
        getApList();
    }

    private void getApList() {
        List<ApInfo> apInfos = new ArrayList<>();
        // TODO: 2024/1/30 在场景中布置路由器，并且将路由器的位置信息在这里进行修改
        // 设置ap信息
        ApInfo apInfo1 = new ApInfo("setup0425.ybd","e4:5e:1b:ad:00:24",0.5,0.5);
        ApInfo apInfo2 = new ApInfo("AP1","28:bd:89:ca:2d:a3",10,10);
        ApInfo apInfo3 = new ApInfo("setup4B97.ybd","cc:f4:11:44:d8:b8",10,15);
        ApInfo apInfo4 = new ApInfo("setup5424.ybd", "28:bd:89:ed:c9:6d", 15, 15);

        apInfos.add(apInfo1);
        apInfos.add(apInfo2);
        apInfos.add(apInfo3);
        apInfos.add(apInfo4);

        for (ApInfo apInfo: apInfos) {
            ap_list.put(apInfo.getBSSID(),apInfo);
            ap_pointF_list.put(apInfo.getBSSID(),new PointF((float)apInfo.getLongitude(),(float)apInfo.getLatitude()));
        }
    }
    public void onClickStartPosition(View view) {
        piMap.clearPoints();
        View inflate = getLayoutInflater().inflate(R.layout.progress_cycle, null);
        initPositionDialog = new AlertDialog.Builder(this)
                .setView(inflate)
                .setMessage("正在定位中...")
                .show();
        // handler 处理收到的消息
        timer = new Timer();
        // 加入Looper.getMainLooper()是为了在主线程中执行
        // 如果没有这句话，那么就会在当前的线程执行
        // TODO: 2024/2/1 到时候删掉这个试试呢
        mHandler = new Handler(Looper.getMainLooper()){
            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(Message msg) {
                if(msg.what == 0){
                    if(mLocationMarkerRealTimeCoord == null){
                        scan_to_find_all_FTM_capable_aps();//扫描当前活跃AP
                        // TODO: 2024/1/30 更新活跃AP位置
//                        updateActiveAPMarker();//更新活跃AP位置
                        ranging_request_to_all_FTM_capable_aps(); //更新定位STA点
                    }
                    if(mLocationMarkerLastTimeCoord == null && mLocationMarkerRealTimeCoord!=null){ //初始化
                        mLocationMarkerLastTimeCoord = mLocationMarkerRealTimeCoord;
                    }
                    if(mLocationMarkerLastTimeCoord!=null
                            && mLocationMarkerRealTimeCoord!=null
                            && offset_bettween(mLocationMarkerLastTimeCoord,mLocationMarkerRealTimeCoord)>max_allowed_offset) {
                        Toast.makeText(getApplicationContext(),"检测到较大位移",Toast.LENGTH_SHORT).show();
                        scan_to_find_all_FTM_capable_aps();//扫描当前活跃AP
                        // TODO: 2024/1/30 更新活跃AP位置
//                        updateActiveAPMarker();//更新活跃AP位置
                        ranging_request_to_all_FTM_capable_aps(); //更新定位STA点
                    }
                }
            }
        };
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // (1) 使用handler发送消息
                Message message=new Message();
                message.what=0;
                mHandler.sendMessage(message);
            }
        },0,10000);//每隔10秒使用handler发送一下消息,也就是每隔10秒执行一次,一直重复执行

//        scheduler = Executors.newSingleThreadScheduledExecutor();
//        scheduler.scheduleAtFixedRate(new Runnable(){
//            @Override
//            public void run() {
//                // (2) 使用runOnUiThread发送消息
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        // 处理
//                    }
//                });
//            });
//        });


        // TODO: 2024/1/30 开始定位 定位算法



    }

    //计算两个点之间的距离
    private double offset_bettween(PointF c1, PointF c2) {
        double [] c1_position = new double[]{c1.x,c1.y};
        double [] c2_position = new double[]{c2.x,c2.y};
        return LocationAlgorithm.euclid_distance(c1_position,c2_position);
    }

    public void onClickRecordInfo(View view) {
        // 弹出
        dialogCollectData.show();
        isCollectingData = dialogCollectData.collectingData;
    }

    public void onClickStopPosition(View view) {

    }


    //扫描
    public void scan_to_find_all_FTM_capable_aps(){
        mWifiManager.startScan();
    }
    /*
    * 测距请求
    * */
    public void ranging_request_to_all_FTM_capable_aps(){
        // 查看权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finish();
        }
        // 无可用WiFi
        if (mFTMCapableAPs.size() == 0) {
            Toast.makeText(this, "无可用WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        // 请求次数
        mNumberOfRangeRequests++;
        // 开始测距请求
        RangingRequest rangingRequest =
                new RangingRequest.Builder().addAccessPoints(mFTMCapableAPs).build();
        Log.i(TAG, "ranging_request_to_all_FTM_capable_aps: " + mFTMCapableAPs);
        Log.i(TAG, "ranging_request_to_all_FTM_capable_aps: mFTMCapableAPs.size() = " + mFTMCapableAPs.size());

        // 不在主线程中，在一个线程池中执行

        mWifiRttManager.startRanging(rangingRequest, getApplication().getMainExecutor(), new RangingResultCallback() {
            // 开启下次请求，用于连续请求
            private void queueNextRangingRequest() {
                mRangeRequestDelayHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ranging_request_to_all_FTM_capable_aps();
                    }
                }, mMillisecondsDelayBeforeNewRangingRequest);
            }
            @Override
            public void onRangingFailure(int code) {
                Log.e(TAG, "onRangingFailure: " + code);
                if (piMap == null) return;
                queueNextRangingRequest();
            }

            @Override
            public void onRangingResults(@NonNull List<RangingResult> results) {
                Log.d(TAG, "onRangingRequest onRangingResults: " + results);
                if (piMap == null) return;
                // 计算定位点
                calculateLocation(results);
                queueNextRangingRequest();
            }
        });
    }

    private boolean calculateLocation(List<RangingResult> rangingResults) {
        // 预处理ap_count,从这次成功测距的结果中提取出既能测距成功又已知坐标的数据
        int ap_count = 0;
        List<RangingResult> datalist = new ArrayList<>();
        for (RangingResult rangingResult : rangingResults){
            if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS){
                // 看这个ap是否在ap_list中
                if (ap_list.containsKey(rangingResult.getMacAddress().toString())){
                    Log.d(TAG, "calculateLocation: ap_count:" + rangingResult.getMacAddress());
//                    Log.i(TAG, "calculateLocation: " + rangingResult);
                    datalist.add(rangingResult);
                    ap_count++;
                }
            }
        }
        if (dialogCollectData.collectingData){
            Log.i(TAG, "collect data" + datalist);
            LogToFile.i(TAG, "collect data: " + datalist, dialogCollectData.logName);
        }
        datalist.clear();
        Log.i(TAG, "calculateLocation: ap_count = " + ap_count);
        if (ap_count < 3) {
            Log.e(TAG, "calculateLocation: 至少需要3个支持FTM的AP才能进行定位。ap_count: " + ap_count);

            return false;
        }

        return true;
    }


    /**
     * 主要目的，接收mWifiManager.startScan()的AP扫描结果
     * 重新装载 mFTMCapableAPs
     */
    class WifiScanReceiver extends BroadcastReceiver {

        // This is checked via mLocationPermissionApproved boolean
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            old_mFTMCapableAPs = new ArrayList<>(mFTMCapableAPs); // 注意这里不能直接赋=，相当于传引用，当mFTMCapableAPs变化，old_mFTMCapableAPs也会变化
            mFTMCapableAPs.clear();

            List<ScanResult> scanResults = mWifiManager.getScanResults();
            for (ScanResult scanResult : scanResults) {
                if (scanResult.is80211mcResponder() && !mFTMCapableAPs.contains(scanResult)) {
                    mFTMCapableAPs.add(scanResult);
                } else {
                    //通过反射验证FTMCapableAP
                    Class cls = scanResult.getClass();
                    try {
                        Field flags = cls.getDeclaredField("flags");
                        flags.set(scanResult, 0x0000000000000002);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    //再进行一次测距request
                    try {
                        RangingRequest rangingRequest =
                                new RangingRequest.Builder().addAccessPoint(scanResult).build();
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }

                        final boolean[] is_AP_supported_FTM = {false};
                        mWifiRttManager.startRanging(
                                rangingRequest, getApplication().getMainExecutor(), new RangingResultCallback() {
                                    @Override
                                    public void onRangingFailure(int code) {
                                        Log.e(TAG, "onRangingFailure: " + code);
                                        //修改FLAGS后依然测距失败，那就恢复之前的状态
                                        try {
                                            Field flags = cls.getDeclaredField("flags");
                                            flags.set(scanResult, 0);
                                        } catch (NoSuchFieldException | IllegalAccessException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onRangingResults(@NonNull List<RangingResult> results) {
                                        Log.d(TAG, "onReceive onRangingResults: " + results);

                                        for(RangingResult rr : results){
                                            if(rr.getStatus() == RangingResult.STATUS_SUCCESS && !mFTMCapableAPs.contains(scanResult)){
                                                synchronized (this){
                                                    mFTMCapableAPs.add(scanResult);
                                                    is_AP_supported_FTM[0] = true;
                                                }
                                            }
                                            else{
                                                //修改FLAGS后依然测距失败，那就恢复之前的状态
                                                try {
                                                    Field flags = cls.getDeclaredField("flags");
                                                    flags.set(scanResult, 0);
                                                } catch (NoSuchFieldException | IllegalAccessException e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                        }
                                    }
                                });

                        if(is_AP_supported_FTM[0]==true){
                            Log.i(TAG, "onReceive: AP_supports_FTM"+scanResult.toString());
//                        mFTMCapableAPs.add(scanResult);
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }

                }
            }

        }
    }


    /*
     * 接收ap_info
     * 利用AP测距页面的intent传递ap_info
     * 但是对于ap_info的修改，需要广播
     * */
    class ApInfoReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle ap_info_bundle = intent.getBundleExtra("ap_info");
            ApInfo apInfo = (ApInfo) ap_info_bundle.getSerializable("ap_info");
            Log.d(TAG, "onReceive: "+ apInfo.toString());
            Toast.makeText(getApplicationContext(),"收到广播:"+apInfo.toString(),Toast.LENGTH_SHORT).show();

        }
    }
}

