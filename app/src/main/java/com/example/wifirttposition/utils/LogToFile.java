package com.example.wifirttposition.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.icu.util.Calendar;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.Settings.Global;
import android.util.Log;

import org.apache.commons.math3.complex.ComplexFormat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 作者　: mmh
 * 时间　: 2023/11/4
 * 描述　: 将log写入手机文件中
 */
public class LogToFile  {


    public static String TAG = "LogToFile";

    public static String logPath = null; // 用于保存log文件位置
    public static String savePath = null;
//    private static String savePath = Environment.getExternalStorageDirectory() + "/wifiLog/"; // 被弃用

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Object lock = new Object();
    public static void init(Context context){
        savePath = context.getExternalFilesDir("wifiLog").getAbsolutePath() ;
        logPath = getFilePath(context) + "/Log"; // 加入子文件夹
    }
    private static final char VERBOSE = 'v';

    private static final char DEBUG = 'd';

    private static final char INFO = 'i';

    private static final char WARN = 'w';

    private static final char ERROR = 'e';

    public static void v(String tag, String msg, String fileName) {
        writeToLogFile(VERBOSE, tag, msg, fileName);
    }
    public static void d(String tag, String msg, String fileName) {
        writeToLogFile(DEBUG, tag, msg, fileName);
    }

    public static void i(String tag, String msg,String fileName) {
        writeToLogFile(INFO, tag, msg, fileName);
    }

    public static void w(String tag, String msg, String fileName) {
        writeToLogFile(WARN, tag, msg, fileName);
    }

    public static void e(String tag, String msg, String fileName) {
        writeToLogFile(ERROR, tag, msg, fileName);
    }

    private static void writeToLogFile(char type, String tag, String msg, String fileName){
        executorService.submit(() -> {
            writeToFile(type, tag, msg, fileName);
        });
    }

    
    /**
     * @description:    文件写入
     * @param:          char type, string TAG, string msg
     * @return:
     */
    private static void writeToFile(char type, String tag, String msg, String mfileName){
        if (null == logPath) {
            Log.e(TAG, "logPath == null ，未初始化LogToFile");
            return;
        }
        String fileName = logPath + "/log_" + mfileName + ".log";//log日志名，使用时间命名，保证不重复
        String log = getTimeString() + " " + type + " " + tag + " " + msg + "\n";//log日志内容，可以自行定制

        // TODO: 2023/11/11 new Thread
        
        //如果父路径不存在
        File file = new File(logPath);
        if (!file.exists()) {
            file.mkdirs();//创建父路径
        }

        synchronized (lock){
            FileOutputStream fos = null;//FileOutputStream会自动调用底层的close()方法，不用关闭
            BufferedWriter bw = null;
            try {

                fos = new FileOutputStream(fileName, true);//这里的第二个参数代表追加还是覆盖，true为追加，false为覆盖
                bw = new BufferedWriter(new OutputStreamWriter(fos));
                bw.write(log);
                Log.i(TAG, "成功写入");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (bw != null) {
                        bw.close();//关闭缓冲流
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @description:    获取文件路径
     * @param:          context
     * @return:         文件路径
     */
    private static String getFilePath(Context context){
        File dir = new File(savePath);
        if (! dir.exists()){
            dir.mkdir();
        }
        return savePath; // 存在当前的app中
    }


    @SuppressLint("SimpleDateFormat")
    private static String getTimeString(){
        SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        return dataFormat.format(calendar.getTime());
    }
    @SuppressLint("SimpleDateFormat")
    private static String getDayString(){
        SimpleDateFormat dateDayFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        return dateDayFormat.format(calendar.getTime());
    }

}
