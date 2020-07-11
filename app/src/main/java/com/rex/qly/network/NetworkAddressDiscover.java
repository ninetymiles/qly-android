package com.rex.qly.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class NetworkAddressDiscover extends Observable {

    private static final Logger mLogger = LoggerFactory.getLogger(NetworkAddressDiscover.class);

    private static final int INTERVAL = 15; // In seconds

    private ScheduledExecutorService mExecutor;

    private boolean mIsStarted;
    private List<InetAddress> mAddrList = new ArrayList<InetAddress>();

    private static NetworkAddressDiscover mInstance;

    private NetworkAddressDiscover() {
    }

    public static synchronized NetworkAddressDiscover getInstance() {
        if (mInstance == null) {
            mInstance = new NetworkAddressDiscover();
        }
        return mInstance;
    }

    public void start() {
        if (mIsStarted) {
            mLogger.warn("Already started");
            return;
        }

        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mExecutor.scheduleWithFixedDelay(mRunnable, 0, INTERVAL, TimeUnit.SECONDS);
        mIsStarted = true;
    }

    public synchronized void stop() {
        if (! mIsStarted) {
            mLogger.warn("Already stopped");
            return;
        }

        mExecutor.shutdown();
        mIsStarted = false;
    }

    public void invoke() {
        if (mExecutor != null && mIsStarted) {
            mExecutor.schedule(mRunnable, 0, TimeUnit.SECONDS);
        }
    }

    @Override
    public void addObserver(Observer observer) {
        super.addObserver(observer);
        observer.update(this, mAddrList);
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            //mLogger.trace("+");
            List<InetAddress> result = new ArrayList<>();
            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress addr = enumIpAddr.nextElement();
                        if (addr.isLinkLocalAddress()) continue; // Skip fe80::%dummy0 and fe80::%wlan0 auto-address configuration, neighbor discovery or when no routers are present
                        if (addr.isLoopbackAddress()) continue; // Skip 127.0.0.1 and ::1
                        //mLogger.trace("hostAddress {}", addr.getHostAddress());
                        result.add(addr);
                    }
                }
            } catch (SocketException ex) {
                mLogger.error("Failed to get Interface or Address", ex);
            }

            try {
                if (!result.containsAll(mAddrList)) setChanged();
                if (!mAddrList.containsAll(result)) setChanged();
                mAddrList = result;
                notifyObservers(mAddrList);
            } catch (Exception ex) {
                mLogger.warn("Failed to notify - {}", ex.getMessage());
            }
            //mLogger.trace("-");
        }
    };
}
