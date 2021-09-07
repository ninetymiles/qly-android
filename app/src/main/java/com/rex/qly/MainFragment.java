package com.rex.qly;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rex.qly.network.NetworkAddressDiscover;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class MainFragment extends Fragment {

    private static final Logger sLogger = LoggerFactory.getLogger(MainFragment.class);

    private static final int REQUEST_PERMISSIONS = 100;

    private AppServiceClient mServiceClient;
    private NetworkAddressDiscover mNetworkDiscover;
    private MainAddrAdapter mAddrAdapter;
    private List<InetAddress> mAddrList = new ArrayList<>();

    public enum State { STOPPED, STOPPING, STARTED, STARTING }
    private State mState = State.STOPPED;

    private ImageView mImageStatus;
    private Button mButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        sLogger.trace("");
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sLogger.trace("");

        mImageStatus = view.findViewById(R.id.main_image_status);

        mAddrAdapter = new MainAddrAdapter(R.layout.fragment_main_addr_item);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.main_addr_recycler);
        recyclerView.setAdapter(mAddrAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mButton = view.findViewById(R.id.main_button);
        mButton.setOnClickListener(mOnClickListener);
        invalidateUIState();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sLogger.trace("");
        mNetworkDiscover = NetworkAddressDiscover.getInstance();
        mNetworkDiscover.start();
        mServiceClient = new AppServiceClient(getContext());
        mServiceClient.connect(new AppService.ServerListener() {
            @Override // AppService.ServerListener
            public void onServerState(AppService.State state, int error) {
                sLogger.trace("state:{} error:{}", state, error);
                switch (state) {
                case STARTING: postState(State.STARTING); break;
                case STARTED:  postState(State.STARTED);  break;
                case STOPPING: postState(State.STOPPING); break;
                case STOPPED:  postState(State.STOPPED);  break;
                }
            }
        });

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            sLogger.debug("Permission <{}> requesting", Manifest.permission.WRITE_EXTERNAL_STORAGE);
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sLogger.trace("");
        mServiceClient.disconnect();
        mNetworkDiscover.stop();
        mNetworkDiscover = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        sLogger.trace("");
        mNetworkDiscover.addObserver(mNetworkObserver);
    }

    @Override
    public void onStop() {
        super.onStop();
        sLogger.trace("");
        mNetworkDiscover.deleteObserver(mNetworkObserver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        sLogger.trace("requestCode:{} permissions:{} results:{}", requestCode, permissions, results);

        if (REQUEST_PERMISSIONS == requestCode) {
            for (int i = 0; i < permissions.length; i++) {
                sLogger.debug("Permission <{}> {}", permissions[i], PackageManager.PERMISSION_GRANTED == results[i] ? "GRANTED" : "DENIED");
//                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])
//                        && PackageManager.PERMISSION_GRANTED == results[i]) {
//                }
            }
        }
    }

    private void postState(State newState) {
        sLogger.trace("newState:{}", newState);
        mState = newState;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidateUIState();
            }
        });
    }

    @UiThread
    private void invalidateUIState() {
        switch (mState) {
        case STARTED:
            mButton.setEnabled(true);
            mButton.setText(R.string.button_stop);
            mImageStatus.setImageResource(R.drawable.ic_success_24dp);
            break;
        case STARTING:
        case STOPPING:
            mButton.setEnabled(false);
            mImageStatus.setImageResource(R.drawable.ic_process_24dp);
            break;
        case STOPPED:
            mButton.setEnabled(true);
            mButton.setText(R.string.button_start);
            mImageStatus.setImageResource(R.drawable.ic_failed_24dp);
            break;
        }
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            sLogger.trace("mState:{}", mState);
            switch (mState) {
            case STARTED:
                mServiceClient.stop();
                break;
            case STOPPED:
                mServiceClient.start();
                break;
            }
        }
    };

    private void updateNetInfo() {
        sLogger.trace("");
        mAddrAdapter.clear(mAddrList.size() == 0);
        for (InetAddress addr : mAddrList) {
            sLogger.trace("addr:{}", addr.getHostAddress());
            MainAddrAdapter.ViewModel model = new MainAddrAdapter.ViewModel();
            model.addr = addr;
            model.type = ConnectivityManager.TYPE_ETHERNET;
            model.extra = null;
            mAddrAdapter.add(model);
        }
    }

    private final Observer mNetworkObserver = new Observer() {
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

    private class AppServiceClient implements ServiceConnection {
        private final Context mContext;
        private AppService mAppService;
        private AppService.ServerListener mListener;
        private boolean mPendingStart;
        private boolean mPendingStop;
        public AppServiceClient(Context ctx) {
            sLogger.trace("");
            mContext = ctx;
        }
        public void connect(AppService.ServerListener listener) {
            sLogger.trace("");
            mListener = listener;
            mContext.bindService(new Intent(mContext, AppService.class), this, Context.BIND_AUTO_CREATE);
        }
        public void disconnect() {
            sLogger.trace("");
            mContext.unbindService(this);
            if (mAppService != null && mListener != null) {
                mAppService.removeServerListener(mListener);
                mAppService = null;
                mListener = null;
            }
            try {
                mContext.unbindService(this);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            }
        }

        public void start() {
            sLogger.trace("");
            mPendingStart = true;
            if (mAppService != null) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean(getString(R.string.prefs_rtmp_key), false)) {
                    mAppService.setRtmpEnable(true);
                    mAppService.setRtmpServerAddress(prefs.getString(getString(R.string.prefs_rtmp_server_key), BuildConfig.DEFAULT_RTMP_SERVER_ADDRESS));
                } else {
                    mAppService.setRtmpEnable(false);
                }
                mAppService.doStartServer();
                mPendingStart = false;
            }
        }
        public void stop() {
            sLogger.trace("");
            mPendingStop = true;
            if (mAppService != null) {
                mAppService.doStopServer();
                mPendingStop = false;
            }
        }

        @Override // ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            sLogger.trace("");
            mAppService = ((AppService.LocalBinder) service).getService();
            if (mListener != null) mAppService.addServerListener(mListener);
            if (mPendingStart) start();
            if (mPendingStop) stop();
        }
        @Override // ServiceConnection
        public void onServiceDisconnected(ComponentName name) {
            sLogger.trace("");
            mAppService = null;
        }
    }
}
