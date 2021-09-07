package com.rex.qly;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import androidx.multidex.MultiDex;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) { // Instead extends from MultiDexApplication
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    //.detectAll()
                    .detectNetwork()
                    .penaltyLog()
                    //.penaltyDeath()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    //.detectAll()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    //.penaltyDeath()
                    .build());
        }

        if (! BuildConfig.DEBUG) {
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(Level.INFO);

            RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
            rollingFileAppender.setContext(ctx);
            rollingFileAppender.setAppend(true);
            rollingFileAppender.setFile("/mnt/sdcard/rex/qly.log");

            TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
            rollingPolicy.setContext(ctx);
            rollingPolicy.setFileNamePattern("/mnt/sdcard/rex/qly.%d{yyyy-MM-dd}.log");
            rollingPolicy.setMaxHistory(6);
            rollingPolicy.setParent(rollingFileAppender);
            rollingPolicy.start();

            rollingFileAppender.setRollingPolicy(rollingPolicy);

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(ctx);
            encoder.setPattern("%date %level/%logger [%thread] %class{0}::%method %msg%n");
            encoder.start();

            rollingFileAppender.setEncoder(encoder);
            rollingFileAppender.start();

            rootLogger.addAppender(rollingFileAppender);
        }
    }
}
