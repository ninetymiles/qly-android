package com.rex.qly;

import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Network address recycler view adapter, showing network address in list
 */
public class MainAddrAdapter extends RecyclerView.Adapter<MainAddrAdapter.ViewHolder> {

    private List<ViewModel> items;
    private int itemResId;

    public MainAddrAdapter(int resId) {
        this(resId, null);
    }

    public MainAddrAdapter(int resId, List<ViewModel> items) {
        this.itemResId = resId;
        this.items = items;
        if (this.items == null) {
            this.items = new ArrayList<ViewModel>();
        }
    }

    @Override
    public MainAddrAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(itemResId, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(MainAddrAdapter.ViewHolder holder, int position) {
        ViewModel item = items.get(position);

        int iconResId = R.drawable.ic_laptop_black_24dp;
        if (item.type == ConnectivityManager.TYPE_WIFI) {
            iconResId = R.drawable.ic_wifi_black_24dp;
        }
        holder.icon.setImageResource(iconResId);

        String textAddr = (item.addr != null) ? item.addr.getHostAddress() : null;
        if (! TextUtils.isEmpty(item.extra)) {
            textAddr = String.format(Locale.US, "%s (%s)", textAddr, item.extra.replace("\"", "").trim());
        }
        holder.text.setText(textAddr);

        holder.itemView.setTag(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void add(ViewModel item) {
        items.add(item);
        Collections.sort(items);
        notifyDataSetChanged();
    }

    public void remove(ViewModel item) {
        items.remove(item);
        Collections.sort(items);
        notifyDataSetChanged();
    }

    public void clear(boolean notifyChange) {
        items.clear();
        if (notifyChange) notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ImageView icon;
        public TextView text;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.main_addr_icon);
            text = (TextView) itemView.findViewById(R.id.main_addr_text);
        }
    }

    public static class ViewModel implements Comparable<ViewModel> {

        public int type;
        public InetAddress addr;
        public String extra;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("<@0x");
            builder.append(Integer.toHexString(hashCode()));
            builder.append(" type:" + type);
            builder.append(" addr:<" + addr.getHostAddress() + ">");
            builder.append(" extra:<" + extra + ">");
            return builder.toString();
        }

        @Override
        public int compareTo(@NonNull ViewModel target) {
            int result = target.type - type;
            if (result == 0) result = target.addr.getHostAddress().compareTo(addr.getHostAddress());
            return result;
        }
    }
}
