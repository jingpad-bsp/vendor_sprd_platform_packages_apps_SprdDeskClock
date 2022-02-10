/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.app.FragmentManager;
import com.android.deskclock.AlarmClockFragment;
import com.android.deskclock.LabelDialogFragment;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.alarms.dataadapter.AlarmItemHolder;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.data.Weekdays;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.ringtone.RingtonePickerActivity;
import com.sprd.deskclock.AlarmSlienceDialog;
import java.util.Calendar;
import android.app.FragmentTransaction;
import android.widget.TextView;
import com.android.deskclock.alarms.dataadapter.ExpandedAlarmViewHolder;
import android.view.View;
import com.android.deskclock.AlarmUtils;
import com.sprd.deskclock.AlarmRepeatDialog;
/**
 * Click handler for an alarm time item.
 */
public final class AlarmTimeClickHandler {

    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("AlarmTimeClickHandler");

    private static final String KEY_PREVIOUS_DAY_MAP = "previousDayMap";
    /* SPRD: bug 473507 add auto silences for each alarm @{ */
    public static final String FRAG_TAG_SLIENCE_DIALOG = "slience_dialog";
    // SPRD: bug 473670 new feature add repeat preference for each alarm
    public static final String FRAG_TAG_REPEAT_DIALOG = "repeat_dialog";
    private final Fragment mFragment;
    private final Context mContext;
    private final AlarmUpdateHandler mAlarmUpdateHandler;
    private final ScrollHandler mScrollHandler;

    private Alarm mSelectedAlarm;
    private Bundle mPreviousDaysOfWeekMap;

    public AlarmTimeClickHandler(Fragment fragment, Bundle savedState,
            AlarmUpdateHandler alarmUpdateHandler, ScrollHandler smoothScrollController) {
        mFragment = fragment;
        mContext = mFragment.getActivity().getApplicationContext();
        mAlarmUpdateHandler = alarmUpdateHandler;
        mScrollHandler = smoothScrollController;
        if (savedState != null) {
            mPreviousDaysOfWeekMap = savedState.getBundle(KEY_PREVIOUS_DAY_MAP);
        }
        if (mPreviousDaysOfWeekMap == null) {
            mPreviousDaysOfWeekMap = new Bundle();
        }
    }

    public void setSelectedAlarm(Alarm selectedAlarm) {
        mSelectedAlarm = selectedAlarm;
    }

    public void saveInstance(Bundle outState) {
        outState.putBundle(KEY_PREVIOUS_DAY_MAP, mPreviousDaysOfWeekMap);
    }

