/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.deskclock.alarms;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.android.deskclock.AlarmAlertWakeLock;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.AlarmInstance;
import android.os.Vibrator;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.hardware.SprdSensor;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.PixelFormat;

import android.telephony.SubscriptionManager;
import android.util.Log;
import com.android.deskclock.Utils;

import com.android.deskclock.AlarmUtils;
import java.util.Calendar;
import android.app.ProcessProtection;

/**
 * This service is in charge of starting/stopping the alarm. It will bring up and manage the
 * {@link AlarmActivity} as well as {@link AlarmKlaxon}.
 *
 * Registers a broadcast receiver to listen for snooze/dismiss intents. The broadcast receiver
 * exits early if AlarmActivity is bound to prevent double-processing of the snooze/dismiss intents.
 */
public class AlarmService extends Service {
    /**
     * AlarmActivity and AlarmService (when unbound) listen for this broadcast intent
     * so that other applications can snooze the alarm (after ALARM_ALERT_ACTION and before
     * ALARM_DONE_ACTION).
     */
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";

    /**
     * AlarmActivity and AlarmService listen for this broadcast intent so that other
     * applications can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
     */
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";

    /** A public action sent by AlarmService when the alarm has started. */
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    /** A public action sent by AlarmService when the alarm has stopped for any reason. */
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    /** Private action used to stop an alarm with this service. */
    public static final String STOP_ALARM_ACTION = "STOP_ALARM";

    /* SPRD: for bug 511193 add alarmclock FlipSilent @{ */
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private float mData;
    private boolean isSupportMuteAlarms = false;
    private Context mContext;
    /* @} */
    /** Binder given to AlarmActivity */
    private final IBinder mBinder = new Binder();

    /** Whether the service is currently bound to AlarmActivity */
    private boolean mIsBound = false;

    /** Whether the receiver is currently registered */
    private boolean mIsRegistered = false;

