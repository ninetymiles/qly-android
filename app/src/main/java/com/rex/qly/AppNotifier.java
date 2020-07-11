package com.rex.qly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppNotifier {

    private final Logger mLogger = LoggerFactory.getLogger(AppNotifier.class);

    private static final String NOTIFY_CHANNEL = "CH-SERVER";
    private static final int NOTIFY_ID = 1;

    private Context mContext;
    private NotificationManager mNotifyMgr;

    private NotificationCompat.Builder mNotifyBuilderReady;
    private NotificationCompat.Builder mNotifyBuilderBusy;

    private boolean mIsServerStart;
    private boolean mIsSessionStart;

    public void onCreate(Context context) {
        //mLogger.trace("");

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent viewIntent = PendingIntent.getActivity(context, 0, intent, 0);
        PendingIntent closeIntent = PendingIntent.getService(context, 0, AppService.createCloseIntent(context), 0);

        mContext = context;
        mNotifyMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFY_CHANNEL,
                    context.getString(R.string.notify_channel_server_status),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.notify_channel_server_status_description));
            mNotifyMgr.createNotificationChannel(channel);
        }

        mNotifyBuilderReady = new NotificationCompat.Builder(context, NOTIFY_CHANNEL);
        mNotifyBuilderReady.setContentIntent(viewIntent);
        mNotifyBuilderReady.setContentTitle(context.getString(R.string.app_name));
        mNotifyBuilderReady.setContentText(context.getString(R.string.notify_ready));
        mNotifyBuilderReady.setOngoing(true);
        mNotifyBuilderReady.setWhen(0);
        mNotifyBuilderReady.setPriority(NotificationCompat.PRIORITY_LOW);
        mNotifyBuilderReady.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher));
        mNotifyBuilderReady.setSmallIcon(R.drawable.ic_present_to_all_white_24dp);
        mNotifyBuilderReady.addAction(R.drawable.ic_close_white_24dp, context.getString(R.string.notify_button_close), closeIntent);

        mNotifyBuilderBusy = new NotificationCompat.Builder(context, NOTIFY_CHANNEL);
        mNotifyBuilderBusy.setContentIntent(viewIntent);
        mNotifyBuilderBusy.setContentTitle(context.getString(R.string.app_name));
        mNotifyBuilderBusy.setContentText(context.getString(R.string.notify_busy));
        mNotifyBuilderBusy.setOngoing(true);
        mNotifyBuilderBusy.setWhen(0);
        mNotifyBuilderBusy.setPriority(NotificationCompat.PRIORITY_LOW);
        mNotifyBuilderBusy.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher));
        mNotifyBuilderBusy.setSmallIcon(R.drawable.ic_cast_connected_white_24dp);
        mNotifyBuilderBusy.addAction(R.drawable.ic_close_white_24dp, context.getString(R.string.notify_button_close), closeIntent);
    }

    public void onSessionStart() {
        //mLogger.trace("");
        if (! mIsSessionStart) {
            mIsSessionStart = true;
            if (mNotifyBuilderBusy != null) {
                notify(NOTIFY_ID, mNotifyBuilderBusy.build());
                mLogger.debug("BUSY");
            }
        }
    }

    public void onSessionStop() {
        //mLogger.trace("");
        if (mIsSessionStart) {
            mIsSessionStart = false;
            if (mNotifyBuilderReady != null) {
                notify(NOTIFY_ID, mNotifyBuilderReady.build());
                mLogger.debug("READY");
            }
        }
    }

    public void onServerStart() {
        //mLogger.trace("");
        if (! mIsServerStart) {
            mIsServerStart = true;
            if (mNotifyBuilderReady != null) {
                notify(NOTIFY_ID, mNotifyBuilderReady.build());
                mLogger.debug("READY");
            }
        }
    }

    public void onServerStop() {
        //mLogger.trace("");
        if (mIsServerStart) {
            mIsServerStart = false;
            mIsSessionStart = false;
            if (mNotifyMgr != null) {
                mNotifyMgr.cancelAll();
            }
            mLogger.debug("CANCEL ALL");
        }
    }

    public void onDestroy() {
        //mLogger.trace("");
        mIsServerStart = false;
        mIsSessionStart = false;
        mNotifyMgr.cancelAll();
        mNotifyMgr = null;
        mNotifyBuilderReady = null;
        mNotifyBuilderBusy = null;
        if (mContext instanceof Service) {
            ((Service) mContext).stopForeground(true);
        }
    }

    private void notify(int id, Notification notify) {
        //mLogger.trace("id:{}", id);
        if (mContext instanceof Service) {
            ((Service) mContext).startForeground(id, notify);
        } else {
            if (mNotifyMgr != null) {
                mNotifyMgr.notify(id, notify);
            }
        }
    }
}
