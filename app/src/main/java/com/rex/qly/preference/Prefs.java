package com.rex.qly.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.provider.Settings;

import com.rex.qly.R;

import java.util.UUID;

public class Prefs {

    // For video resolution
    public static final int RESOLUTION_720P     = 0;
    public static final int RESOLUTION_1080P    = 1;
    public static final int RESOLUTION_4K       = 2;

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public Prefs(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public int getVideoResolution() {
        return Integer.parseInt(mPrefs.getString(mContext.getString(R.string.prefs_video_resolution_key), "1"));
    }

    public Point getVideoResolutionPoint() {
        return getVideoResolutionPoint(getVideoResolution());
    }

    public Point getVideoResolutionPoint(int index) {
        switch (index) {
        case RESOLUTION_4K:
            return new Point(3840, 2160);
        case RESOLUTION_1080P:
            return new Point(1920, 1080);
        case RESOLUTION_720P:
        default:
            return new Point(1280, 720);
        }
    }

    public String getUniqueId() {
        String uniqueId = null;
        String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null) {
            uniqueId = UUID.nameUUIDFromBytes(androidId.getBytes()).toString();
        }
        return mPrefs.getString(mContext.getString(R.string.prefs_unique_id_key), uniqueId);
    }
}
