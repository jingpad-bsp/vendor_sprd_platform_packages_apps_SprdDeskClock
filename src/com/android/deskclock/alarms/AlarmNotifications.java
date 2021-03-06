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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.support.v4.content.ContextCompat;

import com.android.deskclock.AlarmClockFragment;
import com.android.deskclock.AlarmInitReceiver;
import com.android.deskclock.AlarmUtils;
import com.android.deskclock.DeskClock;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.Utils;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import android.media.AudioAttributes;
import java.util.Date;
import java.text.ParseException;
import com.android.deskclock.NotificationChannelManager;
import com.android.deskclock.NotificationChannelManager.Channel;

public final class AlarmNotifications {
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";
    /**
     * Formats times such that chronological order and lexicographical order agree.
     */
    private static final DateFormat SORT_KEY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    /*UNISOC: Modify for bug 1108200 @{*/
    private static final DateFormat CURRENT_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:00.000", Locale.US);
    /*}@*/

    /**
     * This value is coordinated with group ids from
     * {@link com.android.deskclock.data.NotificationModel}
     */
    private static final String UPCOMING_GROUP_KEY = "1";

    /**
     * This value is coordinated with group ids from
     * {@link com.android.deskclock.data.NotificationModel}
     */
    private static final String MISSED_GROUP_KEY = "4";

    /**
     * This value is coordinated with notification ids from
     * {@link com.android.deskclock.data.NotificationModel}
     */
    private static final int ALARM_GROUP_NOTIFICATION_ID = Integer.MAX_VALUE - 4;

    /**
     * This value is coordinated with notification ids from
     * {@link com.android.deskclock.data.NotificationModel}
     */
    private static final int ALARM_GROUP_MISSED_NOTIFICATION_ID = Integer.MAX_VALUE - 5;

    /**
     * This value is coordinated with notification ids from
     * {@link com.android.deskclock.data.NotificationModel}
     */
    private static final int ALARM_FIRING_NOTIFICATION_ID = Integer.MAX_VALUE - 7;

    /*UNISOC: Modify for bug 1108200 @{*/
    private static boolean mHasMissed = false;
    private static boolean mHasUpcoming = false;
    /*}@*/

