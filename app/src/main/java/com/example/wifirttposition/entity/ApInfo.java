package com.example.wifirttposition.entity;

import android.graphics.PointF;

import com.fengmap.android.map.FMMap;
import com.fengmap.android.map.geometry.FMMapCoord;

import java.io.Serializable;

/**
 * 作者　: mmh
 * 时间　: 2023/2/7
 * 描述　:
 */
public class ApInfo implements Serializable {//实现Serializable接口来进行bundle数据传输
    private String SSID;
    private String BSSID;    //MAC
    private double longitude;//经度
    private double latitude; //维度
    private double altitude; //高度
    private int floor_id; //楼层id

    public ApInfo(String SSID, String BSSID, double longitude, double latitude) {
        this.SSID = SSID;
        this.BSSID = BSSID;
        this.longitude = longitude;
        this.latitude = latitude;
    }
    public ApInfo(String SSID, String BSSID, double longitude, double latitude, double altitude) {
        this.SSID = SSID;
        this.BSSID = BSSID;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
    }

    public ApInfo(String SSID, String BSSID, double longitude, double latitude, double altitude, int floor_id) {
        this.SSID = SSID;
        this.BSSID = BSSID;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.floor_id = floor_id;
    }


    public String getSSID() {
        return SSID;
    }

    public void setSSID(String SSID) {
        this.SSID = SSID;
    }

    public String getBSSID() {
        return BSSID;
    }

    public void setBSSID(String BSSID) {
        this.BSSID = BSSID;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public int getFloor_id() {
        return floor_id;
    }

    public void setFloor_id(int floor_id) {
        this.floor_id = floor_id;
    }

    //获取地图坐标
    public FMMapCoord getFMMapCoord(){
        return new FMMapCoord(this.getLongitude(),this.getAltitude());
    }

    //设置AP位置，好像可以直接调取
    public void setPosition(double longitude, double latitude, double altitude){
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
    }

    public double[] getPosition() {
        return new double [] {this.longitude, this.latitude, this.altitude};
    }

    public PointF getPointF() {
        return new PointF((float)this.longitude, (float)this.latitude);
    }


    @Override
    public String toString() {
        return "ApInfo{" +
                "SSID='" + SSID + '\'' +
                ", BSSID='" + BSSID + '\'' +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                ", altitude=" + altitude +
                '}';
    }
}
