package com.rex.qly.preference;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.rex.qly.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferenceViewActivity extends AppCompatActivity {

    private final Logger mLogger = LoggerFactory.getLogger(PreferenceViewActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLogger.trace("");

        setContentView(R.layout.activity_preference);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        if (getSupportFragmentManager().findFragmentByTag(FragmentSetting.TAG) == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.preference_content, new FragmentSetting(), FragmentSetting.TAG)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
