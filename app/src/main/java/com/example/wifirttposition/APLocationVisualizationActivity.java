package com.example.wifirttposition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.example.wifirttposition.entity.ApInfo;
import com.example.wifirttposition.entity.ApRangingHistoryInfo;
import com.example.wifirttposition.entity.LocationInfo;
import com.example.wifirttposition.utils.DBConnectHelper;
import com.example.wifirttposition.utils.FMCoordTransformer;
import com.example.wifirttposition.utils.LocationAlgorithm;
import com.example.wifirttposition.utils.ViewHelper;
import com.example.wifirttposition.widgets.ImageViewCheckBox;
import com.fengmap.android.FMMapSDK;
import com.fengmap.android.analysis.navi.FMNaviAnalyser;
import com.fengmap.android.analysis.navi.FMNaviOption;
import com.fengmap.android.analysis.navi.FMNavigationInfo;
import com.fengmap.android.analysis.navi.FMSimulateNavigation;
import com.fengmap.android.analysis.navi.OnFMNavigationListener;
import com.fengmap.android.exception.FMObjectException;
import com.fengmap.android.map.FMMap;
import com.fengmap.android.map.FMMapUpgradeInfo;
import com.fengmap.android.map.FMMapView;
import com.fengmap.android.map.FMViewMode;
import com.fengmap.android.map.event.OnFMMapChangeListener;
import com.fengmap.android.map.event.OnFMMapClickListener;
import com.fengmap.android.map.event.OnFMMapInitListener;
import com.fengmap.android.map.event.OnFMNodeListener;
import com.fengmap.android.map.geometry.FMGeoCoord;
import com.fengmap.android.map.geometry.FMMapCoord;
import com.fengmap.android.map.layer.FMImageLayer;
import com.fengmap.android.map.layer.FMLineLayer;
import com.fengmap.android.map.layer.FMLocationLayer;
import com.fengmap.android.map.marker.FMImageMarker;
import com.fengmap.android.map.marker.FMLocationMarker;
import com.fengmap.android.map.marker.FMNode;
import com.fengmap.android.utils.FMLog;
import com.fengmap.android.widget.FMNodeInfoWindow;
import com.fengmap.android.widget.FMSwitchFloorComponent;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class APLocationVisualizationActivity extends AppCompatActivity implements
        OnFMNodeListener,
        OnFMMapInitListener,
        OnFMMapClickListener,
        ImageViewCheckBox.OnCheckStateChangedListener,
        OnFMNavigationListener {

    private static final String TAG = "APLocationVisualizationActivity";
    private String DEFAULT_MAP_ID = "1644242462219005954";
    //构建地图节点的信息弹窗对象
    private FMNodeInfoWindow mInfoWindow;
    //map对象
    protected FMMap mFMMap;
    //定义地图的容器
    protected FMMapView mapView;

    private int mNumberOfRangeRequests;
    protected WifiManager mWifiManager;
    private WifiRttManager mWifiRttManager;
    private WifiScanReceiver mWifiScanReceiver;
    private List<ScanResult> mFTMCapableAPs;
    private List<ScanResult> old_mFTMCapableAPs;
    private int mMillisecondsDelayBeforeNewRangingRequest = 100;//每200ms进行一组测距定位
    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    final Handler mRangeRequestDelayHandler = new Handler();
    private Timer timer;//每隔一段时间,就重复执行扫描AP代码
    private Handler mHandler;


    //FMMapCoord定义和构建地图坐标对象
    private List<FMMapCoord> walking_routes;//用来保存STA的历史定位轨迹
    //FMGeoCoord在该地图上的空间坐标
    private List<FMGeoCoord> walking_geo_routes;//用来保存STA的历史定位点轨迹集合
    private int SNAPSHOT_POINTS_NUM = 8;
    List<double[]> snapshot_location_points; //存储连续SNAPSHOT_POINTS_NUM次定位的快照点集合 用于传入算法得到这一时段的最优定位点

    private Map<String, ApRangingHistoryInfo> map = new HashMap(); // key:AP的MAC地址，value：AP测距历史信息对象
    private Map<String, ApInfo> ap_list = new HashMap(); // key:AP的MAC地址，value：AP测距历史信息对象

    private FMLocationLayer mLocationLayer;//定位STA图层
    private FMImageLayer mAPLayer;//ap图层

    private FMLocationMarker mLocationMarker;//定位点标记
    private FMLocationMarker mAMapLocationMarker;//高德定位点标记
    private FMGeoCoord mLocationMarkerRealTimeCoord; //定位点实时位置
    private FMGeoCoord mLocationMarkerLastTimeCoord; //定位点10s前的位置
    private int max_allowed_offset = 10; //定位点最大位移 10m

    private float mLocationMarkerAngle; //定位点方位角，结合高德定位点得到
    private float mLocationMarkerSpeed; //定位点速度

    private FMImageMarker imageStartMarker; //导航起点标记
    private FMImageMarker imageEndMarker; //导航终点标记

    //注册用户在APRangingResultActivity中输入AP经纬度等信息的广播接收器
    private ApInfoReceiver mApInfoReceiver;


    //高德地图SDK
    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    //声明定位回调监听器
    public AMapLocationListener mLocationListener;
    //声明AMapLocationClientOption对象
    public AMapLocationClientOption mLocationOption = null;


    // 地图是否加载完成
    protected boolean isMapLoaded;

    // 点移距离视图中心点超过最大距离5米，就会触发移动动画
    protected static final double NAVI_MOVE_CENTER_MAX_DISTANCE = 5;

    //是否在导航中
    private boolean isInNavigationProcess = false;

    // 导航配置
    protected FMNaviOption mNaviOption;

    //数据库相关
    private Connection conn = null;

    // TODO: 2023/4/4 ScanResultT中的导航路径规划和导航过程没有加入

    /**导航路径规划系列*/
    protected FMMapCoord mStartCoord;//起点坐标
    protected FMGeoCoord mStartGeoCoord;
    protected int mStartGroupId;//起点楼层
    protected FMImageLayer mStartImageLayer;//起点图层
    protected FMMapCoord mEndCoord;//终点坐标
    protected FMGeoCoord mEndGeoCoord;
    protected int mEndGroupId;//终点楼层id
    protected FMImageLayer mEndImageLayer;//终点图层
    protected FMLineLayer mLineLayer;//导航线图层
    protected FMNaviAnalyser mNaviAnalyser;//导航分析

    /**导航过程相关*/
    // 约束过的定位标注
    private FMLocationMarker mHandledMarker;
    // 是否为第一人称
    private boolean mIsFirstView = true;
    // 是否为跟随状态
    private boolean mHasFollowed = true;
    // 总共距离
    private double mTotalDistance;

    // 计时器，每隔一段时间更新导航点位置
    private Timer mTimer;
    // 楼层切换控件
    private FMSwitchFloorComponent mSwitchFloorComponent;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //初始化
        FMMapSDK.init(this);
        Log.d(TAG, "onCreate()"+"Version"+FMMapSDK.getVersion()+ " SDKKey"+FMMapSDK.getSDKKey() +" PackageName"+FMMapSDK.getPackageName()
        +" Sha1"+ FMMapSDK.getSha1Value());
        setContentView(R.layout.activity_aplocation_visualization);

        walking_routes = new ArrayList<>();
        walking_geo_routes = new ArrayList<>();
        //用于存储连续定位的
        snapshot_location_points = new ArrayList<>();

        mapView = (FMMapView) findViewById(R.id.mapview);
        //获取地图操作对象
        mFMMap = mapView.getFMMap();
        //打开地图
        mFMMap.openMapById(DEFAULT_MAP_ID,true);
        Log.d(TAG, "onCreate: 打开地图1");
        //设置初始化监听
        mFMMap.setOnFMMapInitListener(this);
        Log.d(TAG, "onCreate: 打开地图2");

        Bundle receive = this.getIntent().getExtras();
        mFTMCapableAPs = receive.getParcelableArrayList("FTMCapableAPs");//首页中所扫描到可用的WiFi

        Log.d(TAG, "onCreate: " + mFTMCapableAPs);
        old_mFTMCapableAPs = new ArrayList<>();

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

        mApInfoReceiver = new ApInfoReceiver();

        //注册广播接收器
        registerReceiver(mApInfoReceiver, new IntentFilter("broadcast_ap_info"));

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new WifiScanReceiver();
        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        //高德地图隐私合规校验
        AMapLocationClient.updatePrivacyShow(getApplicationContext(),true,true);
        AMapLocationClient.updatePrivacyAgree(getApplicationContext(),true);

        Log.d(TAG, "onCreate: the end");


        //加载数据库里当前建筑地图的AP信息,2层
        //getFloorAPs(1,2);
        // 采取手动输入AP信息的方式
        getFloorAPsByMyself(1,2);
        Log.d(TAG, "onCreate: "+ap_list);
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
        unregisterReceiver(mApInfoReceiver);
        unregisterReceiver(mWifiScanReceiver);
        //注销定时事件
        if(timer!=null) timer.cancel();
        if(mTimer!=null) mTimer.cancel();
        //停止高德定位
        mLocationClient.stopLocation();
        //关闭数据库链接
        if(conn!=null) {
            try {
                conn.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    /**
     * 查询数据库表中的AP信息
     * 直连数据库有问题
     * 不用
     */
    public void getFloorAPs(int building_id,int floor_id){
        new  Thread(){
            public  void run(){
                try {
                    //初始化数据库链接，必须要线程里创建链接
                    conn =(Connection) DBConnectHelper.getConn();
                    if(conn!=null){ //判断 如果返回不为空则说明链接成功 如果为null的话则连接失败 请检查你的 mysql服务器地址是否可用 以及数据库名是否正确 并且 用户名跟密码是否正确
                        Log.d("调试","连接成功");
                        Statement stmt = conn.createStatement(); //根据返回的Connection对象创建 Statement对象
                        String sql = "select * from access_points where building_id="+building_id+" and floor_id="+floor_id; //要执行的sql语句
                        ResultSet rs = stmt.executeQuery(sql); //使用executeQury方法执行sql语句 返回ResultSet对象 即查询的结果
                        while (rs.next()){
                            String bssid = rs.getString("bssid");
                            String ssid = rs.getString("ssid");
                            Double longitude = rs.getDouble("longitude");
                            Double latitude = rs.getDouble("latitude");
                            Double altitude = rs.getDouble("altitude");
                            ApInfo apInfo = new ApInfo(ssid,bssid,longitude,latitude,altitude,floor_id);
                            ap_list.put(bssid,apInfo);
                            Log.i(TAG, "run: "+bssid+"|"+ssid);
                        }
                        stmt.close();
                    }else{
                        Log.e("select调试","连接失败");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.start();

    }

    public void getFloorAPsByMyself(int building_id,int floor_id){
        List<ApInfo> list = new ArrayList<>();
/*

        //寝室
        ApInfo apInfo1 = new ApInfo("setup0425.ybd","e4:5e:1b:ad:00:24",11867348.8572,3443812.7665,0.83,floor_id);
        ApInfo apInfo2 = new ApInfo("setupA4B9.ybd","28:bd:89:ca:2d:a3",11867349.4391,3443817.9338,0.86,floor_id);
        ApInfo apInfo3 = new ApInfo("setup4B97.ybd","cc:f4:11:44:d8:b8",11867350.6521,3443812.0955,0.61,floor_id);
*/

/*
        //商场
        ApInfo apInfo4 = new ApInfo("setup0425.ybd","e4:5e:1b:ad:00:24",12613422.273672653,2642638.417287323,0.80,floor_id);
        ApInfo apInfo5 = new ApInfo("setupA4B9.ybd","28:bd:89:ca:2d:a3",12613417.84825942,2642633.9918718096,0.80,floor_id);
        ApInfo apInfo6 = new ApInfo("setup4B97.ybd","cc:f4:11:44:d8:b8",12613425.899311645,2642633.8319196943,0.80,floor_id);
*/

/*
        //实验楼
        ApInfo apInfo1 = new ApInfo("setup0425.ybd","e4:5e:1b:ad:00:24",11866852.5607,3442996.8546,0.92,floor_id);
        ApInfo apInfo2 = new ApInfo("setupA4B9.ybd","28:bd:89:ca:2d:a3",11866847.9347,3442982.0994,0.90,floor_id);
        ApInfo apInfo3 = new ApInfo("setup4B97.ybd","cc:f4:11:44:d8:b8",11866841.9536,3442996.8840,0.87,floor_id);
*/

        //3101
        ApInfo apInfo1 = new ApInfo("setup0425.ybd","e4:5e:1b:ad:00:24",12578642.1324,3252515.0044,0.92,floor_id);
        ApInfo apInfo2 = new ApInfo("setupA4B9.ybd","28:bd:89:ca:2d:a3",12578629.1716,3252520.5682,0.90,floor_id);
        ApInfo apInfo3 = new ApInfo("setup4B97.ybd","cc:f4:11:44:d8:b8",12578629.1562,3252514.8941,0.87,floor_id);


        list.add(apInfo2);
        list.add(apInfo1);
        list.add(apInfo3);

        for (ApInfo apInfo: list){
            String bssid = apInfo.getBSSID();
            ap_list.put(bssid,apInfo);
        }
        Log.d(TAG, "getFloorAPsByMyself: "+ ap_list);
    }

    //导航途径楼层
    @Override
    public void onCrossGroupId(int lastGroupId, int currGroupId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //设置焦点层
                mFMMap.setFocusByGroupId(currGroupId,null);
                updateLocateGroupView();
            }
        });
    }

    public void updateLocateGroupView(){
        int groupSize = mFMMap.getFMMapInfo().getGroupSize();
        int position = groupSize - mFMMap.getFocusGroupId();
        mSwitchFloorComponent.setSelected(position);
    }

    //导航中
    @Override
    public void onWalking(FMNavigationInfo fmNavigationInfo) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onWalking.run: 运行中...");
                //定位位置的偏移量
                double offset = fmNavigationInfo.getOffsetDistance();

                FMLog.le("offset distance", ""+ offset);

                // 被约束过的点
                FMGeoCoord contraintedCoord = fmNavigationInfo.getPosition();

                // 更新定位标志物
                updateHandledMarker(contraintedCoord, fmNavigationInfo.getAngle());

/*                // 更新路段显示信息
                updateWalkRouteLine(fmNavigationInfo);*/

                // 更新导航配置
                updateNavigationOption();
            }
        });

    }

    //更新导航配置
    private void updateNavigationOption() {
        mNaviOption.setFollowAngle(mIsFirstView);
        mNaviOption.setFollowPosition(mHasFollowed);
    }
    /**
     * 更新约束定位点
     *
     * @param coord 坐标
     */
    private void updateHandledMarker(FMGeoCoord coord, float angle) {
        if (mHandledMarker == null) {
            mHandledMarker = ViewHelper.buildLocationMarker(coord.getGroupId(), coord.getCoord(), angle);
            mLocationLayer.addMarker(mHandledMarker);
        } else {
            mHandledMarker.updateAngleAndPosition(coord.getGroupId(), angle, coord.getCoord());
        }
    }

    /**
     * 删除导航约束定位点标记
     */
    private void removeHandledMarker(){
        if (mHandledMarker != null) {
            mLocationLayer.removeMarker(mHandledMarker);
            mHandledMarker = null;
        }
    }

    //导航完成
    @Override
    public void onComplete() {

    }

    //点击起始点的
    @Override
    public void onMapClick(float v, float v1) {

    }

    //扫描
    public void scan_to_find_all_FTM_capable_aps(){
        mWifiManager.startScan();
    }

    //测距请求
    public void ranging_request_to_all_FTM_capable_aps(){
        //权限要有，不然退出
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            finish();
        }
        //无可用WiFi
        if (mFTMCapableAPs.size() == 0){
            return;
        }
        //计数加一
        mNumberOfRangeRequests++;

        //开始测距请求
        RangingRequest rangingRequest =
                new RangingRequest.Builder().addAccessPoints(mFTMCapableAPs).build();

        mWifiRttManager.startRanging(rangingRequest, getApplication().getMainExecutor(), new RangingResultCallback() {
            //开启下次请求，算递归吧
            private void queueNextRangingRequest() {
                mRangeRequestDelayHandler.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                ranging_request_to_all_FTM_capable_aps();
                            }
                        },
                        mMillisecondsDelayBeforeNewRangingRequest);
            }
            @Override
            public void onRangingFailure(int code) {
                Log.e(TAG, "onRangingFailure: "+code);
                if(mFMMap == null) return;//说明用户已经退出了定位界面，退出
                queueNextRangingRequest();
            }

            @Override
            public void onRangingResults(@NonNull List<RangingResult> results) {
                Log.d(TAG, "onRangingResults: "+results);
                if(mFMMap == null) return;//说明用户已经退出了定位界面

                cal_STA_location(results);
                queueNextRangingRequest();
            }

            /**
             * 通过定位算法，计算AP的测距结果并添加预测的坐标至全局变量
             */
            public boolean cal_STA_location(@NonNull List<RangingResult> rangingResults){
                // 预处理ap_count，得到本次成功测距结果中的已部署AP的数量，即计算既知道坐标又能返回测距结果的AP的数量
                int ap_count = 0;
                for (RangingResult rangingResult : rangingResults){
                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS){
                        if (map.containsKey(rangingResult.getMacAddress().toString())){
                            Log.d(TAG, "cal_STA_location: ap_count: "+rangingResult.getMacAddress());
                            ap_count++ ;
                        }
                    }
                }

                if(ap_count < 2) {
                    Log.e(TAG, "cal_STA_location: 至少需要3个支持FTM测量的AP才能定位，mFTMCapableAPs.size():"+mFTMCapableAPs.size());
                    for(ScanResult sr: mFTMCapableAPs){// todo mFTMCapableAPs 到后面经常size=1 or 2 有问题
                        Log.d(TAG, "cal_STA_location_mFTMCapableAPs: "+sr.SSID+" "+sr.BSSID );
                    }
                    return false;
                }

                //所部署的ap的三维坐标
                double[][] positions = new double[ap_count][3];
                //ap测距信息
                double[] distances = new double[ap_count];

                int i = 0;
                for (RangingResult rangingResult: rangingResults){
                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS){
                        Log.d(TAG, "cal_STA_location: " + rangingResult);
                        if (map.containsKey(rangingResult.getMacAddress().toString())){
                            ApRangingHistoryInfo apRangingHistoryInfo = map.get(rangingResult.getMacAddress().toString());
                            assert apRangingHistoryInfo != null;
                            //记录AP测距结果，存入历史测距数据
                            apRangingHistoryInfo.add_ranging_record(rangingResult); //记录AP测距结果，存入历史测距数据
                            //拿到AP坐标
                            positions[i] = map.get(rangingResult.getMacAddress().toString()).getApinfo().getPosition();//拿到AP坐标
                            Log.d(TAG, "cal_STA_location: position[" + i +"]  "+ rangingResult.getMacAddress().toString() +Arrays.toString(positions[i]));
                            //本次测距结果 单位m
                            distances[i] = rangingResult.getDistanceMm() / 1000f; //本次测距结果 单位m
                            //Log.d(TAG, "cal_STA_location: distances[" + i +"]"+distances[i]);
                            // 0.762 参考 http://people.csail.mit.edu/bkph/other/WifiRttScanX/FTM_RTT_AP_ratings.txt
                            i++;
                        }
                    }
                }
                /*Log.d(TAG, "cal_STA_location: positions[]"+positions);
                Log.d(TAG, "cal_STA_location: distances[]"+distances);*/
                // TODO: 2023/4/11 可加调试
                //计算用户位置，调用定位算法 非线性最小二乘求解器  莱文伯格马夸特优化器
                Log.d(TAG, "cal_STA_location: distances" + Arrays.toString(distances));
                NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
                LeastSquaresOptimizer.Optimum optimum = solver.solve();
                double[] centroid = optimum.getPoint().toArray();
                Log.d(TAG, "cal_STA_location: 预测坐标为 "+ Arrays.toString(centroid));

                // 每SNAPSHOT_POINTS_NUM个坐标代入Mean_shift聚类算法求最密集点，作为最终预测坐标
                snapshot_location_points.add(centroid);
                if (snapshot_location_points.size() == SNAPSHOT_POINTS_NUM){
                    //计算平均值
                    double[] init_point = new double [centroid.length];
                    for(int x=0;x<init_point.length;x++){
                        double tmp_sum = 0;
                        for(int j=0;j<snapshot_location_points.size();j++){
                            tmp_sum += snapshot_location_points.get(j)[x];
                        }
                        init_point[x] = tmp_sum / snapshot_location_points.size();
                    }
                    //init_point实际上就是10个点的平均坐标
                    centroid = LocationAlgorithm.mean_shift(snapshot_location_points,init_point);
                    //Log.d(TAG, "cal_STA_location: snapshot"+ Arrays.toString(snapshot_location_points.get(1)));
                    snapshot_location_points.clear();
                    //地图地理坐标
                    FMGeoCoord coord = null;

                    //用户移动速度>=0, 才录入定位坐标
                    if (mLocationMarkerSpeed>=0){
                        Log.d(TAG, "cal_STA_location: mLocationMarkerSpeed" + mLocationMarkerSpeed);
                        LocationInfo locationInfo = new LocationInfo();
                        if (centroid.length == 3){
                            //最终点  加入
                            walking_routes.add(new FMMapCoord(centroid[0],centroid[1],centroid[2]));
                            Log.d(TAG, "cal_STA_location: walking_routes的最后一个，也就是最近加入的"+ walking_routes.get(walking_routes.size()-1));
                            //得出地图空间坐标
                            coord = new FMGeoCoord(mFMMap.getFocusGroupId(),//todo 默认单层定位，待优化
                                    new FMMapCoord(centroid[0], centroid[1],centroid[2]));
                            locationInfo.setLongitude(centroid[0]);
                            locationInfo.setLatitude(centroid[1]);
                            locationInfo.setAltitude(centroid[2]);

                        }else if(centroid.length==2){
                            walking_routes.add(new FMMapCoord(centroid[0],centroid[1]));
                            coord = new FMGeoCoord(mFMMap.getFocusGroupId(),//todo 默认单层定位，待优化
                                    new FMMapCoord(centroid[0], centroid[1]));
                            locationInfo.setLongitude(centroid[0]);
                            locationInfo.setLatitude(centroid[1]);
                        }
                        //todo 是否需要检验新定位点是否漂移？


                        walking_geo_routes.add(coord);
                        mLocationMarkerRealTimeCoord = coord;
                        Log.d(TAG, "cal_STA_location: mean_shift之后的定位坐标"+ coord);

                        // TODO: 2023/4/11 将定位数据加入数据库


                    }
                    //更新定位点在地图的位置
                    if (!isInNavigationProcess){
                        updateLocationMarker();
                        Log.d(TAG, "cal_STA_location: updateLocationMarker");
                    }else {
                        removeLocationMarker();
                    }


                }


                return true;
            }
        });

    }
    //更新STA位置
    public void updateLocationMarker(){
        Log.d(TAG, "updateLocationMarker: 更新定位标记点");
        if (mLocationMarker == null){
            mLocationMarker = ViewHelper.buildLocationMarker(mFMMap.getFocusGroupId(),
                    walking_routes.get(walking_routes.size()-1),mLocationMarkerAngle);
            Log.d(TAG, "updateLocationMarker: 首次"+ mLocationMarker);
            mLocationLayer.addMarker(mLocationMarker);

        }
        else {
            mLocationMarker.updateAngleAndPosition(mLocationMarkerAngle,walking_routes.get(walking_routes.size()-1));
            Log.d(TAG, "updateLocationMarker: 方向角"+ mLocationMarkerAngle);

 /*           //地图实时跟随，即定位点始终在地图中间
            if(mFMMap != null)
                mFMMap.moveToCenter(walking_routes.get(walking_routes.size()-1),true);
                mFMMap.move(walking_routes.get(walking_routes.size()-2),walking_routes.get(walking_routes.size()-1),true);
*/


            //尝试平滑移动, 模拟导航
            FMSimulateNavigation mNavigation = new FMSimulateNavigation(mFMMap);
            mNavigation.setStartPoint(walking_geo_routes.get(walking_geo_routes.size()-2));
            mNavigation.setEndPoint(walking_geo_routes.get(walking_geo_routes.size()-1));
            // 创建模拟导航属性配置对象
            mNaviOption = new FMNaviOption();

            // 点移距离视图中心点超过最大距离5米，就会触发移动动画；若设为0，则实时居中
            mNaviOption.setNeedMoveToCenterMaxDistance(NAVI_MOVE_CENTER_MAX_DISTANCE);
            // 设置配置
            mNavigation.setNaviOption(mNaviOption);
            // 设置导航监听接口
            //需要导航的接口
            mNavigation.setOnNavigationListener(this);
            FMSimulateNavigation simulateNavigation = mNavigation;
            // 3米每秒。
            simulateNavigation.simulate(3.0f);
            mNavigation.clear();
        }
        Log.d(TAG, "updateLocationMarker: "+ mLocationMarker);

    }

    public void removeLocationMarker(){
        if(mLocationMarker!=null){
            mLocationLayer.removeMarker(mLocationMarker);
            mLocationMarker = null;
        }
    }

    //更新活跃的ap
    public void updateActiveAPMarker(){
        if(mFMMap == null)
            return;
        Toast.makeText(getApplicationContext(),"更新AP状态...",Toast.LENGTH_SHORT).show();
        //mFTMCapableAPs首页中检测的可用AP，ap_list是设置的ap数据，自己所放置的ap，包括位置信息等
        for (ScanResult sr : mFTMCapableAPs){
            if(ap_list.containsKey(sr.BSSID)){
                ApInfo ap_info = ap_list.get(sr.BSSID);
                removeAPImageMarker(mAPLayer,ap_info);
                createAPImageMarker(mAPLayer,ap_info);
                Log.d(TAG, "updateActiveAPMarker: 更新的AP"+ ap_info);
            }
        }
    }
    protected void createAPImageMarker(FMImageLayer mAPLayer ,ApInfo ap_info){
        if(ap_info == null) return;
        FMMapCoord ap_coord = new FMMapCoord(ap_info.getLongitude(), ap_info.getLatitude(), ap_info.getAltitude());
        FMImageMarker imageMarker = ViewHelper.buildImageMarker(getResources(), ap_coord, R.drawable.ic_wifi);
        Bundle ap_info_bundle = new Bundle();
        ap_info_bundle.putSerializable("ap_info", (Serializable) ap_info);
        imageMarker.setBundle(ap_info_bundle);
        mAPLayer.addMarker(imageMarker);

        //把AP信息存入map对象
        ApRangingHistoryInfo apRangingHistoryInfo = new ApRangingHistoryInfo(ap_info,new ArrayList<RangingResult>());
        map.put(ap_info.getBSSID(),apRangingHistoryInfo);

    }

    protected void removeAPImageMarker(FMImageLayer imageLayer,ApInfo ap_info){

        //解决java.util.ConcurrentModificationException异常，参考：https://www.jianshu.com/p/c5b52927a61a
        Iterator<FMImageMarker> markers = imageLayer.getAll().iterator();
        synchronized (imageLayer.getAll()){
            while(markers.hasNext()){
                FMImageMarker marker = markers.next();
                if(marker.getGroupId()+1 == ap_info.getFloor_id() &&
                        marker.getPosition().toString().equals(ap_info.getFMMapCoord().toString())){
                    markers.remove();
                    imageLayer.removeMarker(marker);
                }
            }
        }
    }

    //添加定位标注
    private void addLocationMarker() {
        FMMapCoord position = new FMMapCoord(1.1867363230774025E7,3443808.014451876,0.0);
        FMLocationMarker locationMarker = new FMLocationMarker(mFMMap.getFocusGroupId(), position);
        //设置定位点图片
        locationMarker.setActiveImageFromRes(R.drawable.active);
        //设置定位图片宽高
        locationMarker.setMarkerWidth(60);
        locationMarker.setMarkerHeight(60);
        mLocationLayer.addMarker(locationMarker);
    }

    @SuppressLint("HandlerLeak")
    @Override
    public void onMapInitSuccess(String s) {
        Log.d(TAG, "onMapInitSuccess: success");

        // TODO: 2023/4/11 在线加载主题


        int groupId = mFMMap.getFocusGroupId();//定位点图层
        mLocationLayer = mFMMap.getFMLayerProxy().getFMLocationLayer();
        mFMMap.addLayer(mLocationLayer);
        //addLocationMarker();

        //AP图片图层
        mAPLayer = mFMMap.getFMLayerProxy().getFMImageLayer(groupId);
        mAPLayer.setOnFMNodeListener(this);
//        updateActiveAPMarker();

        //线图层
        mLineLayer = mFMMap.getFMLayerProxy().getFMLineLayer();
        mFMMap.addLayer(mLineLayer);

        //导航分析
        try {
            mNaviAnalyser = FMNaviAnalyser.getFMNaviAnalyserById(DEFAULT_MAP_ID);
        } catch (FileNotFoundException | FMObjectException e) {
            e.printStackTrace();
        }

        //10s更新一次ap并且进行一些操作
        // TODO: 2023/4/12 *****这一块再看看*****
        timer = new Timer();
        // (2) 使用handler处理接收到的消息
        mHandler = new Handler(){
            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(Message msg) {
                if(msg.what == 0){
                    if(mLocationMarkerRealTimeCoord == null){
                        scan_to_find_all_FTM_capable_aps();//扫描当前活跃AP
                        updateActiveAPMarker();//更新活跃AP位置
                        ranging_request_to_all_FTM_capable_aps(); //更新定位STA点
                    }
                    if(mLocationMarkerLastTimeCoord == null && mLocationMarkerRealTimeCoord!=null){ //初始化
                        mLocationMarkerLastTimeCoord = mLocationMarkerRealTimeCoord;
                    }
                    if(mLocationMarkerLastTimeCoord!=null &&
                            mLocationMarkerRealTimeCoord!=null &&
                            offset_bettween(mLocationMarkerLastTimeCoord,mLocationMarkerRealTimeCoord)>max_allowed_offset) {
                        Toast.makeText(getApplicationContext(),"检测到较大位移",Toast.LENGTH_SHORT).show();
                        scan_to_find_all_FTM_capable_aps();//扫描当前活跃AP
                        updateActiveAPMarker();//更新活跃AP位置
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

        launchGaoDeLocation();
        isMapLoaded = true;

    }

    private double offset_bettween(FMGeoCoord c1, FMGeoCoord c2){
        double[] x = {c1.getCoord().x, c1.getCoord().y, c1.getCoord().z};
        double[] y = {c2.getCoord().x, c2.getCoord().y, c2.getCoord().z};
        return LocationAlgorithm.euclid_distance(x,y);
    }

    //高德地图sdk
    public void launchGaoDeLocation(){
        mLocationListener = new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation aMapLocation) {
                if (aMapLocation != null){
                    if (aMapLocation.getErrorCode() == 0){
                        Log.d(TAG, "高德 onLocationChanged: "+aMapLocation.toString());
                        //将高德的位置转化为fmmap的位置
                        FMMapCoord coord = FMCoordTransformer.wgs2WebMercator(aMapLocation.getLongitude(),aMapLocation.getLatitude());
                        Log.d(TAG, "高德定位 FMMapCoord: "+coord);
                        if(walking_routes.size()>0) { //这里主要同步方向角
                            //更新定位点在地图上的位置,如果不在导航过程中就更新实时位置
                            if(!isInNavigationProcess){
                                updateLocationMarker();
                            }else{//如果在导航中就暂时移除定位点标记
                                removeLocationMarker();
                            }
                        }
                        Log.d(TAG, "高德定位方向角度: "+aMapLocation.getBearing()+" and "+aMapLocation.getBearingAccuracyDegrees());
                        mLocationMarkerAngle = -aMapLocation.getBearing();
                        /*Log.d(TAG, "onLocationChanged: getLocationDetail() "+aMapLocation.getLocationDetail());
                        Log.d(TAG, "onLocationChanged: getLocationQualityReport() "+ aMapLocation.getLocationQualityReport());
                        Log.d(TAG, "onLocationChanged: getConScenario() "+aMapLocation.getConScenario());
                        Log.d(TAG, "onLocationChanged: getDescription() "+aMapLocation.getDescription());
                        Log.d(TAG, "onLocationChanged: getLocationType() "+aMapLocation.getLocationType());
                        Log.d(TAG, "onLocationChanged: getSpeed() "+aMapLocation.getSpeed());*/
                        mLocationMarkerSpeed = aMapLocation.getSpeed();
                        /*Log.d(TAG, "onLocationChanged: getTrustedLevel() "+aMapLocation.getTrustedLevel());
                        Log.d(TAG, "onLocationChanged: getBuildingId() and getFloor() "+aMapLocation.getBuildingId()+" and "+aMapLocation.getFloor());
                        Log.d(TAG, "onLocationChanged: getPoiName() and getProvider() "+aMapLocation.getPoiName()+" and "+aMapLocation.getProvider());
                        Log.d(TAG, "onLocationChanged: getSatellites() "+aMapLocation.getSatellites());
                        Log.d(TAG, "onLocationChanged: getGpsAccuracyStatus() "+aMapLocation.getGpsAccuracyStatus());
*/

                    }
                }
            }



        };


        //初始化定位
        try {
            mLocationClient = new AMapLocationClient(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        //设置定位回调监听
        mLocationClient.setLocationListener(mLocationListener);
        //初始化AMapLocationClientOption对象
        mLocationOption = new AMapLocationClientOption();
        //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置定位间隔,单位毫秒,默认为2000ms，最低1000ms。
        mLocationOption.setInterval(1000);
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption.setNeedAddress(true);
        //设置是否启用缓存策略
        mLocationOption.setLocationCacheEnable(false);
        Log.i(TAG, "launchGaoDeLocation:mLocationOption.isLocationCacheEnable() "+mLocationOption.isLocationCacheEnable());
        //设置是否返回方向角度、速度等传感器信息，启用传感器返回速度
        mLocationOption.setSensorEnable(true);
        //给定位客户端对象设置定位参数
        mLocationClient.setLocationOption(mLocationOption);
        //启动定位
        mLocationClient.startLocation();
    }

    @Override
    public void onMapInitFailure(String s, int i) {
        Log.e(TAG, "onMapInitFailure: Error.");
    }

    @Override
    public boolean onUpgrade(FMMapUpgradeInfo fmMapUpgradeInfo) {
        return false;
    }


    //点击覆盖物 fmNode为标注物
    @Override
    public boolean onClick(FMNode fmNode) {
        FMImageMarker imageMarker = (FMImageMarker) fmNode;
        Bundle ap_info_bundle = imageMarker.getBundle();
        if(ap_info_bundle != null){
            //createAPImageMarker中put过
            ApInfo apInfo = (ApInfo) ap_info_bundle.getSerializable("ap_info");
            //String ap_info = "SSID:WILD\nBSSID:a8:52:34:4f:3d:12\nLongitude:12949361.8178\nLatitude:4865287.2138";
            if(apInfo != null)
                showInfoWindow(imageMarker,apInfo.toString());
        }

        return true;
    }

    /**
     * 显示AP信息框
     */
    private void showInfoWindow(FMImageMarker imageMarker,String ap_info) {
        if (mInfoWindow == null) {
            mInfoWindow = new FMNodeInfoWindow(mapView, R.layout.layout_info_window);
            TextView map_info = (TextView)findViewById(R.id.ap_info);
            map_info.setText(ap_info);
            mInfoWindow.setPosition(mFMMap.getFocusGroupId(), imageMarker.getPosition());

            //关闭信息框
            mInfoWindow.getView().findViewById(R.id.ap_info).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mInfoWindow.close();
                }
            });
        }

        if (mInfoWindow.isOpened()) {
            mInfoWindow.close();
        } else {
            mInfoWindow.setPosition(mFMMap.getFocusGroupId(), imageMarker.getPosition());//设置位置
            TextView map_info = (TextView)findViewById(R.id.ap_info);
            map_info.setText(ap_info);
            Log.d(TAG, "showInfoWindow: "+ap_info);
            mInfoWindow.openOnTarget(imageMarker);
        }
        mFMMap.updateMap();
    }

    @Override
    public boolean onLongPress(FMNode fmNode) {
        return false;
    }

    @Override
    public void onCheckStateChanged(View view, boolean isChecked) {
        switch (view.getId()){
            case R.id.btn_3d:{
                //设置2d 3d效果
                setViewMode();
            }
            break;
            case R.id.btn_view: {
                //设置第一人称和第三人称
                setViewState(isChecked);
            }
            break;
            case R.id.btn_locate: {
                //设置是否跟随
                setFollowState(isChecked);
            }
            break;
            default:
                break;
        }
    }

    /**
     * 设置地图2、3D效果
     */
    private void setViewMode() {
        if (mFMMap.getCurrentFMViewMode() == FMViewMode.FMVIEW_MODE_2D) {
            mFMMap.setFMViewMode(FMViewMode.FMVIEW_MODE_3D);
        } else {
            mFMMap.setFMViewMode(FMViewMode.FMVIEW_MODE_2D);
        }
    }

    /**
     * 设置是否为第一人称
     *
     * @param enable true 第一人称
     *               false 第三人称
     */
    private void setViewState(boolean enable) {
        this.mIsFirstView = !enable;
        setFloorControlEnable();
    }


    /**
     * 设置楼层控件是否可用
     */
    private void setFloorControlEnable() {
        if (getFloorControlEnable()) {
            mSwitchFloorComponent.close();
            mSwitchFloorComponent.setEnabled(false);
        } else {
            mSwitchFloorComponent.setEnabled(true);
        }
    }

    /**
     * 楼层控件是否可以使用。
     */
    private boolean getFloorControlEnable() {
        return mHasFollowed || mIsFirstView;
    }

    /**
     * 设置跟随状态
     *
     * @param enable true 跟随
     *               false 不跟随
     */
    private void setFollowState(boolean enable) {
        mHasFollowed = enable;
        setFloorControlEnable();
    }



    @Override
    public void onBackPressed(){
        if (mFMMap != null) {
            mFMMap.onDestroy();
            mFMMap = null;
        }
        /*
        if (mTts != null) {
            mTts.destroy();
        }
        if(mNavigation!=null){
            mNavigation.stop();
            mNavigation.clear();
            mNavigation.release();
        }*/

        // 清除定位点集合
        walking_routes.clear();
        walking_geo_routes.clear();

        //停止高德定位
        mLocationClient.stopLocation();

        super.onBackPressed();
        //this.finish();
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
                                        Log.d(TAG, "onRangingResults: " + results);

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