/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
// [Notification Channel] This Class is to create and handle channel

package com.android.deskclock;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;
import android.support.v4.app.NotificationCompat;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains info on how to create {@link NotificationChannel
 * NotificationChannels}
 */
public class NotificationChannelManager {

    private static final String PREF_NEED_FIRST_INIT = "NeedInit";
    private static NotificationChannelManager instance;

    public static NotificationChannelManager getInstance() {
        LogUtils.d("getInstance: instance: " + instance);
        if (instance == null) {
            instance = new NotificationChannelManager();
        }
        return instance;
    }

    /**
     * Set the channel of notification appropriately.
     *
     */
    public static void applyChannel(
            @NonNull NotificationCompat.Builder notification,
            @NonNull Context context, @Channel String channelId) {

        LogUtils.d("applyChannel: : context: " + context + " channelId: "
                + channelId);
        NotificationChannel channel = NotificationChannelManager
                .getInstance().getChannel(context, channelId);
        notification.setChannelId(channel.getId());
    }

    /** The base Channel IDs for {@link NotificationChannel} */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ Channel.STOPWATCH_NOTIFICATION_CHANNEL,
            Channel.TIMER_NOTIFICATION_CHANNEL, Channel.TIMER_EXPIRED_NOTIFICATION_CHANNEL,
            Channel.ALARM_NOTIFICATION_CHANNEL, Channel.FIRED_ALARM_NOTIFICATION_CHANNEL })

    public @interface Channel {
        String STOPWATCH_NOTIFICATION_CHANNEL = "stopwatchNotif";
        String TIMER_NOTIFICATION_CHANNEL = "timerNotif";
        String TIMER_EXPIRED_NOTIFICATION_CHANNEL = "timerExpiredNotif";
        String ALARM_NOTIFICATION_CHANNEL = "alarmNotif";
        String FIRED_ALARM_NOTIFICATION_CHANNEL = "firedAlarmNotif";
    }

    @Channel
    private static final String[] allChannels = new String[] {
            Channel.STOPWATCH_NOTIFICATION_CHANNEL,
            Channel.TIMER_NOTIFICATION_CHANNEL, Channel.TIMER_EXPIRED_NOTIFICATION_CHANNEL,
            Channel.ALARM_NOTIFICATION_CHANNEL, Channel.FIRED_ALARM_NOTIFICATION_CHANNEL };

    public void firstInitIfNeeded(@NonNull Context context) {
        // TO DO : run this on non UI thread
        firstInitIfNeededSync(context);
    }

    private boolean firstInitIfNeededSync(@NonNull Context context) {
        if (needsFirstInit(context)) {
            LogUtils.d("firstInitIfNeededSync: needsFirstInit true ");
            initChannels(context);
            return true;
        }
        return false;
    }

    public boolean needsFirstInit(@NonNull Context context) {
        return (Utils.getDefaultSharedPreferences(context)
                .getBoolean(PREF_NEED_FIRST_INIT, true));
    }

    public void initChannels(@NonNull Context context) {
        LogUtils.d("NotificationChannelManager.initChannels");

        for (@Channel String channel : allChannels) {
            getChannel(context, channel);
        }
        Utils.getDefaultSharedPreferences(context).edit().putBoolean(PREF_NEED_FIRST_INIT, false).apply();
    }

    @NonNull
    private NotificationChannel getChannel(@NonNull Context context,
            @Channel String channelId) {
        NotificationChannel channel = createChannel(context, channelId);
        return channel;
    }

    private NotificationChannel createChannel(Context context,
            @Channel String channelId) {

        LogUtils.d("createChannel..channelId: " + channelId);

        CharSequence name;
        int importance;
        Uri sound = null;

        switch (channelId) {

            case Channel.STOPWATCH_NOTIFICATION_CHANNEL:
                name = context.getString(R.string.menu_stopwatch);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                break;

            case Channel.TIMER_NOTIFICATION_CHANNEL:
                name = context.getString(R.string.menu_timer);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                break;

            case Channel.TIMER_EXPIRED_NOTIFICATION_CHANNEL:
                name = context.getString(R.string.default_timer_ringtone_title);
                importance = NotificationManager.IMPORTANCE_HIGH;
                break;

            case Channel.ALARM_NOTIFICATION_CHANNEL:
                name = context.getString(R.string.alarm_list_title);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                break;

            case Channel.FIRED_ALARM_NOTIFICATION_CHANNEL:
                name = context.getString(R.string.firing_alarm);
                importance = NotificationManager.IMPORTANCE_HIGH;
                break;

            default:
                throw new IllegalArgumentException("Unknown channel: " + channelId);
        }
        NotificationChannel channel = new NotificationChannel(channelId, name,
                importance);
        // UNISOC: Modify for bug 1131211
        channel.setShowBadge(true);
        channel.enableVibration(false);
        channel.setSound(sound, null);
        channel.enableLights(false);
        channel.setBypassDnd(true);

        getNotificationManager(context).createNotificationChannel(channel);
        LogUtils.d("createChannel ends.channel: " + channel);

        return channel;

    }

    private static NotificationManager getNotificationManager(
            @NonNull Context context) {
        return context.getSystemService(NotificationManager.class);
    }

}
