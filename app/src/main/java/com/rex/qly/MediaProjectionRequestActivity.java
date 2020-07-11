package com.rex.qly;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MediaProjectionRequestActivity extends Activity {

    private static final Logger sLogger = LoggerFactory.getLogger(MediaProjectionRequestActivity.class);

    private static final int REQUEST_SCREEN_CAPTURE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sLogger.trace("");
        Intent intent = ((MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_SCREEN_CAPTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        sLogger.trace("requestCode:{} resultCode:{}", requestCode, resultCode);
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            Intent intent = AppService.createProjectionIntent(this, resultCode, resultData);
            startService(intent);
            finish();
        }
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, MediaProjectionRequestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static class MediaProjectionRequester21 implements AppService.MediaProjectionRequester {
        public MediaProjectionRequester21() {
            sLogger.trace("");
        }
        @Override
        public void onRequestProjection(Context context) {
            sLogger.trace("");
            context.startActivity(createIntent(context));
        }
    }

    // Since Android 10 (API-29) restrict start activity from background service
    // Need display notification instead
    // Ref: https://developer.android.google.cn/guide/components/activities/background-starts
    public static class MediaProjectionRequester29 implements AppService.MediaProjectionRequester {

        private NotificationManager mNotifyMgr;
        private NotificationCompat.Builder mNotifyBuilder;

        private static final String NOTIFY_CHANNEL = "CH-CAPTURE";
        private static final int NOTIFY_ID = 200;

        public MediaProjectionRequester29() {
            sLogger.trace("");
        }

        @Override
        public void onRequestProjection(@NonNull Context context) {
            sLogger.trace("");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mNotifyMgr = context.getSystemService(NotificationManager.class);
            } else {
                mNotifyMgr = ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(NOTIFY_CHANNEL,
                        context.getString(R.string.notify_channel_capture_request),
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(context.getString(R.string.notify_channel_capture_request_description));
                mNotifyMgr.createNotificationChannel(channel);
            }

            // Prepare required attributes
            mNotifyBuilder = new NotificationCompat.Builder(context, NOTIFY_CHANNEL)
                    .setContentTitle(context.getString(R.string.notify_projection_request_title))
                    .setContentText(context.getString(R.string.notify_projection_request_content))
                    .setSmallIcon(R.drawable.ic_cast_white_24dp);

            // Optional attributes
            mNotifyBuilder.setSubText(context.getString(R.string.notify_projection_request_countdown, 15));
            mNotifyBuilder.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher)); // Show in notification view
            mNotifyBuilder.setPriority(NotificationCompat.PRIORITY_MAX); // For compat Android 7.0 and lower, since Android 8.0 (API-26) use NotificationChannel.setImportance() instead
            mNotifyBuilder.setUsesChronometer(true);
            mNotifyBuilder.setTimeoutAfter(15000);
            mNotifyBuilder.setDeleteIntent(
                    PendingIntent.getService(context, 0, AppService.createProjectionIntent(context, RESULT_CANCELED, null), 0)
            ); // When timeout or user delete auto send intent

            // Show heads-up notifications
            mNotifyBuilder.setFullScreenIntent(PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class)
                            .setAction(Intent.ACTION_VIEW)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    0),
                    true);

            // Show on lock screen, since Android 5.0 (API-21)
            mNotifyBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            mNotifyBuilder.addAction(R.drawable.ic_accept_24dp,
                    context.getString(R.string.notify_projection_request_accept_btn),
                    PendingIntent.getActivity(context, 0, createIntent(context), 0)
            );

            mNotifyMgr.notify(NOTIFY_ID, mNotifyBuilder.build());
        }
    }
}
