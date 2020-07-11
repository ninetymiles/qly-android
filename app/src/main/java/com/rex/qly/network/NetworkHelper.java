package com.rex.qly.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;


public class NetworkHelper {

    private static final Logger mLogger = LoggerFactory.getLogger(NetworkHelper.class);

    public static final String INVALID_HWADDR = "02:00:00:00:00:00";

    public static class NetInfo {
        public InetAddress inetAddr;
        public byte[] macAddr;
        public String name;
        public NetInfo(InetAddress inetAddr, byte[] macAddr, String name) {
            this.inetAddr = inetAddr;
            this.macAddr = macAddr;
            this.name = name;
        }
    }

    public static String getWifiHWAddrStr(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi.getConnectionInfo();
        String HWaddr = INVALID_HWADDR;
        try {
            HWaddr = info.getMacAddress().toUpperCase(Locale.US);
            //mLogger.trace("HWaddr:" + HWaddr);
        } catch (NullPointerException ex) {
        }
        return HWaddr;
    }

    public static byte[] getWifiHWAddr(Context context) {
        String addressString = getWifiHWAddrStr(context);
        if (addressString == null) {
            return null;
        }
        return hexStringToBytes(addressString.replaceAll("[:]", ""));
    }

    public static int getWifiAddress(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        try {
            return wifi.getConnectionInfo().getIpAddress();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    public static String getWifiSSID(Context context) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        try {
            return wifi.getConnectionInfo().getSSID();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return "";
    }

    @SuppressWarnings("NewApi")
    public static NetInfo getActivateNetwork(Context context) {
//        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//        NetworkInfo info = connMgr.getActiveNetworkInfo();
//        mLogger.trace("active info:" + ((info != null) ? info.toString() : "NULL"));

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        if (wifiInfo != null && wifiInfo.getIpAddress() != 0) {
            InetAddress inetAddr = intToInetAddress(wifiInfo.getIpAddress());
            byte[] macAddr = hexStringToBytes(wifiInfo.getMacAddress().toUpperCase(Locale.US).replaceAll("[:]", ""));
            //mLogger.trace("Got wifi address:" + inetAddr.getHostAddress());
            return new NetInfo(inetAddr, macAddr, null);
        }

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return new NetInfo(inetAddress, intf.getHardwareAddress(), intf.getName());
                    }
                }
            }
        } catch (SocketException ex) {
            mLogger.error("Failed to get IP address", ex);
        }
        return null;
    }

    public static List<NetInfo> getActiveAddrList() {
        List<NetInfo> result = new ArrayList<NetInfo>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        result.add(new NetInfo(inetAddress, intf.getHardwareAddress(), intf.getName()));
                    }
                }
            }
        } catch (SocketException ex) {
            mLogger.error("Failed to get IP address", ex);
        }
        return result;
    }

    public static void dumpNetwork(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] infoArr = connMgr.getAllNetworkInfo();
        if (infoArr != null) {
            for (NetworkInfo info : infoArr) {
                mLogger.info("info:" + info.toString());
            }
        }

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();

                mLogger.info("itf name:" + intf.getName() +
                        " displayName:" + intf.getDisplayName() +
                        " mac:<" + inetHWAddrString(intf.getHardwareAddress()) + ">" +
                        " virtual:" + intf.isVirtual() +
                        " loopback:" + intf.isLoopback() +
                        " p2p:" + intf.isPointToPoint() +
                        " up:" + intf.isUp() +
                        " mtu:" + intf.getMTU() +
                        " multicast:" + intf.supportsMulticast());
            }
        } catch (SocketException ex) {
            mLogger.error("Failed to get interface", ex);
        }
    }

    // Ref hidden android.net.NetworkUtils
    public static InetAddress intToInetAddress(int hostAddress) {
        byte[] addressBytes = { (byte)(0xff &  hostAddress),
                                (byte)(0xff & (hostAddress >> 8)),
                                (byte)(0xff & (hostAddress >> 16)),
                                (byte)(0xff & (hostAddress >> 24)) };

        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }

    // Ref hidden android.net.NetworkUtils
    public static int inetAddressToInt(Inet4Address inetAddr) throws IllegalArgumentException {
        byte [] addr = inetAddr.getAddress();
        return  ((addr[3] & 0xff) << 24) |
                ((addr[2] & 0xff) << 16) |
                ((addr[1] & 0xff) << 8)  |
                 (addr[0] & 0xff);
    }

    // Convert "0123" to { 0x01, 0x23 }
    public static byte[] hexStringToBytes(String hexString) {
        if (TextUtils.isEmpty(hexString)) {
            return null;
        }
        hexString = hexString.toUpperCase(Locale.US);
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    // Convert 'F' to 0xF, accept upper case only
    protected static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    /*
     * IP address should stored in reverse order
     * Convert "1.2.3.4" to 0x04030201
     */
    public static long inetStrToLong(String addr) {
        long result = 0;
        try {
            String[] addrArray = addr.split("\\.");
            for (int i = addrArray.length - 1; i >= 0; i--) {
                int num = Integer.parseInt(addrArray[i]);
                result = (result << 8) + num;
            }
        } catch (Exception ex) {
            mLogger.error("Parse IP address failed", ex);
        }
        return result;
    }

    /*
     * IP address stored in reverse order
     * Convert 0x04030201 to "1.2.3.4"
     */
    public static String inetIntToStr(int addr) {
        return  ((addr >> 0) & 0xFF) + "." +
                ((addr >> 8) & 0xFF) + "." +
                ((addr >> 16) & 0xFF) + "." +
                ((addr >> 24) & 0xFF);
    }

    /**
     * Convert byte array to string in format AA:BB:CC:DD:EE:FF
     * @param addrs Mac address in bytes
     * @return String in format AA:BB:CC:DD:EE::FF
     */
    public static String inetHWAddrString(byte[] addrs) {
        StringBuffer HWaddr = new StringBuffer();
        try {
            for (byte b : addrs) {
                HWaddr.append(String.format(Locale.US, "%02X:", b));
            }
            HWaddr.setCharAt(HWaddr.length() - 1, ' ');
        } catch (Exception ex) {
            return INVALID_HWADDR;
        }
        return HWaddr.toString().trim();
    }

    /*
     * Use by STRD tracking
     * 0: Unknown
     * 1: Ethernet
     * 2: WiFi
     * 3: 3G
     */
    public static String getNetworkType(Context context) {
        String NICType = "0"; // Unknown
        ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = conMan.getActiveNetworkInfo();
        if (info != null) {
            switch (info.getType()) {
            case ConnectivityManager.TYPE_ETHERNET:
                NICType = "1"; // LAN network
                break;
            case ConnectivityManager.TYPE_WIFI:
                NICType = "2"; // WLAN network
                break;
            case ConnectivityManager.TYPE_MOBILE:
            case ConnectivityManager.TYPE_MOBILE_DUN:
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
            case ConnectivityManager.TYPE_MOBILE_MMS:
            case ConnectivityManager.TYPE_MOBILE_SUPL:
            case ConnectivityManager.TYPE_WIMAX:
                NICType = "3"; // Mobile network, 2G/3G/4G
                break;
            case ConnectivityManager.TYPE_BLUETOOTH:
            case ConnectivityManager.TYPE_DUMMY:
            default:
                // Undefined
                break;
            }
        }
        return NICType;
    }
}
