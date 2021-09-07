package com.rex.qly;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.Keep;

import com.rex.qly.preference.Prefs;
import com.rex.qly.record.OutputCallbackRtmp;
import com.rex.qly.record.SurfaceRecorder;
import com.rex.qly.utils.AssetsHelper;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import fi.iki.elonen.SimpleWebServer;

@Keep
public class AppService extends Service {

    private static final Logger sLogger = LoggerFactory.getLogger(AppService.class);

    private static final int MAX_NUM_IMAGES = 3;

    private static final String ACTION_SERVER_START     = "com.rex.qly.ACTION_SERVER_START";
    private static final String ACTION_SERVER_STOP      = "com.rex.qly.ACTION_SERVER_STOP";
    private static final String ACTION_SESSION_STOP     = "com.rex.qly.ACTION_SESSION_STOP";
    private static final String ACTION_PROJECTION       = "com.rex.qly.ACTION_PROJECTION";
    private static final String KEY_RESULT_CODE         = "com.rex.qly.RESULT_CODE";
    private static final String KEY_RESULT_DATA         = "com.rex.qly.RESULT_DATA";

    private static final int MSG_SERVER_START           = 1;
    private static final int MSG_SERVER_STOP            = 2;
    private static final int MSG_SESSION_START          = 3;
    private static final int MSG_SESSION_STOP           = 4;
    private static final int MSG_SESSION_MESSAGE        = 5;
    private static final int MSG_SESSION_SEND           = 6;
    private static final int MSG_DEVICE_ROTATE          = 7;

    public enum State { STARTING, STARTED, STOPPING, STOPPED }
    private State mState = State.STOPPED;

    private final AppNotifier mNotifier = new AppNotifier();

    private final ArrayList<ServerListener> mServerListeners = new ArrayList<ServerListener>();
    private volatile Handler mHandler;

    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private MediaProjection mProjection;
    private Integer mProjectionResultCode;
    private Intent mProjectionResultData;

    private VirtualDisplay mDisplay;
    private ImageReader mImageReader;
    private Surface mSurface;

    private boolean mRtmpEnabled;
    private String mRtmpServerAddress;
    private SurfaceRecorder mSurfaceRecorder;

    private SimpleWebServer mHttpServer;
    private WsServer mWsServer;

