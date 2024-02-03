package com.example.wifirttposition.widgets;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.wifirttposition.R;
import com.example.wifirttposition.utils.LogToFile;

/**
 * 作者　: mmh
 * 时间　: 2023/11/11
 * 描述　: 在收集数据时，通过弹窗将log信息放入指定的log文件中
 *        通过确定开始将log写入文件，取消则停止
 */
public class DialogCollectData extends Dialog {
    private static final String TAG = "DialogCollectData";


    // 标题
    private TextView titleTv;
    // 输入框
    private EditText fileNameEditText;

     // 按钮之间的分割线
    private View columnLineView ;

    public boolean collectingData = false;

    private Button confirmButton, cancelButton;

    /*
    * 下面是是一些数据内容
    */
    public String logName;

    public DialogCollectData(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_data_layout);
        titleTv = (TextView) findViewById(R.id.dialog_title);
        columnLineView = findViewById(R.id.dialog_line);
        fileNameEditText = (EditText) findViewById(R.id.et_input);
        confirmButton = (Button) findViewById(R.id.dialog_confirm);
        cancelButton = (Button) findViewById(R.id.dialog_cancel);

        // 设置两个监听
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!collectingData){
                    // 开始收集数据
                    startCollecting();
                } else {
                    // 停止收集数据
                    stopCollecting();
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (collectingData){
                    stopCollecting();
                }
            }
        });

    }

    private void startCollecting(){
        collectingData = true;
        confirmButton.setText("Collecting...");
        cancelButton.setText("End");
        logName = fileNameEditText.getText().toString();// 获取保存的文件名
        // 写入文件
        // TODO: 2023/11/11 写入文件
//        LogToFile.i(TAG, "text", logName);
//        Log.i(TAG, "startCollecting: text");
    }

    private void stopCollecting() {
        collectingData = false;
        cancelButton.setText("取消");
        confirmButton.setText("确定");
    }

}
