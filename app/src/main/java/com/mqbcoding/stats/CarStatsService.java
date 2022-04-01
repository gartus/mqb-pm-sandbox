package com.mqbcoding.stats;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.github.martoreto.aauto.vex.CarStatsClient;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class CarStatsService extends CarModeService {
    private static final String TAG = "CarStatsService";

    private static final int NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "car";

    private CarStatsClientTweaked mStatsClient;
    private CarStatsLogger mStatsLogger;
    private OilTempMonitor mOilTempMonitor;
    private EngineSpeedMonitor mEngineSpeedMonitor;
    private WheelStateMonitor mWheelStateMonitor;

    private final IBinder mBinder = new CarStatsBinder();
    private Timer updateTimer;
    private Runnable updateTimerRunnable;
    private static final Map<String, Object> mLastMeasurements = new HashMap<>();

    @SuppressWarnings("unused")
    public class CarStatsBinder extends Binder {
        CarStatsClientTweaked getStatsClient() {
            return mStatsClient;
        }
        OilTempMonitor getOilTempMonitor() {
            return mOilTempMonitor;
        }
        WheelStateMonitor getWheelStateMonitor() {
            return mWheelStateMonitor;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Service starting...");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            mChannel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert mNotificationManager != null;
            mNotificationManager.createNotificationChannel(mChannel);
        }

        mStatsClient = new CarStatsClientTweaked(this);

        mStatsLogger = new CarStatsLogger(this, mStatsClient, new Handler());
        mStatsLogger.registerListener(mStatsLoggerListener);
        mStatsClient.registerListener(mStatsLogger);

        mOilTempMonitor = new OilTempMonitor(this, new Handler());
        mStatsClient.registerListener(mOilTempMonitor);

        mEngineSpeedMonitor = new EngineSpeedMonitor(this,new Handler());
        mStatsClient.registerListener(mEngineSpeedMonitor);

        mWheelStateMonitor = new WheelStateMonitor(this, new Handler());
        mStatsClient.registerListener(mWheelStateMonitor);

        mStatsClient.start();
        createAndStartUpdateTimer();
    }

    private void createAndStartUpdateTimer() {
        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimerRunnable = new Runnable() {
                    public void run() {
                        if (mStatsClient != null) {
                            mLastMeasurements.clear();
                            Map<String, Object> measurements = mStatsClient.getMergedMeasurements();
                            if(measurements != null) {
                                mLastMeasurements.putAll(measurements);
                            }
                        }
                    }
                };
            }
        }, 0, 250);//Update display 0,25 second
    }

    @Override
    protected int getNotificationId() {
        return NOTIFICATION_ID;
    }

    @Override
    protected Notification buildNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationCompat.Builder notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getText(R.string.app_name))
                    .setContentText(getText(R.string.notification_service_text))
                    .setSmallIcon(R.drawable.ic_launcher);
            return notification.build();
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service stopping.");

        if (mStatsLogger != null) {
            mStatsLogger.close();
            mStatsLogger = null;
        }
        if (mOilTempMonitor != null) {
            mOilTempMonitor.close();
            mOilTempMonitor = null;
        }
        if (mEngineSpeedMonitor != null) {
            mEngineSpeedMonitor.close();
            mEngineSpeedMonitor = null;
        }
        updateTimer.cancel();
        mStatsClient.stop();
        mStatsClient = null;

        super.onDestroy();
    }

    private final CarStatsLogger.Listener mStatsLoggerListener = new CarStatsLogger.Listener() {
        @Override
        public void onLogFileComplete(File logFile) {
            LogUploadService.schedule(CarStatsService.this, logFile);
        }
    };
}