    private final LocalBinder mLocalBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AppService getService() {
            return AppService.this;
        }
    }

    public interface ServerListener {
        int ERR_NONE        = 0;
        int ERR_CANCEL      = -1;
        int ERR_DISCONNECT  = -2;
        void onServerState(State state, int error);
    }

    public interface MediaProjectionRequester {
        void onRequestProjection(Context context);
    }
    public interface MediaProjectionRequesterFactory {
        MediaProjectionRequester createRequester();
    }
    private MediaProjectionRequesterFactory mProjectionRequesterFactory = new MediaProjectionRequesterFactory() {
        @Override
        public MediaProjectionRequester createRequester() {
            sLogger.trace("");
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API-29
//                return new MediaProjectionRequestActivity.MediaProjectionRequester29();
//            }
            return new MediaProjectionRequestActivity.MediaProjectionRequester21();
        }
    };
    public void setProjectionRequesterFactory(MediaProjectionRequesterFactory factory) {
        sLogger.trace("factory:{}", factory);
        mProjectionRequesterFactory = factory;
    }

    @Override
    public void onCreate() {
        sLogger.trace("");

        HandlerThread thread = new HandlerThread("AppService");
        thread.start();
        mHandler = new AppHandler(thread.getLooper());

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Export asset file and unzip to /data/data/PACKAGE_NAME/www
        File homePath = new File(getApplicationInfo().dataDir + File.separator + "www");
        AssetsHelper helper = new AssetsHelper(getApplicationContext());
        helper.clearPath(homePath);
        try {
            File assetFile = helper.exportAssetFile("www.zip", getApplicationInfo().dataDir);
            helper.unzip(assetFile, homePath);
        } catch (IOException ex) {
            sLogger.warn("Failed to export assets\n", ex);
        }

        // Setup web server at /data/data/PACKAGE_NAME/www
        mHttpServer = new SimpleWebServer(null, 8000, homePath, false);

        // Setup websocket server
        mWsServer = new WsServer(5566, mWsServerCallback);

        mNotifier.onCreate(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_SERVER_START.equals(intent.getAction())) {
                mHandler.sendEmptyMessage(MSG_SERVER_START);
            } else if (ACTION_SERVER_STOP.equals(intent.getAction())) {
                mHandler.sendEmptyMessage(MSG_SERVER_STOP);
            } else if (ACTION_SESSION_STOP.equals(intent.getAction())) {
                mHandler.sendEmptyMessage(MSG_SESSION_STOP);
            } else if (ACTION_PROJECTION.equals(intent.getAction())) {
                mProjectionResultCode = intent.getIntExtra(KEY_RESULT_CODE, Activity.RESULT_CANCELED);
                mProjectionResultData = intent.getParcelableExtra(KEY_RESULT_DATA);
                sLogger.trace("ACTION_PROJECTION resultCode:{} resultData:{}", mProjectionResultCode, mProjectionResultData);
                if (Activity.RESULT_OK == mProjectionResultCode) {
                    doStartSession(mProjectionResultCode, mProjectionResultData);
                } else {
                    doStopSession();
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sLogger.trace("");
        mHandler.getLooper().quit();
        mNotifier.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        sLogger.trace("");

        if (mOrientation != newConfig.orientation) {
            mOrientation = newConfig.orientation;
            mHandler.obtainMessage(MSG_DEVICE_ROTATE).sendToTarget();
        }
    }

    public void addServerListener(ServerListener listener) {
        sLogger.trace("listener:{}", listener);
        synchronized (mServerListeners) {
            mServerListeners.add(listener);
            listener.onServerState(mState, ServerListener.ERR_NONE);
        }
    }

    public void removeServerListener(ServerListener listener) {
        sLogger.trace("listener:{}", listener);
        synchronized (mServerListeners) {
            mServerListeners.remove(listener);
        }
    }

    private final class AppHandler extends Handler {
        public AppHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            //sLogger.trace("what:{}", msg.what);
            switch (msg.what) {
            case MSG_SERVER_START:
                sLogger.trace("MSG_SERVER_START");
                handleServerStartCommand();
                break;
            case MSG_SERVER_STOP:
                sLogger.trace("MSG_SERVER_STOP");
                handleServerStopCommand();
                break;
            case MSG_SESSION_START:
                sLogger.trace("MSG_SESSION_START");
                handleSessionStartCommand();
                break;
            case MSG_SESSION_STOP:
                sLogger.trace("MSG_SESSION_STOP");
                handleSessionStopCommand();
                break;
            case MSG_SESSION_MESSAGE:
                sLogger.trace("MSG_SESSION_MESSAGE\n{}", msg.obj);
                handleSessionMessage((String) msg.obj);
                break;
            case MSG_SESSION_SEND:
                sLogger.trace("MSG_SESSION_SEND\n{}", msg.obj);
                mWsServer.sendMessage((String) msg.obj);
                break;
            case MSG_DEVICE_ROTATE:
                sLogger.trace("MSG_DEVICE_ROTATE");
                handleDeviceRotate();
                break;
            default:
                sLogger.warn("Unknown message:" + msg);
                super.handleMessage(msg);
            }
        }
    }

    private boolean handleServerStartCommand() {
        sLogger.trace("+ {}", mState);
        if (State.STOPPED != mState) {
            sLogger.trace("- server already started");
            return false;
        }
        setServerState(State.STARTING);

        startService(new Intent(this, AppService.class));
        mNotifier.onServerStart();

        if (! mRtmpEnabled) {
            try {
                mHttpServer.start();
            } catch (IOException ex) {
                sLogger.warn("Failed to start http server\n", ex);
            }
            try {
                mWsServer.start(60000);
            } catch (IOException ex) {
                sLogger.warn("Failed to start ws server\n", ex);
            }
        }

        setServerState(State.STARTED);

        if (mRtmpEnabled) {
            if (mProjectionResultCode == null && mProjectionResultData == null) {
                requestMediaProjection();
            } else {
                doStartSession(mProjectionResultCode, mProjectionResultData);
            }
        }

        sLogger.trace("-");
        return true;
    }

    private boolean handleServerStopCommand() {
        sLogger.trace("+ {}", mState);
        if (State.STARTED != mState && State.STARTING != mState) {
            sLogger.trace("- server not started");
            return false;
        }
        setServerState(State.STOPPING);

        stopSelf();

        if (mProjection != null) {
            doStopSession();
        }
        if (! mRtmpEnabled) {
            mHttpServer.stop();
            mWsServer.stop();
        }

        mNotifier.onServerStop();

        setServerState(State.STOPPED);
        sLogger.trace("-");
        return true;
    }

    private boolean handleSessionStartCommand() {
        sLogger.trace("+ {}", mState);

        if (State.STARTED != mState) {
            sLogger.trace("- server not started");
            return false;
        }
        if (mProjection == null) {
            sLogger.trace("- projection not ready");
            return false;
        }
        if (mDisplay != null) {
            sLogger.trace("- session in busy");
            return false;
        }

        if (mWakeLock == null) {
            sLogger.info("Acquire power lock");
            mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "qly:session");
            mWakeLock.acquire();
        }

        final Point displaySize = getDefaultDisplaySize();
        final Point preferSize = new Prefs(this).getVideoResolutionPoint();
        final Point captureSize = scaleSize(displaySize.x, displaySize.y, preferSize.x, preferSize.y);
        sLogger.debug("Display:{}x{} Prefer:{}x{} Capture:{}x{}",
                displaySize.x, displaySize.y,
                preferSize.x, preferSize.y,
                captureSize.x, captureSize.y);

        if (mRtmpEnabled) {
            mSurfaceRecorder = new SurfaceRecorder();
            mSurfaceRecorder.setOutputCallback(new OutputCallbackRtmp(mRtmpServerAddress));
            mSurfaceRecorder.setSurfaceCallback(new SurfaceRecorder.SurfaceCallback() {
                @Override
                public void onSurface(Surface surface) {
                    //mSurface = surface; // SurfaceRecorder.stop() will auto release the surface, do not need keep a reference for handleSessionStopCommand()
                    mDisplay = mProjection.createVirtualDisplay("VirtualDisplay",
                            captureSize.x, captureSize.y, 213, // TV-DPI
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            surface,
                            mVirtualDisplayCallback,
                            mHandler);
                }
            });
            mSurfaceRecorder.start(captureSize.x, captureSize.y, 30, bitRate(captureSize.x, captureSize.y));
        } else {
            mImageReader = ImageReader.newInstance(captureSize.x, captureSize.y, PixelFormat.RGBA_8888, MAX_NUM_IMAGES);
            mImageReader.setOnImageAvailableListener(mImageSendListener, mHandler);
            mSurface = mImageReader.getSurface();
            if (mDisplay == null) {
                mDisplay = mProjection.createVirtualDisplay("VirtualDisplay",
                        captureSize.x, captureSize.y, 213, // TV-DPI
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mSurface,
                        mVirtualDisplayCallback,
                        mHandler);
            }
        }

        mNotifier.onSessionStart();

        sLogger.trace("-");
        return true;
    }

    // May be after server stopped, so do not check current state
    private boolean handleSessionStopCommand() {
        sLogger.trace("+ {}", mState);

        if (mWakeLock != null) {
            sLogger.info("Release power lock");
            mWakeLock.release();
            mWakeLock = null;
        }

        if (mSurfaceRecorder != null) {
            mSurfaceRecorder.stop();
            mSurfaceRecorder = null;
        }
        if (mDisplay != null) {
            mDisplay.setSurface(null);
            mDisplay.release();
            mDisplay = null;
        }
        if (mProjection != null) {
            mProjection.stop();
            mProjection = null;
        }
        if (mSurface != null) {
            mSurface.release();
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mWsServer.quitSession();
        mNotifier.onSessionStop();
        sLogger.trace("-");
        return true;
    }

    private void handleSessionMessage(String message) {
        sLogger.trace("message:<{}>", message);
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");
            switch (type) {
            case "capture":
                sLogger.trace("capture");
                if (State.STARTED.equals(mState)) {
                    if (mProjectionResultCode == null && mProjectionResultData == null) {
                        requestMediaProjection();
                    } else {
                        doStartSession(mProjectionResultCode, mProjectionResultData);
                    }
                }
                break;
            case "heartbeat":
                sLogger.trace("heartbeat");
                break;
            }
        } catch (JSONException ex) {
            sLogger.warn("Failed to parse JSON message\n", ex);
        }
    }

    private boolean handleDeviceRotate() {
        sLogger.trace("");
        if (mDisplay == null) {
            return false; // Session not started, do nothing
        }
        mDisplay.setSurface(null);

        Point displaySize = getDefaultDisplaySize();
        Point preferSize = new Prefs(this).getVideoResolutionPoint();
        Point captureSize = scaleSize(displaySize.x, displaySize.y, preferSize.x, preferSize.y);
        sLogger.debug("Display:{}x{} Prefer:{}x{} Capture:{}x{}",
                displaySize.x, displaySize.y,
                preferSize.x, preferSize.y,
                captureSize.x, captureSize.y);

        if (mRtmpEnabled) {
            mSurfaceRecorder.stop();
            mSurfaceRecorder.start(captureSize.x, captureSize.y, 30, bitRate(captureSize.x, captureSize.y));
        } else {
            mSurface.release();
            //mImageReader.close(); // FIXME: Close here will got "dequeueBuffer: BufferQueue has been abandoned"
            mImageReader = ImageReader.newInstance(captureSize.x, captureSize.y, PixelFormat.RGBA_8888, MAX_NUM_IMAGES);
            mImageReader.setOnImageAvailableListener(mImageSendListener, mHandler);
        }
        mDisplay.resize(captureSize.x, captureSize.y, 213);
        mDisplay.setSurface(mImageReader.getSurface());
        return true;
    }

    public void setRtmpEnable(boolean enabled) {
        mRtmpEnabled = enabled;
    }

    public void setRtmpServerAddress(String address) {
        mRtmpServerAddress = address;
    }

    public void doStartServer() {
        sLogger.trace("");
        mHandler.removeMessages(MSG_SERVER_START);
        mHandler.obtainMessage(MSG_SERVER_START).sendToTarget();
    }

    public void doStopServer() {
        sLogger.trace("");
        mHandler.removeMessages(MSG_SERVER_STOP);
        mHandler.obtainMessage(MSG_SERVER_STOP).sendToTarget();
    }

    private void doStartSession(int resultCode, Intent resultData) {
        sLogger.trace("resultCode:{} resultData:{}", resultCode, resultData);
        mProjection = ((MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(resultCode, resultData);
        if (mProjection != null) {
            mHandler.removeMessages(MSG_SESSION_START);
            mHandler.obtainMessage(MSG_SESSION_START).sendToTarget();
        } else {
            sLogger.error("Failed to create MediaProjection");
        }
    }

    private void doStopSession() {
        sLogger.trace("");
        mHandler.removeMessages(MSG_SESSION_STOP);
        mHandler.obtainMessage(MSG_SESSION_STOP).sendToTarget();
    }

    private Point getDefaultDisplaySize() {
        Point displaySize = new Point();
        Display display = ((DisplayManager) getSystemService(Service.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealSize(displaySize);
        return displaySize;
    }

    private Point scaleSize(int width, int height, int maxWidth, int maxHeight) {
        if (maxWidth > width && maxHeight > height) {
            // Not over the limitation, use specified resolution directly
        } else {
            // Scale down resolution has same ratio with Main Display resolution
            float srcRatio = (float) width    / (float) height;
            float dstRatio = (float) maxWidth / (float) maxHeight;
            if (dstRatio > srcRatio) {
                height = Math.min(height, maxHeight);
                width  = Math.round(height * srcRatio);
            } else {
                width  = Math.min(width, maxWidth);
                height = Math.round(width / srcRatio);
            }
        }
        return new Point(width, height);
    }

    private int bitRate(int width, int height) {
        int bitRate = 4 * 1000000; // 4M
        if (height > 720) {
            bitRate = 10 * 1000000; // 10M
        } else if (height > 1088) {
            bitRate = 50 * 1000000; // 50M
        }
        return bitRate;
    }

    // Notification will send intent to stop the server
    public static Intent createServerStopIntent(Context context) {
        return new Intent(context, AppService.class)
                .setAction(ACTION_SERVER_STOP);
    }

    public static Intent createSessionStopIntent(Context context) {
        return new Intent(context, AppService.class)
                .setAction(ACTION_SESSION_STOP);
    }

    public static Intent createProjectionIntent(Context context, int resultCode, Intent resultData) {
        return new Intent(context, AppService.class)
                .setAction(ACTION_PROJECTION)
                .putExtra(KEY_RESULT_CODE, resultCode)
                .putExtra(KEY_RESULT_DATA, resultData);
    }

    private void setServerState(State s) {
        setServerState(s, ServerListener.ERR_NONE);
    }

    private void setServerState(State s, int error) {
        mState = s;
        synchronized (mServerListeners) {
            for (ServerListener listener : mServerListeners) {
                listener.onServerState(mState, ServerListener.ERR_NONE);
            }
        }
    }

    private void requestMediaProjection() {
        sLogger.trace("factory:{}", mProjectionRequesterFactory);
        try {
            mProjectionRequesterFactory.createRequester()
                    .onRequestProjection(this);
        } catch (Exception ex) {
            sLogger.warn("Failed to send request - {}", ex.getMessage());
        }
    }

    private final ImageReader.OnImageAvailableListener mImageSendListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //sLogger.trace("send width:{} height:{}", reader.getWidth(), reader.getHeight());
            ByteArrayOutputStream bos = new ByteArrayOutputStream(32768);
            Image image = mImageReader.acquireLatestImage();
            if (image == null) {
                return;
            }
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * reader.getWidth();

            Bitmap bitmap = Bitmap.createBitmap(reader.getWidth() + rowPadding / pixelStride, reader.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos);

            mWsServer.sendFrame(bos.toByteArray());

            image.close();
            bitmap.recycle();
            try {
                bos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    };

    private final ImageReader.OnImageAvailableListener mImageExportListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //sLogger.trace("export width:{} height:{}", reader.getWidth(), reader.getHeight());
            Image image = null;
            Bitmap bitmap = null;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "sos.jpeg"));
                image = mImageReader.acquireLatestImage();
                if (image == null) {
                    return;
                }

                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();
                int pixelStride = plane.getPixelStride();
                int rowStride = plane.getRowStride();
                int rowPadding = rowStride - pixelStride * reader.getWidth();

                bitmap = Bitmap.createBitmap(reader.getWidth() + rowPadding / pixelStride, reader.getHeight(), Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, fos);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
                try {
                    if (fos != null) {
                        fos.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    };

    private final WsServer.Callback mWsServerCallback = new WsServer.Callback() {
        private final Set<WsServer.WsStreamSocket> mSockets = new LinkedHashSet<>();
        @Override
        public void onStart(WsServer.WsStreamSocket ws) {
            sLogger.trace("");
            synchronized (mSockets) {
                mSockets.add(ws);
                sLogger.info("Session:{}", mSockets.size());
            }
        }
        @Override
        public void onStop(WsServer.WsStreamSocket ws) {
            sLogger.trace("");
            synchronized (mSockets) {
                mSockets.remove(ws);
                sLogger.debug("Session:{}", mSockets.size());
                if (mSockets.size() == 0) {
                    sLogger.info("All session stopped");
                    mHandler.removeMessages(MSG_SESSION_STOP);
                    mHandler.obtainMessage(MSG_SESSION_STOP).sendToTarget();
                }
            }
        }
        @Override
        public void onMessage(WsServer.WsStreamSocket ws, String message) {
            sLogger.trace("message:<{}>", message);
            mHandler.obtainMessage(MSG_SESSION_MESSAGE, message).sendToTarget();
        }
    };

    @SuppressLint("NewApi")
    private final VirtualDisplay.Callback mVirtualDisplayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            //sLogger.trace("VirtualDisplayCallback");
        }
        @Override
        public void onResumed() {
            //sLogger.trace("VirtualDisplayCallback");
        }
        @Override
        public void onStopped() {
            //sLogger.trace("VirtualDisplayCallback");
            // XXX: Can not detect user delete the virtual display from system bar UI
        }
    };
}
