package com.example.wifirttposition.utils;

import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wifirttposition.R;

import java.util.List;

/**
 * 作者　:
 * 时间　: 2023/2/10
 * 描述　:
 */
public class MyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    //item有两种类型，一种是表头，一种为元素
    private static final int HEADER_POSITION = 0;
    private static final int HEADER_TYPE =0;
    private static final int ITEM_TYPE = 1;

    public static OnScanResultListener mOnScanResultListener;

    private List<ScanResult> mAPWithRTTList;

    //构造器传入
    public MyAdapter(List<ScanResult> list, OnScanResultListener scanResultListener) {
        mAPWithRTTList = list;
        mOnScanResultListener = scanResultListener;
    }

    public class ViewHolderItem extends RecyclerView.ViewHolder implements View.OnClickListener{
        public TextView mSSIDTextView;
        public TextView mBSSIDTextView;
        public TextView mdbmTextView;
        public TextView ismccTextView;
        public ViewHolderItem(@NonNull View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            mSSIDTextView = itemView.findViewById(R.id.ssid_text_view);
            mBSSIDTextView = itemView.findViewById(R.id.bssid_text_view);
            mdbmTextView = itemView.findViewById(R.id.dbm_text_view);
            ismccTextView = itemView.findViewById(R.id.is_mc_text_view);
        }
        @Override
        public void onClick(View v) {
            mOnScanResultListener.onScanResultItemClick(getItem(getAdapterPosition()));
        }
    }

    public static class ViewHolderHeader extends RecyclerView.ViewHolder{

        public ViewHolderHeader(@NonNull View itemView) {
            super(itemView);
        }
    }


    public void swapData(List<ScanResult> list) {

        //清除任何更新，因为即使是空列表也意味着找不到WifiRtt设备。
        mAPWithRTTList.clear();

        if ((list != null) && (list.size() > 0)) {
            mAPWithRTTList.addAll(list);
        }

        notifyDataSetChanged();
    }

    //取扫描结果
    private ScanResult getItem(int position){
        return mAPWithRTTList.get(position - 1);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        RecyclerView.ViewHolder viewHolder;

        if (viewType == ITEM_TYPE) {
            viewHolder = new ViewHolderItem(LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row_item,parent,false));
        } else if (viewType == HEADER_TYPE){
            viewHolder = new ViewHolderHeader(LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row_header,parent,false));
        } else {
            throw new RuntimeException(viewType + " isn't a valid viewType.");
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderItem){
            ViewHolderItem viewHolderItem = (ViewHolderItem) holder;
            //扫描结果
            ScanResult currentScanResult = getItem(position);//需要去掉表头占的一个位置
            viewHolderItem.mdbmTextView.setText(String.valueOf(currentScanResult.level));
            viewHolderItem.mSSIDTextView.setText(currentScanResult.SSID);
            viewHolderItem.mBSSIDTextView.setText(currentScanResult.BSSID);
            viewHolderItem.ismccTextView.setText(currentScanResult.is80211mcResponder()?"√":"x");
        }else if (holder instanceof ViewHolderHeader){
            //不需要数据，表头
        }else {
            throw new RuntimeException(holder + " " + "isn't a valid view holder.");
        }
    }

    @Override
    public int getItemCount() {
        return mAPWithRTTList.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == HEADER_POSITION) {
            return HEADER_TYPE;
        } else {
            return ITEM_TYPE;
        }
    }
    //自定义回调事件接口
    public static interface OnScanResultListener{
        void onScanResultItemClick(ScanResult scanResult);
    }
}
