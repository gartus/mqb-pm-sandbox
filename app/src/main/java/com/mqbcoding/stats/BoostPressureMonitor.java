package com.mqbcoding.stats;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.apps.auto.sdk.notification.CarNotificationExtender;

import java.util.Date;
import java.util.Map;

public class BoostPressureMonitor implements CarStatsClientTweaked.Listener {
    private static final String TAG = "BoostPressureMonitor";

    public static final String PREF_ENABLED = "engineTempMonitoringActive";
    public static final String PREF_MAX_BOOST_PRESSURE = "maxBoostPressure";

    private static final int NOTIFICATION_ID = 3;

    private static final int NOTIFICATION_TIMEOUT_MS = 60000;

    private static final float HYSTERESIS = 1;

    private final Handler mHandler;
    private final NotificationManager mNotificationManager;

    private final Context mContext;
    private boolean mIsEnabled;
    private float mMaxPressure;
    private float mTorqueTurboPressure;

    enum State {
        UNKNOWN,
        TURBO_PRESSURE_NOMINAL,
        TURBO_PRESSURE_EXCEEDED
    }

    private State mState = State.UNKNOWN;

    public BoostPressureMonitor(Context context, Handler handler) {
        super();

        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mHandler = handler;
        mContext = context;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.registerOnSharedPreferenceChangeListener(mPreferencesListener);
        readPreferences(sharedPreferences);
    }

    private void readPreferences(SharedPreferences preferences) {
        mIsEnabled = preferences.getBoolean(PREF_ENABLED, true);
        mMaxPressure = Float.parseFloat(preferences.getString(PREF_MAX_BOOST_PRESSURE, "20"));
        if (!mIsEnabled) {
            mHandler.post(mDismissNotification);
            mState = State.UNKNOWN;
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            readPreferences(sharedPreferences);
        }
    };

    private final Runnable mDismissNotification = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Dismissing boost pressure notification");
            mNotificationManager.cancel(TAG, NOTIFICATION_ID);
        }
    };

    private void notifyBoostPressureExceeded(String text, int icon) {
        String title = mContext.getString(R.string.notification_boost_pressure_title);

        Notification notification = new NotificationCompat.Builder(mContext, CarStatsService.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning_24dp)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .extend(new CarNotificationExtender.Builder()
                        .setTitle(title)
                        .setSubtitle(text)
                        .setActionIconResId(icon)
                        .setThumbnail(CarUtils.getCarBitmap(mContext, R.drawable.ic_warning_24dp,
                                R.color.car_primary, 128))
                        .setShouldShowAsHeadsUp(true)
                        .build())
                .build();
        mNotificationManager.notify(TAG, NOTIFICATION_ID, notification);
        mHandler.postDelayed(mDismissNotification, NOTIFICATION_TIMEOUT_MS);

        CarNotificationSoundPlayer soundPlayer = new CarNotificationSoundPlayer(mContext, R.raw.light);
        soundPlayer.play();
    }

    @Override
    public void onNewMeasurements(String provider, Date timestamp, Map<String, Object> values) {
        if (!mIsEnabled) {
            return;
        }

        Float coolantTemp = mTorqueTurboPressure;

        if (mState == State.UNKNOWN) {
            mState = State.TURBO_PRESSURE_NOMINAL;
        } else if (mState != State.TURBO_PRESSURE_NOMINAL && mTorqueTurboPressure > mMaxPressure) {
            mState = State.TURBO_PRESSURE_EXCEEDED;
            notifyBoostPressureExceeded(mContext.getString(R.string.notification_exceeded_boost_pressure_text, String.valueOf(mTorqueTurboPressure)), R.drawable.ic_warning_24dp);
        } else if (mState == State.TURBO_PRESSURE_EXCEEDED && mTorqueTurboPressure < (mMaxPressure - HYSTERESIS)) {
            mState = State.TURBO_PRESSURE_NOMINAL;
        }
    }

    @Override
    public void onSchemaChanged() {
        // do nothing
    }

    public void updateTorqueTurboPressure(float mTorqueTurboPressure) {
        this.mTorqueTurboPressure = mTorqueTurboPressure;
    }

    public synchronized void close() {
        mHandler.post(mDismissNotification);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(mPreferencesListener);
    }
}