    public void setAlarmEnabled(Alarm alarm, boolean newState) {
        if (newState != alarm.enabled) {
            alarm.enabled = newState;
            Events.sendAlarmEvent(newState ? R.string.action_enable : R.string.action_disable,
                    R.string.label_deskclock);
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, alarm.enabled, false);
            LOGGER.d("Updating alarm enabled state to " + newState);
        }
    }

    public void setAlarmVibrationEnabled(Alarm alarm, boolean newState) {
        if (newState != alarm.vibrate) {
            alarm.vibrate = newState;
            Events.sendAlarmEvent(R.string.action_toggle_vibrate, R.string.label_deskclock);
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, false, true);
            LOGGER.d("Updating vibrate state to " + newState);

            if (newState) {
                // Buzz the vibrator to preview the alarm firing behavior.
                final Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (v.hasVibrator()) {
                    v.vibrate(300);
                }
            }
        }
    }

    public void setAlarmRepeatEnabled(Alarm alarm, boolean isEnabled) {
        final Calendar now = Calendar.getInstance();
        final Calendar oldNextAlarmTime = alarm.getNextAlarmTime(now);
        if (!isEnabled) {
             //ExpandedAlarmViewHolder.repeatDays.setVisibility(View.GONE);
            // Remove all repeat days
            alarm.daysOfWeek = Weekdays.NONE;
            final Calendar newNextAlarmTime = alarm.getNextAlarmTime(now);
            final boolean popupToast = !oldNextAlarmTime
                    .equals(newNextAlarmTime);
            mAlarmUpdateHandler.asyncUpdateAlarm(alarm, popupToast, false);
        } else {
            showRepeatAlarmDialog(alarm, mFragment);
            //ExpandedAlarmViewHolder.repeat.setChecked(false);
        }
    }

    public void setDayOfWeekEnabled(Alarm alarm, boolean checked, int index) {
         showRepeatAlarmDialog(alarm, mFragment);
    }

    public void onDeleteClicked(AlarmItemHolder itemHolder) {
        if (mFragment instanceof AlarmClockFragment) {
            ((AlarmClockFragment) mFragment).removeItem(itemHolder);
        }
        final Alarm alarm = itemHolder.item;
        Events.sendAlarmEvent(R.string.action_delete, R.string.label_deskclock);
        /* UNISOC: modify for bug 1106945@{ */
        AlarmUpdateHandler.mIsAddAlarm = true;
        /* @} */
        mAlarmUpdateHandler.asyncDeleteAlarm(alarm);
        LOGGER.d("Deleting alarm.");
    }
    /* SPRD: bug 473670 new feature add repeat preference for each alarm @{ */
    public static void showRepeatAlarmDialog(Alarm alarm, Fragment fragment) {
        final FragmentManager manager = fragment.getFragmentManager();
        final FragmentTransaction ft = manager.beginTransaction();
        final Fragment prev = manager.findFragmentByTag(FRAG_TAG_REPEAT_DIALOG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.commitAllowingStateLoss();
        AlarmRepeatDialog repeatDialogFragment = AlarmRepeatDialog.newInstance(alarm);
        repeatDialogFragment.setTargetFragment(fragment, 0);
        if (repeatDialogFragment != null && !repeatDialogFragment.isAdded()) {
            repeatDialogFragment.showAllowingStateLoss(manager, FRAG_TAG_REPEAT_DIALOG);
        }
    }
    /* @} */

    public void onClockClicked(Alarm alarm) {
        mSelectedAlarm = alarm;
        Events.sendAlarmEvent(R.string.action_set_time, R.string.label_deskclock);
        TimePickerDialogFragment.show(mFragment, alarm.hour, alarm.minutes);
    }

    public void dismissAlarmInstance(AlarmInstance alarmInstance) {
        // SPRD: Bug 613499 do explicit Intent protect
        if (alarmInstance == null) {
            return;
        }
        final Intent dismissIntent = AlarmStateManager.createStateChangeIntent(
                mContext, AlarmStateManager.ALARM_DISMISS_TAG, alarmInstance,
                AlarmInstance.PREDISMISSED_STATE);
        mContext.startService(dismissIntent);
        mAlarmUpdateHandler.showPredismissToast(alarmInstance);
    }

    public void onRingtoneClicked(Context context, Alarm alarm) {
        mSelectedAlarm = alarm;
        Events.sendAlarmEvent(R.string.action_set_ringtone, R.string.label_deskclock);

        final Intent intent =
                RingtonePickerActivity.createAlarmRingtonePickerIntent(context, alarm);
        context.startActivity(intent);
    }

    public void onEditLabelClicked(Alarm alarm) {
        Events.sendAlarmEvent(R.string.action_set_label, R.string.label_deskclock);
        final LabelDialogFragment fragment =
                LabelDialogFragment.newInstance(alarm, alarm.label, mFragment.getTag());
        LabelDialogFragment.show(mFragment.getFragmentManager(), fragment);
    }

    public void onTimeSet(int hourOfDay, int minute) {
        if (mSelectedAlarm == null) {
            // If mSelectedAlarm is null then we're creating a new alarm.
            final Alarm a = new Alarm();
            a.hour = hourOfDay;
            a.minutes = minute;
            a.enabled = true;
            mAlarmUpdateHandler.asyncAddAlarm(a);
        } else {
            mSelectedAlarm.hour = hourOfDay;
            mSelectedAlarm.minutes = minute;
            mSelectedAlarm.enabled = true;
            mScrollHandler.setSmoothScrollStableId(mSelectedAlarm.id);
            mAlarmUpdateHandler.asyncUpdateAlarm(mSelectedAlarm, true, false);
            mSelectedAlarm = null;
        }
    }
     /* SPRD: bug 473507 add auto silences for each alarm @{ */
     public void setAutoSilence(Alarm alarm,TextView textView) {
        showAutoSilenceDialog(alarm, mFragment, textView);
    }

    public static void showAutoSilenceDialog(Alarm alarm, Fragment fragment, TextView textView) {
        final FragmentManager manager = fragment.getFragmentManager();
        final FragmentTransaction ft = manager.beginTransaction();
        final Fragment prev = manager
                .findFragmentByTag(FRAG_TAG_SLIENCE_DIALOG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.commitAllowingStateLoss();
        AlarmSlienceDialog slienceDialogFragment = AlarmSlienceDialog
                .newInstance(alarm, textView);
        slienceDialogFragment.setTargetFragment(fragment, 0);
        if (slienceDialogFragment != null && !slienceDialogFragment.isAdded()) {
            slienceDialogFragment.showAllowingStateLoss(manager,
                    FRAG_TAG_SLIENCE_DIALOG);
        }
    }
    /* @} */
}