    /* SPRD: modify for bug 634752 @{ */
    private AlarmInstance mInstance;
    /* @} */

    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mIsBound = false;
        return super.onUnbind(intent);
    }

    /**
     * Utility method to help stop an alarm properly. Nothing will happen, if alarm is not firing
     * or using a different instance.
     *
     * @param context application context
     * @param instance you are trying to stop
     */
    public static void stopAlarm(Context context, AlarmInstance instance) {
        final Intent intent = AlarmInstance.createIntent(context, AlarmService.class, instance.mId)
                .setAction(STOP_ALARM_ACTION);

        // We don't need a wake lock here, since we are trying to kill an alarm
        context.startService(intent);
        /* UNISOC:: Modify for bug 1208905 @{ */
        new ProcessProtection().setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_IDLE);
        /* @} */
    }

    /* SPRD: modify for bug 634752 1104190 @{ */
    private TelephonyManager mTelephonyManager;
    private int mInitialCallState;
    private AlarmInstance mCurrentAlarm = null;
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
            state = TelephonyManager.getDefault().getCallState();
            LogUtils.v("onCallStateChanged state = " + state + "mInitialCallState = "+ mInitialCallState);
            if (state == mInitialCallState) {
                return;
            } else if (mInitialCallState != TelephonyManager.CALL_STATE_IDLE
                    && state != TelephonyManager.CALL_STATE_IDLE) {
                mInitialCallState = state;
                return;
            } else {
                mInitialCallState = state;
            }
            if (state != TelephonyManager.CALL_STATE_IDLE) {
                //inCalling
                AlarmStateManager.setMissedState(AlarmService.this, mInstance);
            } else if (mInstance != null) {
                //idle
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                startAlarm(mInstance);
            }
        }
    };
    /* @} */

    private void startAlarm(AlarmInstance instance) {
        LogUtils.v("AlarmService.start with instance: " + instance.mId);

        /* SPRD: modify for bug 634752 932513 1104190@{ */
        mInitialCallState = TelephonyManager.getDefault().getCallState();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        /* @} */
        if (mCurrentAlarm != null) {
            /* UNISOC: Modify for bug 1234701 1399829 @{ */
            if (mCurrentAlarm.getAlarmTime().before(instance.getAlarmTime())) {
                AlarmStateManager.setMissedState(this, mCurrentAlarm);
                stopCurrentAlarm();
            } else {
                AlarmStateManager.setMissedState(this, instance);
                AlarmStateManager.mForVolumeInstance = mCurrentAlarm;
                return;
            }
            /* @} */
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this);

        mCurrentAlarm = instance;
        /* UNISOC:: Modify for bug 1208905 @{ */
        new ProcessProtection().setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_PERSISTENT);
        /* @} */
        AlarmNotifications.showAlarmNotification(this, mCurrentAlarm);
        /* SPRD: modify for bug 634752 1104190@{ */
        if (mInitialCallState == TelephonyManager.CALL_STATE_IDLE) {
            AlarmKlaxon.start(this, mCurrentAlarm);
        } else {
            Vibrator vibrator = (Vibrator)getSystemService(Service.VIBRATOR_SERVICE);
            vibrator.vibrate(100);
        }
        /* @} */
        sendBroadcast(new Intent(ALARM_ALERT_ACTION));
    }

    private void stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            LogUtils.v("There is no current alarm to stop");
            return;
        }

        final long instanceId = mCurrentAlarm.mId;
        LogUtils.v("AlarmService.stop with instance: %s", instanceId);

        AlarmKlaxon.stop(this);

        sendBroadcast(new Intent(ALARM_DONE_ACTION));

        // Since we use the same id for all notifications, the system has no way to distinguish the
        // firing notification we were bound to from other subsequent notifications posted for the
        // same AlarmInstance (e.g. after snoozing). We workaround the issue by forcing removal of
        // the notification and re-posting it.
        stopForeground(true /* removeNotification */);
        mCurrentAlarm = AlarmInstance.getInstance(getContentResolver(), instanceId);
        if (mCurrentAlarm != null) {
            AlarmNotifications.updateNotification(this, mCurrentAlarm);
        }

        mCurrentAlarm = null;
        AlarmAlertWakeLock.releaseCpuLock();
    }

    private final BroadcastReceiver mActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            LogUtils.i("AlarmService received intent %s", action);
            if (mCurrentAlarm == null || mCurrentAlarm.mAlarmState != AlarmInstance.FIRED_STATE) {
                LogUtils.i("No valid firing alarm");
                return;
            }

            if (mIsBound) {
                LogUtils.i("AlarmActivity bound; AlarmService no-op");
                return;
            }

            switch (action) {
                case ALARM_SNOOZE_ACTION:
                    // Set the alarm state to snoozed.
                    // If this broadcast receiver is handling the snooze intent then AlarmActivity
                    // must not be showing, so always show snooze toast.
                    AlarmStateManager.setSnoozeState(context, mCurrentAlarm, true /* showToast */);
                    Events.sendAlarmEvent(R.string.action_snooze, R.string.label_intent);
                    break;
                case ALARM_DISMISS_ACTION:
                    // Set the alarm state to dismissed.
                    AlarmStateManager.deleteInstanceAndUpdateParent(context, mCurrentAlarm);
                    Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_intent);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        /* SPRD: modify for bug 634752 1104190 @{ */
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        /* @} */

        // Register the broadcast receiver
        final IntentFilter filter = new IntentFilter(ALARM_SNOOZE_ACTION);
        filter.addAction(ALARM_DISMISS_ACTION);
        registerReceiver(mActionsReceiver, filter);
        mIsRegistered = true;
        /* SPRD: for bug 511193 add alarmclock FlipSilent @{ */
        registerSensorEventListener();
        /* @} */
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.v("AlarmService.onStartCommand() with %s", intent);

        final long instanceId = AlarmInstance.getId(intent.getData());
        switch (intent.getAction()) {
            case AlarmStateManager.CHANGE_STATE_ACTION:
                AlarmStateManager.handleIntent(this, intent);

                // If state is changed to firing, actually fire the alarm!
                final int alarmState = intent.getIntExtra(AlarmStateManager.ALARM_STATE_EXTRA, -1);
                if (alarmState == AlarmInstance.FIRED_STATE) {
                    /* SPRD: Bug 591844 The video will be paused when alarm goes off @{ */
                    if (mContext.getResources().getBoolean(R.bool.video_paused_at_fired_state)
                            && AlarmStateManager.isLiveMoveTopActivity(this)) {
                        Vibrator vibrator = (Vibrator)getSystemService(Service.VIBRATOR_SERVICE);
                        vibrator.vibrate(3000);
                        break;
                    }
                    /* @} */
                    final ContentResolver cr = this.getContentResolver();
                    /* SPRD: modify for bug 634752 @{ */
                    mInstance = AlarmInstance.getInstance(cr, instanceId);
                    if (mInstance == null) {
                        LogUtils.e("No instance found to start alarm: %d", instanceId);
                        if (mCurrentAlarm != null) {
                            // Only release lock if we are not firing alarm
                            AlarmAlertWakeLock.releaseCpuLock();
                        }
                        break;
                    }

                    if (mCurrentAlarm != null && mCurrentAlarm.mId == instanceId) {
                        LogUtils.e("Alarm already started for instance: %d", instanceId);
                        break;
                    }
                    /* SPRD: modify for bug 748282 @{ */
                    if (AlarmUtils.getFormattedTime(mContext,
                            Calendar.getInstance()).equals(
                            AlarmUtils.getFormattedTime(mContext,
                                    mInstance.getAlarmTime()))) {
                        LogUtils.d("AlarmInstance.FIRED_STATE mInstance " + mInstance.mId);
                        startAlarm(mInstance);
                    }
                    /* @} */
                }
                break;
            case STOP_ALARM_ACTION:
                if (mCurrentAlarm != null && mCurrentAlarm.mId != instanceId) {
                    LogUtils.e("Can't stop alarm for instance: %d because current alarm is: %d",
                            instanceId, mCurrentAlarm.mId);
                    break;
                }
                stopCurrentAlarm();
                stopSelf();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.v("AlarmService.onDestroy() called");
        super.onDestroy();

        /* SPRD: modify for bug 634752 1104190@{ */
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        /* @} */
        /* SPRD: for bug 511193 add alarmclock FlipSilent @{ */
        unregisterSensorEventListener();
        /* @} */
        if (mCurrentAlarm != null) {
            stopCurrentAlarm();
        }

        if (mIsRegistered) {
            unregisterReceiver(mActionsReceiver);
            mIsRegistered = false;
        }
    }

    /* SPRD: for bug 511193 add alarmclock FlipSilent @{ */
    private final SensorEventListener mSensorListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            mData = event.values[SensorManager.DATA_Z];
            if (mContext != null){
                LogUtils.d("SPRDSwitchDetector mData=" + mData);
                if (mData == 2.0){
                    AlarmKlaxon.stop(mContext);
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public void registerSensorEventListener() {
        isSupportMuteAlarms = Settings.Global.getInt(getContentResolver(), Settings.Global.MUTE_ALARMS, 0) != 0;
        LogUtils.v("isSupportMuteAlarms:" + isSupportMuteAlarms);
        mContext = this;
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            /* for bug 544753 modify alarmclock FlipSilent */
            mSensor = mSensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_FLIP);
            if (mSensor != null && isSupportMuteAlarms) {
                mSensorManager.registerListener(mSensorListener, mSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    public void unregisterSensorEventListener() {
        if (mSensorManager != null) {
            LogUtils.d("SensorEventListener mSensorManager != null unregisterListener");
            mSensorManager.unregisterListener(mSensorListener);
            mContext = null;
        }
    }
    /* @} */
}
