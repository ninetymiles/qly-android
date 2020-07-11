package com.rex.qly;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rex.qly.network.NetworkAddressDiscover;
import com.rex.qly.network.NetworkHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class MainFragment extends Fragment {

    private static final Logger sLogger = LoggerFactory.getLogger(MainFragment.class);

    private NetworkAddressDiscover mNetworkDiscover;
    private MainAddrAdapter mAddrAdapter;
    private List<InetAddress> mAddrList = new ArrayList<InetAddress>();

    public enum State { STOPPED, STOPPING, STARTED, STARTING }
    private State mState = State.STOPPED;

    private Button mButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        sLogger.trace("");
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sLogger.trace("");

        mAddrAdapter = new MainAddrAdapter(R.layout.fragment_main_addr_item);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.main_addr_recycler);
        recyclerView.setAdapter(mAddrAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mButton = view.findViewById(R.id.main_button);
        mButton.setOnClickListener(mOnClickListener);
        invalidateButton();
    }

    @Override
    public void onStart() {
        super.onStart();
        sLogger.trace("");
        mNetworkDiscover = NetworkAddressDiscover.getInstance();
        mNetworkDiscover.addObserver(mNetworkObserver);
        mNetworkDiscover.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        sLogger.trace("");
        mNetworkDiscover.deleteObserver(mNetworkObserver);
    }

    private void postState(State newState) {
        sLogger.trace("newState:{}", newState);
        mState = newState;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidateButton();
            }
        });
    }

    @UiThread
    private void invalidateButton() {
        switch (mState) {
        case STARTED:
            mButton.setEnabled(true);
            mButton.setText(R.string.button_stop);
            break;
        case STARTING:
            mButton.setEnabled(false);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    postState(State.STARTED);
                }
            }, 100);
            break;
        case STOPPED:
            mButton.setEnabled(true);
            mButton.setText(R.string.button_start);
            break;
        case STOPPING:
            mButton.setEnabled(false);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    postState(State.STOPPED);
                }
            }, 100);
            break;
        }
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            sLogger.trace("mState:{}", mState);
            switch (mState) {
            case STARTED:
                postState(State.STOPPING);
                break;
            case STOPPED:
                postState(State.STARTING);
                break;
            }
        }
    };


    private void updateNetInfo() {
        sLogger.trace("");

        int wifiIpAddress = 0;
        String wifiSSID = "";
        WifiManager wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        if (wifiInfo != null) {
            wifiIpAddress = wifiInfo.getIpAddress();
            wifiSSID = wifiInfo.getSSID();
        }

        mAddrAdapter.clear(mAddrList.size() == 0);
        for (InetAddress addr : mAddrList) {
            sLogger.trace("addr:{}", addr.getHostAddress());
            MainAddrAdapter.ViewModel model = new MainAddrAdapter.ViewModel();
            model.addr = addr;
            model.type = ConnectivityManager.TYPE_ETHERNET;
            model.extra = null;
            if (addr instanceof Inet4Address) { // Current can only get IPv4 address from WifiManager
                if (wifiIpAddress == NetworkHelper.inetAddressToInt((Inet4Address) addr)) {
                    model.type = ConnectivityManager.TYPE_WIFI;
                    model.extra = wifiSSID;
                }
            }
            mAddrAdapter.add(model);
        }
    }

    private Observer mNetworkObserver = new Observer() {
        @Override
        public void update(Observable observable, Object data) {
            sLogger.trace("");
            mAddrList = (List<InetAddress>) data;

            // Can not update the RecyclerAdapter synchronized, avoid dead lock
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateNetInfo();
                }
            });
        }
    };
}
