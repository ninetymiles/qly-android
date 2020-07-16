package com.rex.qly.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewConfiguration;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.rex.qly.BuildConfig;
import com.rex.qly.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FragmentSetting extends PreferenceFragmentCompat {

    private final Logger mLogger = LoggerFactory.getLogger(FragmentSetting.class);

    private static final boolean ENABLE_DEV_MODE = false;

    public static final String TAG = "SETTING";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preference_settings, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //mLogger.trace("");

        ListPreference listPrefs;
        listPrefs = (ListPreference) getPreferenceScreen().findPreference(getString(R.string.prefs_video_resolution_key));
        listPrefs.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mLogger.trace("key:{} newValue:{}", preference.getKey(), newValue);
                //mAppConnection.setPreferResolution(mPrefs.getVideoResolutionPoint(Integer.parseInt((String) newValue)));
                return true;
            }
        });

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        EditTextPreference textPrefs = (EditTextPreference) getPreferenceScreen().findPreference(getString(R.string.prefs_rtmp_server_key));
        textPrefs.setSummary(prefs.getString(getString(R.string.prefs_rtmp_server_key), BuildConfig.DEFAULT_RTMP_SERVER_ADDRESS));

        // For version info item
        Preference prefsVersion = getPreferenceScreen().findPreference(getString(R.string.prefs_version_key));
        prefsVersion.setSummary(String.format(getString(R.string.about_version_summary), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        prefsVersion.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private long mTapTime;
            private int mTapCount;
            private Toast mToast;
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //mLogger.trace("mTapCount:{}", mTapCount);
                long currentTime = System.currentTimeMillis();
                if (ENABLE_DEV_MODE && currentTime - mTapTime < ViewConfiguration.getJumpTapTimeout()) { //
                    mTapCount++;
                    if (mToast != null) {
                        mToast.cancel();
                    }
                    if (mTapCount >= 20 || prefs.getBoolean(getString(R.string.prefs_development_key), false)) {
                        mToast = Toast.makeText(getActivity(), getString(R.string.about_toast_development_on), Toast.LENGTH_SHORT);
                        mToast.show();
                        prefs.edit().putBoolean(getString(R.string.prefs_development_key), true).apply();
                    } else if (mTapCount >= 6 || prefs.getBoolean(getString(R.string.prefs_experimental_key), false)) {
                        mToast = Toast.makeText(getActivity(), getString(R.string.about_toast_experimental_on), Toast.LENGTH_SHORT);
                        mToast.show();
                        prefs.edit().putBoolean(getString(R.string.prefs_experimental_key), true).apply();
                    } else if (mTapCount >= 3) {
                        mToast = Toast.makeText(getActivity(), getString(R.string.about_toast_experimental, 6 - mTapCount), Toast.LENGTH_SHORT);
                        mToast.show();
                    }
                } else {
                    mTapCount = 1;
                }
                mTapTime = System.currentTimeMillis();
                return true; // Avoid continue report to PreferenceTree
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        //mLogger.trace("");

        // Need specified the title, PreferenceFragmentCompat will not auto apply from PreferenceScreen title
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.menu_settings);
        }
    }
}

