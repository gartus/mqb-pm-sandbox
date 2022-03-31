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

import com.github.martoreto.aauto.vex.CarStatsClient;
import com.google.android.apps.auto.sdk.notification.CarNotificationExtender;

import java.util.Date;
import java.util.Map;

public class OilTempMonitor implements CarStatsClient.Listener {
    private static final String TAG = "OilTempMonitor";

    public static final String PREF_ENABLED = "oilTempMonitoringActive";
    public static final String PREF_THRESHOLD = "oilTempThreshold";
    public static final String PREF_MAX_THRESHOLD = "maxOperationTempThreshold";

    public static final String TORQUE_COOLANT_TEMP_KEY = "torqueCoolantTemp";
    public static final String EXLAP_KEY = "oilTemperature";

    private static final int NOTIFICATION_ID = 2;

    private static final int NOTIFICATION_TIMEOUT_MS = 60000;

    private static final float HYSTERESIS = 1;

    private final Handler mHandler;
    private final NotificationManager mNotificationManager;

    private final Context mContext;
    private boolean mIsEnabled;
    private float mHighThreshold;
    private float mLowThreshold;

    enum State {
        UNKNOWN,
        TEMP_NOT_REACHED,
        TEMP_REACHED,
        HIGH_TEMP
    }

    private State mState = State.UNKNOWN;

    public OilTempMonitor(Context context, Handler handler) {
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
        mLowThreshold = Float.parseFloat(preferences.getString(PREF_THRESHOLD, "85"));
        mHighThreshold = Float.parseFloat(preferences.getString(PREF_MAX_THRESHOLD, "120"));
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
            Log.d(TAG, "Dismissing oil temperature notification");
            mNotificationManager.cancel(TAG, NOTIFICATION_ID);
        }
    };

    private void notifyOilTempReached(String text, int icon) {
        String title = mContext.getString(R.string.notification_oil_title);

        Notification notification = new NotificationCompat.Builder(mContext, CarStatsService.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oil)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .extend(new CarNotificationExtender.Builder()
                        .setTitle(title)
                        .setSubtitle(text)
                        .setActionIconResId(icon)
                        .setThumbnail(CarUtils.getCarBitmap(mContext, R.drawable.ic_oil,
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
        if (values.containsKey(EXLAP_KEY)) {
            Float oilTemp = (Float) values.get(EXLAP_KEY);
            Float coolantTemp = (Float) values.get(TORQUE_COOLANT_TEMP_KEY);

            if (mState == State.UNKNOWN && hasReachedOperationalTemp(coolantTemp, oilTemp)) {
                mState = State.TEMP_REACHED;
            } else if (mState == State.UNKNOWN) {
                mState = State.TEMP_NOT_REACHED;
            } else if (mState != State.HIGH_TEMP && isHighTempEngine(oilTemp, coolantTemp)) {
                mState = State.HIGH_TEMP;
                notifyOilTempReached(mContext.getString(R.string.notification_high_oil_text), R.drawable.ic_warning_24dp);
            } else if (mState == State.TEMP_NOT_REACHED && hasReachedOperationalTemp(coolantTemp, oilTemp)) {
                mState = State.TEMP_REACHED;
                notifyOilTempReached(mContext.getString(R.string.notification_oil_text), R.drawable.ic_check_white_24dp);
            } else if (mState == State.TEMP_REACHED && isBelowOperationalTemp(coolantTemp, oilTemp)) {
                mState = State.TEMP_NOT_REACHED;
            } else if (mState == State.HIGH_TEMP && isBelowHighTemp(oilTemp, coolantTemp)) {
                mState = State.TEMP_REACHED;
            }
        }
    }

    private boolean hasReachedOperationalTemp(Float coolantTemp, Float oilTemp) {
        boolean coolantOk = (coolantTemp == null || coolantTemp > mLowThreshold);
        boolean oilOk = (oilTemp == null || oilTemp > mLowThreshold);
        return coolantOk && oilOk;
    }

    private boolean isBelowOperationalTemp(Float coolantTemp, Float oilTemp) {
        boolean coolantNotOk = (coolantTemp != null && coolantTemp < (mLowThreshold - HYSTERESIS));
        boolean oilNotOk = (oilTemp != null && oilTemp < (mLowThreshold - HYSTERESIS));
        return coolantNotOk || oilNotOk;
    }

    private boolean isHighTempEngine(Float coolantTemp, Float oilTemp) {
        boolean coolantHighTemp = (coolantTemp != null && coolantTemp > mHighThreshold);
        boolean oilHighTemp = (oilTemp != null && oilTemp > mHighThreshold);
        return coolantHighTemp || oilHighTemp;
    }

    private boolean isBelowHighTemp(Float coolantTemp, Float oilTemp) {
        boolean coolantOk = (coolantTemp == null || coolantTemp < (mHighThreshold - HYSTERESIS));
        boolean oilOk = (oilTemp == null || oilTemp < (mHighThreshold - HYSTERESIS));
        return coolantOk && oilOk;
    }

    @Override
    public void onSchemaChanged() {
        // do nothing
    }

    public synchronized void close() {
        mHandler.post(mDismissNotification);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(mPreferencesListener);
    }
}