    static synchronized void showLowPriorityNotification(Context context,
            AlarmInstance instance) {
        LogUtils.v("Displaying low priority notification for alarm instance: " + instance.mId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                    Channel.ALARM_NOTIFICATION_CHANNEL) // UNISOC: Modify for bug 1106733
                .setShowWhen(false)
                .setContentTitle(context.getString(
                        R.string.alarm_alert_predismiss_title))
                .setContentText(AlarmUtils.getAlarmText(context, instance, true /* includeLabel */))
                .setColor(ContextCompat.getColor(context, R.color.default_background))
                .setSound(null)
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setAutoCancel(false)
                .setSortKey(createSortKey(instance))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        if (Utils.isNOrLater()) {
            builder.setGroup(UPCOMING_GROUP_KEY);
        }

        // Setup up hide notification
        Intent hideIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DELETE_TAG, instance,
                AlarmInstance.HIDE_NOTIFICATION_STATE);
        final int id = instance.hashCode();
        builder.setDeleteIntent(PendingIntent.getService(context, id,
                hideIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.PREDISMISSED_STATE);
        builder.addAction(R.drawable.ic_alarm_off_24dp,
                context.getString(R.string.alarm_alert_dismiss_now_text),
                PendingIntent.getService(context, id,
                        dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup content action if instance is owned by alarm
        Intent viewAlarmIntent = createViewAlarmIntent(context, instance);
        builder.setContentIntent(PendingIntent.getActivity(context, id,
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        final Notification notification = builder.build();
        nm.notify(id, notification);
        updateUpcomingAlarmGroupNotification(context, -1, notification);
    }

    static synchronized void showHighPriorityNotification(Context context,
            AlarmInstance instance) {
        LogUtils.v("Displaying high priority notification for alarm instance: " + instance.mId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                    Channel.ALARM_NOTIFICATION_CHANNEL) // UNISOC: Modify for bug 1106733
                .setShowWhen(false)
                .setContentTitle(context.getString(R.string.alarm_alert_predismiss_title))
                .setContentText(AlarmUtils.getAlarmText(context, instance, true /* includeLabel */))
                .setColor(ContextCompat.getColor(context, R.color.default_background))
                .setSound(null)
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setAutoCancel(false)
                .setSortKey(createSortKey(instance))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        if (Utils.isNOrLater()) {
            builder.setGroup(UPCOMING_GROUP_KEY);
        }

        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.PREDISMISSED_STATE);
        final int id = instance.hashCode();
        builder.addAction(R.drawable.ic_alarm_off_24dp,
                context.getString(R.string.alarm_alert_dismiss_now_text),
                PendingIntent.getService(context, id,
                        dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup content action if instance is owned by alarm
        Intent viewAlarmIntent = createViewAlarmIntent(context, instance);
        builder.setContentIntent(PendingIntent.getActivity(context, id,
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        final Notification notification = builder.build();
        nm.notify(id, notification);
        updateUpcomingAlarmGroupNotification(context, -1, notification);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static boolean isGroupSummary(Notification n) {
        return (n.flags & Notification.FLAG_GROUP_SUMMARY) == Notification.FLAG_GROUP_SUMMARY;
    }

    /**
     * Method which returns the first active notification for a given group. If a notification was
     * just posted, provide it to make sure it is included as a potential result. If a notification
     * was just canceled, provide the id so that it is not included as a potential result. These
     * extra parameters are needed due to a race condition which exists in
     * {@link NotificationManager#getActiveNotifications()}.
     *
     * @param context Context from which to grab the NotificationManager
     * @param group The group key to query for notifications
     * @param canceledNotificationId The id of the just-canceled notification (-1 if none)
     * @param postedNotification The notification that was just posted
     * @return The first active notification for the group
     */
    @TargetApi(Build.VERSION_CODES.N)
    private static Notification getFirstActiveNotification(Context context, String group,
            int canceledNotificationId, Notification postedNotification) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final StatusBarNotification[] notifications = nm.getActiveNotifications();
        Notification firstActiveNotification = postedNotification;
        for (StatusBarNotification statusBarNotification : notifications) {
            final Notification n = statusBarNotification.getNotification();
            /*UNISOC: Modify for bug 1108200 1138231 @{*/
            LogUtils.v("getFirstActiveNotification n: " + n);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:00.000");
            Date dateCurrent = new Date(0L);
            Date dateIncoming = new Date(0L);
            String statusbarKey = n.getSortKey();
            String statusbarKeyData;
            if ("alarmNotif".equals(n.getChannelId()) && statusbarKey != null) {
                statusbarKeyData = UPCOMING_GROUP_KEY.equals(n.getGroup()) ? statusbarKey : statusbarKey.substring(7);
                try {
                    dateIncoming = sdf.parse(statusbarKeyData);
                    dateCurrent = sdf.parse(getDateString());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            if (UPCOMING_GROUP_KEY.equals(n.getGroup()) && dateCurrent.getTime() < dateIncoming.getTime()) {
                mHasUpcoming = true;
            }

            if (MISSED_GROUP_KEY.equals(n.getGroup()) && dateCurrent.getTime() > dateIncoming.getTime()) {
                mHasMissed = true;
            }
            /*}@*/
            if (!isGroupSummary(n)
                    && group.equals(n.getGroup())
                    && statusBarNotification.getId() != canceledNotificationId) {
                if (firstActiveNotification == null
                        || n.getSortKey().compareTo(firstActiveNotification.getSortKey()) < 0) {
                    firstActiveNotification = n;
                }
            }
        }
        return firstActiveNotification;
    }

    /*UNISOC: Modify for bug 1108200 @{*/
    private static String getDateString() {
        return CURRENT_TIME_FORMAT.format(System.currentTimeMillis());
    }
    /*}@*/

    @TargetApi(Build.VERSION_CODES.N)
    private static Notification getActiveGroupSummaryNotification(Context context, String group) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final StatusBarNotification[] notifications = nm.getActiveNotifications();
        for (StatusBarNotification statusBarNotification : notifications) {
            final Notification n = statusBarNotification.getNotification();
            if (isGroupSummary(n) && group.equals(n.getGroup())) {
                return n;
            }
        }
        return null;
    }

    private static void updateUpcomingAlarmGroupNotification(Context context,
            int canceledNotificationId, Notification postedNotification) {
        if (!Utils.isNOrLater()) {
            return;
        }

        final NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        final Notification firstUpcoming = getFirstActiveNotification(context, UPCOMING_GROUP_KEY,
                canceledNotificationId, postedNotification);
        if (firstUpcoming == null) {
            nm.cancel(ALARM_GROUP_NOTIFICATION_ID);
            return;
        }

        /*UNISOC: Modify for bug 1108200 @{*/
        if (mHasMissed == false) nm.cancel(ALARM_GROUP_MISSED_NOTIFICATION_ID);
        mHasMissed = false;
        mHasUpcoming = false;
        /*}@*/

        Notification summary = getActiveGroupSummaryNotification(context, UPCOMING_GROUP_KEY);
        /*UNISOC: Modify for bug 1106733 1164766 @{*/
        if (summary == null
                || !Objects.equals(summary.contentIntent, firstUpcoming.contentIntent)) {
            summary = new NotificationCompat.Builder(context,
                            Channel.ALARM_NOTIFICATION_CHANNEL)
                    .setShowWhen(false)
                    .setContentTitle(context.getString(R.string.alarm_alert_predismiss_title))
                    .setContentIntent(firstUpcoming.contentIntent)
                    .setColor(ContextCompat.getColor(context, R.color.default_background))
                    .setSound(null)
                    .setSmallIcon(R.drawable.stat_notify_alarm)
                    .setGroup(UPCOMING_GROUP_KEY)
                    .setGroupSummary(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setLocalOnly(true)
                    .build();
            nm.notify(ALARM_GROUP_NOTIFICATION_ID, summary);
        }
        /*}@*/
    }

    private static void updateMissedAlarmGroupNotification(Context context,
            int canceledNotificationId, Notification postedNotification) {
        if (!Utils.isNOrLater()) {
            return;
        }

        final NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        final Notification firstMissed = getFirstActiveNotification(context, MISSED_GROUP_KEY,
                canceledNotificationId, postedNotification);
        if (firstMissed == null) {
            nm.cancel(ALARM_GROUP_MISSED_NOTIFICATION_ID);
            return;
        }

        /*UNISOC: Modify for bug 1108200 @{*/
        if (mHasUpcoming == false) nm.cancel(ALARM_GROUP_NOTIFICATION_ID);
        mHasUpcoming = false;
        mHasMissed = false;
        /*}@*/

        Notification summary = getActiveGroupSummaryNotification(context, MISSED_GROUP_KEY);
        /*UNISOC: Modify for bug 1106733 1164766 @{*/
        if (summary == null
                || !Objects.equals(summary.contentIntent, firstMissed.contentIntent)) {
            summary = new NotificationCompat.Builder(context,
                            Channel.ALARM_NOTIFICATION_CHANNEL)
                    .setShowWhen(false)
                    .setContentTitle(context.getString(R.string.alarm_missed_title))
                    .setContentIntent(firstMissed.contentIntent)
                    .setColor(ContextCompat.getColor(context, R.color.default_background))
                    .setSound(null)
                    .setSmallIcon(R.drawable.stat_notify_alarm)
                    .setGroup(MISSED_GROUP_KEY)
                    .setGroupSummary(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setLocalOnly(true)
                    .build();
            nm.notify(ALARM_GROUP_MISSED_NOTIFICATION_ID, summary);
        }
        /*}@*/
    }

    static synchronized void showSnoozeNotification(Context context,
            AlarmInstance instance) {
        LogUtils.v("Displaying snoozed notification for alarm instance: " + instance.mId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                            Channel.ALARM_NOTIFICATION_CHANNEL) // UNISOC: Modify for bug 1106733
                .setShowWhen(false)
                .setContentTitle(instance.getLabelOrDefault(context))
                .setContentText(context.getString(R.string.alarm_alert_snooze_until,
                        AlarmUtils.getFormattedTime(context, instance.getAlarmTime())))
                .setColor(ContextCompat.getColor(context, R.color.default_background))
                .setSound(null)
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setAutoCancel(false)
                .setSortKey(createSortKey(instance))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        if (Utils.isNOrLater()) {
            builder.setGroup(UPCOMING_GROUP_KEY);
        }

        // Setup up dismiss action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        final int id = instance.hashCode();
        builder.addAction(R.drawable.ic_alarm_off_24dp,
                context.getString(R.string.alarm_alert_dismiss_text),
                PendingIntent.getService(context, id,
                        dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup content action if instance is owned by alarm
        Intent viewAlarmIntent = createViewAlarmIntent(context, instance);
        builder.setContentIntent(PendingIntent.getActivity(context, id,
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        final Notification notification = builder.build();
        nm.notify(id, notification);
        updateUpcomingAlarmGroupNotification(context, -1, notification);
    }

    static synchronized void showMissedNotification(Context context,
            AlarmInstance instance) {
        LogUtils.v("Displaying missed notification for alarm instance: " + instance.mId);

        String label = instance.mLabel;
        String alarmTime = AlarmUtils.getFormattedTime(context, instance.getAlarmTime());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                    Channel.ALARM_NOTIFICATION_CHANNEL) // UNISOC: Modify for bug 1106733
                .setShowWhen(false)
                .setContentTitle(context.getString(R.string.alarm_missed_title))
                .setContentText(instance.mLabel.isEmpty() ? alarmTime :
                        context.getString(R.string.alarm_missed_text, alarmTime, label))
                .setColor(ContextCompat.getColor(context, R.color.default_background))
                .setSound(null)
                .setSortKey(createSortKey(instance))
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        if (Utils.isNOrLater()) {
            builder.setGroup(MISSED_GROUP_KEY);
        }

        final int id = instance.hashCode();

        // Setup dismiss intent
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(context,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        builder.setDeleteIntent(PendingIntent.getService(context, id,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Setup content intent
        Intent showAndDismiss = AlarmInstance.createIntent(context, AlarmStateManager.class,
                instance.mId);
        /*UNISOC: Modify for bug 1189096 @{*/
        showAndDismiss.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        /*}@*/
        showAndDismiss.putExtra(EXTRA_NOTIFICATION_ID, id);
        showAndDismiss.setAction(AlarmStateManager.SHOW_AND_DISMISS_ALARM_ACTION);
        builder.setContentIntent(PendingIntent.getBroadcast(context, id,
                showAndDismiss, PendingIntent.FLAG_UPDATE_CURRENT));

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        final Notification notification = builder.build();
        nm.notify(id, notification);
        updateMissedAlarmGroupNotification(context, -1, notification);
    }

    static synchronized void showAlarmNotification(Service service, AlarmInstance instance) {
        LogUtils.v("Displaying alarm notification for alarm instance: " + instance.mId);

        Resources resources = service.getResources();

        //* SPRD: add for bug 602457
        NotificationCompat.Builder notification = new NotificationCompat.Builder(service,
                     Channel.FIRED_ALARM_NOTIFICATION_CHANNEL) // UNISOC: Modify for bug 1106733
                .setContentTitle(instance.getLabelOrDefault(service))
                .setContentText(AlarmUtils.getFormattedTime(service, instance.getAlarmTime()))
                .setColor(ContextCompat.getColor(service, R.color.default_background))
                .setSound(null)
                .setSmallIcon(R.drawable.stat_notify_alarm)
                .setOngoing(true)
                .setAutoCancel(false)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setWhen(0)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        // Setup Snooze Action
        Intent snoozeIntent = AlarmStateManager.createStateChangeIntent(service,
                AlarmStateManager.ALARM_SNOOZE_TAG, instance, AlarmInstance.SNOOZE_STATE);
        snoozeIntent.putExtra(AlarmStateManager.FROM_NOTIFICATION_EXTRA, true);
        PendingIntent snoozePendingIntent = PendingIntent.getService(service,
                ALARM_FIRING_NOTIFICATION_ID, snoozeIntent,PendingIntent.FLAG_UPDATE_CURRENT); //UNISOC: Modify for bug 1171086
        notification.addAction(R.drawable.ic_snooze_24dp,
                resources.getString(R.string.alarm_alert_snooze_text), snoozePendingIntent);

        // Setup Dismiss Action
        Intent dismissIntent = AlarmStateManager.createStateChangeIntent(service,
                AlarmStateManager.ALARM_DISMISS_TAG, instance, AlarmInstance.DISMISSED_STATE);
        dismissIntent.putExtra(AlarmStateManager.FROM_NOTIFICATION_EXTRA, true);
        PendingIntent dismissPendingIntent = PendingIntent.getService(service,
                ALARM_FIRING_NOTIFICATION_ID, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);//UNISOC: Modify for bug 1171086
        notification.addAction(R.drawable.ic_alarm_off_24dp,
                resources.getString(R.string.alarm_alert_dismiss_text),
                dismissPendingIntent);

        /* SPRD: bug 474360 add poweronoff alarm @{ */
        Intent fullScreenIntent;
        SharedPreferences prefs = Utils.getDefaultSharedPreferences(service);
        String typeShutdownAlarm = prefs.getString("type_shutdown_alarm", "");
        LogUtils.v("alarm notification typeShutdownAlarm " + typeShutdownAlarm);
        /* SPRD: bug 621890,956161 modify poweronoff alarm @{ */
        boolean isShutdownAlarm = false;
        long alarmTime = prefs.getLong("time_shutdown_alarm", 0);
        isShutdownAlarm = String.valueOf(instance.getAlarmTime().getTimeInMillis()).equals(String.valueOf(alarmTime * 1000));
        if (typeShutdownAlarm != null && typeShutdownAlarm.equals("alarm") && isShutdownAlarm) {
            /** @} **/
            // Setup fullscreen intent
            fullScreenIntent = AlarmInstance.createIntent(service, AlarmShutDownActivity.class, instance.mId);

            // set action, so we can be different then content pending intent
            fullScreenIntent.setAction("fullscreen_activity");
            fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            notification.setFullScreenIntent(PendingIntent.getActivity(service,
                    ALARM_FIRING_NOTIFICATION_ID, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT), true); //UNISOC: Modify for bug 1171086
            notification.setPriority(NotificationCompat.PRIORITY_MAX);

            /*UNISOC: Modify for bug 1106733 @{*/
            NotificationChannelManager.applyChannel(notification, service, Channel.FIRED_ALARM_NOTIFICATION_CHANNEL);
            /*}@*/

            clearNotification(service, instance);
            //* SPRD: add for bug 602457
            //nm.notify(instance.hashCode(), notification.build());
            /** Fix bug 604407 Not start power off activity directly {@ **/
            service.startActivity(fullScreenIntent);
            /** @} **/
        } else {
            // Setup Content Action
            Intent contentIntent = AlarmInstance.createIntent(service, AlarmActivity.class,
                    instance.mId);
            notification.setContentIntent(PendingIntent.getActivity(service,
                    ALARM_FIRING_NOTIFICATION_ID, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT)); //UNISOC: Modify for bug 1171086

            // Setup fullscreen intent
            fullScreenIntent = AlarmInstance.createIntent(service, AlarmActivity.class,
                    instance.mId);
            // set action, so we can be different then content pending intent
            fullScreenIntent.setAction("fullscreen_activity");
            fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            notification.setFullScreenIntent(PendingIntent.getActivity(service,
                    ALARM_FIRING_NOTIFICATION_ID, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT), true); //UNISOC: Modify for bug 1171086
            notification.setPriority(NotificationCompat.PRIORITY_MAX);

            /*UNISOC: Modify for bug 1106733 @{*/
            NotificationChannelManager.applyChannel(notification, service, Channel.FIRED_ALARM_NOTIFICATION_CHANNEL);
            /*}@*/

            clearNotification(service, instance);
            service.startForeground(ALARM_FIRING_NOTIFICATION_ID, notification.build()); //UNISOC: Modify for bug 1171086
            //service.startActivity(fullScreenIntent);
        }
        /* @} */
    }

    static synchronized void clearNotification(Context context, AlarmInstance instance) {
        LogUtils.v("Clearing notifications for alarm instance: " + instance.mId);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        final int id = instance.hashCode();
        nm.cancel(id);
        updateUpcomingAlarmGroupNotification(context, id, null);
        updateMissedAlarmGroupNotification(context, id, null);
    }

    /**
     * Updates the notification for an existing alarm. Use if the label has changed.
     */
    static void updateNotification(Context context, AlarmInstance instance) {
        switch (instance.mAlarmState) {
            case AlarmInstance.LOW_NOTIFICATION_STATE:
                showLowPriorityNotification(context, instance);
                break;
            case AlarmInstance.HIGH_NOTIFICATION_STATE:
                showHighPriorityNotification(context, instance);
                break;
            case AlarmInstance.SNOOZE_STATE:
                showSnoozeNotification(context, instance);
                break;
            case AlarmInstance.MISSED_STATE:
                showMissedNotification(context, instance);
                break;
            default:
                LogUtils.d("No notification to update");
        }
    }

    static Intent createViewAlarmIntent(Context context, AlarmInstance instance) {
        long alarmId = instance.mAlarmId == null ? Alarm.INVALID_ID : instance.mAlarmId;
        Intent viewAlarmIntent = Alarm.createIntent(context, DeskClock.class, alarmId);
        //viewAlarmIntent.putExtra(DeskClock.SELECT_TAB_INTENT_EXTRA, DeskClock.ALARM_TAB_INDEX);
        viewAlarmIntent.putExtra(AlarmClockFragment.SCROLL_TO_ALARM_INTENT_EXTRA, alarmId);
        viewAlarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return viewAlarmIntent;
    }
    /**
     * Alarm notifications are sorted chronologically. Missed alarms are sorted chronologically
     * <strong>after</strong> all upcoming/snoozed alarms by including the "MISSED" prefix on the
     * sort key.
     *
     * @param instance the alarm instance for which the notification is generated
     * @return the sort key that specifies the order of this alarm notification
     */
    private static String createSortKey(AlarmInstance instance) {
        final String timeKey = SORT_KEY_FORMAT.format(instance.getAlarmTime().getTime());
        final boolean missedAlarm = instance.mAlarmState == AlarmInstance.MISSED_STATE;
        return missedAlarm ? ("MISSED " + timeKey) : timeKey;
    }

}
