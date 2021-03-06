/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.deskclock;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import com.android.deskclock.controller.Controller;
import com.android.deskclock.alarms.AlarmStateManager;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.events.Events;
import android.content.SharedPreferences;
/* SPRD: bug 621890 modify poweronoff alarm @{ */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;
/* @} */

public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * When running on N devices, we're interested in the boot completed event that is sent while
     * the user is still locked, so that we can schedule alarms.
     */
    @SuppressLint("InlinedApi")
    private static final String ACTION_BOOT_COMPLETED = Utils.isNOrLater()
            ? Intent.ACTION_LOCKED_BOOT_COMPLETED : Intent.ACTION_BOOT_COMPLETED;

    /**
     * This receiver handles a variety of actions:
     *
     * <ul>
     *     <li>Clean up backup data that was recently restored to this device on
     *     ACTION_COMPLETE_RESTORE.</li>
     *     <li>Reset timers and stopwatch on ACTION_BOOT_COMPLETED</li>
     *     <li>Fix alarm states on ACTION_BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED,
     *     and LOCALE_CHANGED</li>
     *     <li>Rebuild notifications on MY_PACKAGE_REPLACED</li>
     * </ul>
     */
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        LogUtils.i("AlarmInitReceiver " + action);

        final PendingResult result = goAsync();
        final WakeLock wl = AlarmAlertWakeLock.createPartialWakeLock(context);
        wl.acquire();

        // We need to increment the global id out of the async task to prevent race conditions
        AlarmStateManager.updateGlobalIntentId(context);
        // Updates stopwatch and timer data after a device reboot so they are as accurate as
        // possible.
        if (ACTION_BOOT_COMPLETED.equals(action)) {
            DataModel.getDataModel().updateAfterReboot();
            // Stopwatch and timer data need to be updated on time change so the reboot
            // functionality works as expected.
        } else if (Intent.ACTION_TIME_CHANGED.equals(action)) {
            DataModel.getDataModel().updateAfterTimeSet();
        }

        // Clear stopwatch data and reset timers because they rely on elapsed real-time values
        // which are meaningless after a device reboot.
        if (ACTION_BOOT_COMPLETED.equals(action)) {
            //DataModel.getDataModel().clearLaps();
            //DataModel.getDataModel().resetStopwatch();
            //Events.sendStopwatchEvent(R.string.action_reset, R.string.label_reboot);
            //DataModel.getDataModel().resetTimers(R.string.label_reboot);
            /* SPRD: bug 474360 add poweronoff alarm @{ */
            final SharedPreferences prefs = Utils.getDefaultSharedPreferences(context);
            prefs.edit().putString("type_shutdown_alarm", SystemProperties.get("ro.bootmode","unknown")).apply();
            /* @} */
            /* SPRD: bug 621890 modify poweronoff alarm @{ */
            if ("alarm".equals(SystemProperties.get("ro.bootmode","unknown"))) {
                  saveShutdownAlarmTimer(prefs);
            }
            /* @} */
        }

        // Update shortcuts so they exist for the user.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            Controller.getController().updateShortcuts();
            /*UNISOC: Modify for bug 1106733 @{*/
            NotificationChannelManager.getInstance().initChannels(context);
            /*}@*/
        }

        // Notifications are canceled by the system on application upgrade. This broadcast signals
        // that the new app is free to rebuild the notifications using the existing data.
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            DataModel.getDataModel().updateAllNotifications();
            Controller.getController().updateShortcuts();
            /*UNISOC: Modify for bug 1106733 @{*/
            NotificationChannelManager.getInstance().initChannels(context);
            /*}@*/
        }


        AsyncHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Process restored data if any exists
                    if (!DeskClockBackupAgent.processRestoredData(context)) {
                        // Update all the alarm instances on time change event
                        AlarmStateManager.fixAlarmInstances(context);
                    }
                } finally {
                    result.finish();
                    wl.release();
                    LogUtils.v("AlarmInitReceiver finished");
                }
            }
        });
    }
    /* SPRD: bug 621890 modify poweronoff alarm @{ */
    public void saveShutdownAlarmTimer(final SharedPreferences prefs) {
        try {
            FileInputStream inputReader = null;
            Scanner scan = null;
            File fileName = new File("/mnt/vendor/alarm_flag");
            long alarmTime = 0;
            if(fileName.exists()) {
                try{
                    inputReader = new FileInputStream(fileName);
                    scan = new Scanner(inputReader);
                    int count = 0;
                    while (scan.hasNext()) {
                        if (count == 1) {
                            alarmTime = scan.nextLong();
                            prefs.edit().putLong("time_shutdown_alarm", alarmTime).apply();
                            LogUtils.i("saveShutdownAlarmTimer", "shutdownTimer: "+alarmTime);
                            break;
                        }
                        scan.next();
                        count++;
                    }
                } catch(FileNotFoundException e) {
                    LogUtils.e("/mnt/vendor/alarm_flag");
                } catch(NumberFormatException e) {
                    LogUtils.e("!!!!!!!number format error.!!!!!!!!");
                    e.printStackTrace();
                }finally {
                    inputReader.close();
                    /* UNISOC: Modify for bug 1198237 @{ */
                    if (null != scan) {
                        scan.close();
                    }
                    /* @} */
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    /* @} */
}
