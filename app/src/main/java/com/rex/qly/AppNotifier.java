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

    private enum State { IDLE, READY, BUSY }
    private State mState = State.IDLE;

    public void onCreate(Context context) {
        //mLogger.trace("");

        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent viewIntent = PendingIntent.getActivity(context, 0, intent, 0);

        mContext = context;
        mNotifyMgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFY_CHANNEL,
                    context.getString(R.string.notify_channel_server_status),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(context.getString(R.string.notify_channel_server_status_description));
            mNotifyMgr.createNotificationChannel(channel);
        }

        mNotifyBuilderReady = new NotificationCompat.Builder(context, NOTIFY_CHANNEL)
                .setContentIntent(viewIntent)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notify_ready))
                .setOngoing(true)
                .setWhen(0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setSmallIcon(R.drawable.ic_present_to_all_white_24dp)
                .addAction(R.drawable.ic_close_white_24dp,
                        context.getString(R.string.notify_button_close),
                        PendingIntent.getService(context, 0, AppService.createServerStopIntent(context), 0));

        mNotifyBuilderBusy = new NotificationCompat.Builder(context, NOTIFY_CHANNEL)
                .setContentIntent(viewIntent)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notify_busy))
                .setOngoing(true)
                .setWhen(0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setSmallIcon(R.drawable.ic_cast_connected_white_24dp)
                .addAction(R.drawable.ic_close_white_24dp,
                        context.getString(R.string.notify_button_stop),
                        PendingIntent.getService(context, 0, AppService.createSessionStopIntent(context), 0));
    }

    public void onSessionStart() {
        //mLogger.trace("");
        if (!State.IDLE.equals(mState)) return;

        mState = State.BUSY;
        mLogger.debug("{}", mState);
        if (mNotifyBuilderBusy != null) {
            notify(NOTIFY_ID, mNotifyBuilderBusy.build());
        }
    }

    public void onSessionStop() {
        //mLogger.trace("");
        if (!State.BUSY.equals(mState)) return;

        mState = State.READY;
        mLogger.debug("{}", mState);
        if (mNotifyBuilderReady != null) {
            notify(NOTIFY_ID, mNotifyBuilderReady.build());
        }
    }

    public void onServerStart() {
        //mLogger.trace("");
        if (!State.IDLE.equals(mState)) return;

        mState = State.READY;
        mLogger.debug("{}", mState);
        if (mNotifyBuilderReady != null) {
            notify(NOTIFY_ID, mNotifyBuilderReady.build());
        }
    }

    public void onServerStop() {
        //mLogger.trace("");
        if (!State.READY.equals(mState)) return;

        mState = State.IDLE;
        mLogger.debug("{}", mState);
        if (mNotifyMgr != null) {
            mNotifyMgr.cancelAll();
        }
        if (mContext instanceof Service) {
            ((Service) mContext).stopForeground(true);
        }
    }

    public void onDestroy() {
        //mLogger.trace("");
        mNotifyMgr.cancelAll();
        mNotifyMgr = null;
        mNotifyBuilderReady = null;
        mNotifyBuilderBusy = null;
        if (mContext instanceof Service) {
            ((Service) mContext).stopForeground(true);
        }
    }

    private void notify(int id, Notification notify) {
        //mLogger.trace("id:{} notify:{}", id, notify);
        if (mContext instanceof Service) {
            ((Service) mContext).startForeground(id, notify);
        } else {
            if (mNotifyMgr != null) {
                mNotifyMgr.notify(id, notify);
            }
        }
    }
}
