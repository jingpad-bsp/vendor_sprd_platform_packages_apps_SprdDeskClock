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

package com.android.deskclock.alarms.dataadapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.preference.PreferenceManager;
import android.os.Vibrator;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.deskclock.AnimatorUtils;
import com.android.deskclock.ItemAdapter;
import com.android.deskclock.R;
import com.android.deskclock.ThemeUtils;
import com.android.deskclock.Utils;
import com.android.deskclock.alarms.AlarmTimeClickHandler;
import com.android.deskclock.data.DataModel;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.uidata.UiDataModel;
import com.android.deskclock.settings.SettingsActivity;
import java.util.List;
import com.sprd.deskclock.AlarmRepeatDialog.ShowRepeatAlarmDialogListener;
import static android.content.Context.VIBRATOR_SERVICE;
import static android.view.View.TRANSLATION_Y;

import android.annotation.SuppressLint;
import android.content.UriPermission;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.ArraySet;
import java.util.Set;

/**
 * A ViewHolder containing views for an alarm item in expanded state.
 */
public final class ExpandedAlarmViewHolder extends AlarmItemViewHolder implements ShowRepeatAlarmDialogListener{
    public static final int VIEW_TYPE = R.layout.alarm_time_expanded;

    public final CheckBox repeat;
    private final TextView editLabel;
    public final LinearLayout repeatDays;
    private final CompoundButton[] dayButtons = new CompoundButton[7];
    public final CheckBox vibrate;
    public final TextView ringtone;
    public final TextView delete;
    private final View hairLine;
     /* SPRD: bug 473507 add auto silences for each alarm @{ */
    public TextView autoSilence;
    private static final String DEFAULT_ALARM_TIMEOUT_SETTING = "10";
    /* @} */
    private final boolean mHasVibrator;

    private ExpandedAlarmViewHolder(View itemView, boolean hasVibrator) {
        super(itemView);

        mHasVibrator = hasVibrator;

        delete = (TextView) itemView.findViewById(R.id.delete);
        repeat = (CheckBox) itemView.findViewById(R.id.repeat_onoff);
        vibrate = (CheckBox) itemView.findViewById(R.id.vibrate_onoff);
        ringtone = (TextView) itemView.findViewById(R.id.choose_ringtone);
        editLabel = (TextView) itemView.findViewById(R.id.edit_label);
        repeatDays = (LinearLayout) itemView.findViewById(R.id.repeat_days);
        hairLine = itemView.findViewById(R.id.hairline);
        // SPRD: bug 473507 add auto silences for each alarm
        autoSilence = (TextView)itemView.findViewById(R.id.auto_silence);

        final Context context = itemView.getContext();
        itemView.setBackground(new LayerDrawable(new Drawable[] {
                ContextCompat.getDrawable(context, R.drawable.alarm_background_expanded),
                ThemeUtils.resolveDrawable(context, R.attr.selectableItemBackground)
        }));

        // Build button for each day.
        final LayoutInflater inflater = LayoutInflater.from(context);
        final List<Integer> weekdays = DataModel.getDataModel().getWeekdayOrder().getCalendarDays();
        for (int i = 0; i < 7; i++) {
            final View dayButtonFrame = inflater.inflate(R.layout.day_button, repeatDays,
                    false /* attachToRoot */);
            final CompoundButton dayButton =
                    (CompoundButton) dayButtonFrame.findViewById(R.id.day_button_box);
            final int weekday = weekdays.get(i);
            dayButton.setText(UiDataModel.getUiDataModel().getShortWeekday(weekday));
            dayButton.setContentDescription(UiDataModel.getUiDataModel().getLongWeekday(weekday));
            repeatDays.addView(dayButtonFrame);
            dayButtons[i] = dayButton;
        }

        // Cannot set in xml since we need compat functionality for API < 21
        final Drawable labelIcon = Utils.getVectorDrawable(context, R.drawable.ic_label);
        editLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(labelIcon, null, null, null);
        final Drawable deleteIcon = Utils.getVectorDrawable(context, R.drawable.ic_delete_small);
        delete.setCompoundDrawablesRelativeWithIntrinsicBounds(deleteIcon, null, null, null);

        // Collapse handler
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Events.sendAlarmEvent(R.string.action_collapse_implied, R.string.label_deskclock);
                getItemHolder().collapse();
            }
        });
        arrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Events.sendAlarmEvent(R.string.action_collapse, R.string.label_deskclock);
                getItemHolder().collapse();
            }
        });
        // Edit time handler
        clock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAlarmTimeClickHandler().onClockClicked(getItemHolder().item);
            }
        });
        // Edit label handler
        editLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getAlarmTimeClickHandler().onEditLabelClicked(getItemHolder().item);
            }
        });
        // Vibrator checkbox handler
        vibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAlarmTimeClickHandler().setAlarmVibrationEnabled(getItemHolder().item,
                        ((CheckBox) v).isChecked());
            }
        });
        // Ringtone editor handler
        ringtone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getAlarmTimeClickHandler().onRingtoneClicked(context, getItemHolder().item);
            }
        });
        // Delete alarm handler
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAlarmTimeClickHandler().onDeleteClicked(getItemHolder());
                v.announceForAccessibility(context.getString(R.string.alarm_deleted));
            }
        });

        autoSilence.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAlarmTimeClickHandler().setAutoSilence(getItemHolder().item,autoSilence);
            }
        });
        // Repeat checkbox handler
        repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final boolean checked = ((CheckBox) view).isChecked();
                getAlarmTimeClickHandler().setAlarmRepeatEnabled(getItemHolder().item, checked);
                //getItemHolder().notifyItemChanged(ANIMATE_REPEAT_DAYS);
            }
        });
        // Day buttons handler
        for (int i = 0; i < dayButtons.length; i++) {
            final int buttonIndex = i;
            dayButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final boolean isChecked = ((CompoundButton) view).isChecked();
                    getAlarmTimeClickHandler().setDayOfWeekEnabled(getItemHolder().item,
                            isChecked, buttonIndex);
                }
            });
        }

        itemView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onBindItemView(final AlarmItemHolder itemHolder) {
        super.onBindItemView(itemHolder);

        final Alarm alarm = itemHolder.item;
        final AlarmInstance alarmInstance = itemHolder.getAlarmInstance();
        final Context context = itemView.getContext();
        bindEditLabel(context, alarm);
        bindDaysOfWeekButtons(alarm, context);
        bindVibrator(alarm);
        bindRingtone(context, alarm);
        bindPreemptiveDismissButton(context, alarm, alarmInstance);
        // UNISOC: Modify for bug 1140464
        bindAutoSilence(context, alarm);
    }

    /* UNISOC: Modify for bug 1140464 {@ */
    private void bindAutoSilence(Context context, Alarm alarm) {
        int i = Integer.parseInt(Utils.getDefaultSharedPreferences(context)
                .getString(alarm.id + "", DEFAULT_ALARM_TIMEOUT_SETTING));
        String initialAutoSilenceTitle = context.getResources()
                .getStringArray(R.array.auto_silence)[i / 5];
        autoSilence.setText(context.getResources().getString(R.string.auto_silence_title)
                + " " + initialAutoSilenceTitle);
    }
    /* @} */

    private void bindRingtone(Context context, Alarm alarm) {
        final String title = DataModel.getDataModel().getRingtoneTitle(alarm.alert);
        ringtone.setText(title);

        final String description = context.getString(R.string.ringtone_description);
        ringtone.setContentDescription(description + " " + title);

        final boolean silent = Utils.RINGTONE_SILENT.equals(alarm.alert);
        /* UNISOC: Modify for bug 1192864 1299323{@ */
        Drawable icon = Utils.getVectorDrawable(context,
                silent ? R.drawable.ic_ringtone_silent : R.drawable.ic_ringtone);
        if (!silent && !isRingtoneHasPermission(context, alarm.alert)) {

            //icon is changed because the ringtone has no access to be played.
            icon = Utils.getVectorDrawable(context, R.drawable.ic_ringtone_not_found);
            final int colorAccent = ThemeUtils.resolveColor(context, R.attr.colorAccent);
            icon.setColorFilter(colorAccent, PorterDuff.Mode.SRC_ATOP);
        }
        /* @} */

        ringtone.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    /* UNISOC: Modify for bug 1192864 {@ */
    /**
     * Only custom ringtones will be checked, system internal or default(i.e. #DEFAULT_ALARM_ALERT_URI)
     * ringtones are considered to have the permission to be played normally.
     */
    @SuppressLint("NewApi")
    private boolean isRingtoneHasPermission(Context context, Uri uri) {
        final List<UriPermission> uriPermissions =
                context.getContentResolver().getPersistedUriPermissions();
        final Set<Uri> permissions = new ArraySet<>(uriPermissions.size());
        for (UriPermission uriPermission : uriPermissions) {
            permissions.add(uriPermission.getUri());
        }

        if (uri.toString().indexOf("media/internal/audio/") == -1
                && uri.toString().indexOf("settings/system/") == -1) {
            return permissions.contains(uri);
        }
        return true;
    }
    /* @} */

    private void bindDaysOfWeekButtons(Alarm alarm, Context context) {
        final List<Integer> weekdays = DataModel.getDataModel().getWeekdayOrder().getCalendarDays();
        for (int i = 0; i < weekdays.size(); i++) {
            final CompoundButton dayButton = dayButtons[i];
            if (alarm.daysOfWeek.isBitOn(weekdays.get(i))) {
                dayButton.setChecked(true);
                dayButton.setTextColor(ThemeUtils.resolveColor(context,
                        android.R.attr.windowBackground));
            } else {
                dayButton.setChecked(false);
                dayButton.setTextColor(Color.WHITE);
            }
        }
        if (alarm.daysOfWeek.isRepeating()) {
            repeat.setChecked(true);
            repeatDays.setVisibility(View.VISIBLE);
        } else {
            repeat.setChecked(false);
            repeatDays.setVisibility(View.GONE);
        }
    }

    private void bindEditLabel(Context context, Alarm alarm) {
        editLabel.setText(alarm.label);
        editLabel.setContentDescription(alarm.label != null && alarm.label.length() > 0
                ? context.getString(R.string.label_description) + " " + alarm.label
                : context.getString(R.string.no_label_specified));
    }

    private void bindVibrator(Alarm alarm) {
        if (!mHasVibrator) {
            vibrate.setVisibility(View.INVISIBLE);
        } else {
            vibrate.setVisibility(View.VISIBLE);
            vibrate.setChecked(alarm.vibrate);
        }
    }

    private AlarmTimeClickHandler getAlarmTimeClickHandler() {
        return getItemHolder().getAlarmTimeClickHandler();
    }

    @Override
    public Animator onAnimateChange(List<Object> payloads, int fromLeft, int fromTop, int fromRight,
            int fromBottom, long duration) {
        if (payloads == null || payloads.isEmpty() || !payloads.contains(ANIMATE_REPEAT_DAYS)) {
            return null;
        }

        final boolean isExpansion = repeatDays.getVisibility() == View.VISIBLE;
        final int height = repeatDays.getHeight();
        setTranslationY(isExpansion ? -height : 0f, isExpansion ? -height : height);
        repeatDays.setVisibility(View.VISIBLE);
        repeatDays.setAlpha(isExpansion ? 0f : 1f);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(AnimatorUtils.getBoundsAnimator(itemView,
                fromLeft, fromTop, fromRight, fromBottom,
                itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom()),
                ObjectAnimator.ofFloat(repeatDays, View.ALPHA, isExpansion ? 1f : 0f),
                ObjectAnimator.ofFloat(repeatDays, TRANSLATION_Y, isExpansion ? 0f : -height),
                ObjectAnimator.ofFloat(ringtone, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(vibrate, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(editLabel, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(preemptiveDismissButton, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(hairLine, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(delete, TRANSLATION_Y, 0f),
                ObjectAnimator.ofFloat(arrow, TRANSLATION_Y, 0f));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                setTranslationY(0f, 0f);
                repeatDays.setAlpha(1f);
                repeatDays.setVisibility(isExpansion ? View.VISIBLE : View.GONE);
                itemView.requestLayout();
            }
        });
        animatorSet.setDuration(duration);
        animatorSet.setInterpolator(AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        return animatorSet;
    }

    private void setTranslationY(float repeatDaysTranslationY, float translationY) {
        repeatDays.setTranslationY(repeatDaysTranslationY);
        ringtone.setTranslationY(translationY);
        vibrate.setTranslationY(translationY);
        editLabel.setTranslationY(translationY);
        preemptiveDismissButton.setTranslationY(translationY);
        hairLine.setTranslationY(translationY);
        delete.setTranslationY(translationY);
        arrow.setTranslationY(translationY);
    }

    @Override
    public Animator onAnimateChange(final ViewHolder oldHolder, ViewHolder newHolder,
            long duration) {
        if (!(oldHolder instanceof AlarmItemViewHolder)
                || !(newHolder instanceof AlarmItemViewHolder)) {
            return null;
        }

        final boolean isExpanding = this == newHolder;
        AnimatorUtils.setBackgroundAlpha(itemView, isExpanding ? 0 : 255);
        setChangingViewsAlpha(isExpanding ? 0f : 1f);

        final Animator changeAnimatorSet = isExpanding
                ? createExpandingAnimator((AlarmItemViewHolder) oldHolder, duration)
                : createCollapsingAnimator((AlarmItemViewHolder) newHolder, duration);
        changeAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                AnimatorUtils.setBackgroundAlpha(itemView, 255);
                clock.setVisibility(View.VISIBLE);
                onOff.setVisibility(View.VISIBLE);
                arrow.setVisibility(View.VISIBLE);
                arrow.setTranslationY(0f);
                setChangingViewsAlpha(1f);
                arrow.jumpDrawablesToCurrentState();
            }
        });
        return changeAnimatorSet;
    }

    private Animator createCollapsingAnimator(AlarmItemViewHolder newHolder, long duration) {
        arrow.setVisibility(View.INVISIBLE);
        clock.setVisibility(View.INVISIBLE);
        onOff.setVisibility(View.INVISIBLE);

        final boolean daysVisible = repeatDays.getVisibility() == View.VISIBLE;
        final int numberOfItems = countNumberOfItems();

        final View oldView = itemView;
        final View newView = newHolder.itemView;

        final Animator backgroundAnimator = ObjectAnimator.ofPropertyValuesHolder(oldView,
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 255, 0));
        backgroundAnimator.setDuration(duration);

        final Animator boundsAnimator = AnimatorUtils.getBoundsAnimator(oldView, oldView, newView);
        boundsAnimator.setDuration(duration);
        boundsAnimator.setInterpolator(AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final long shortDuration = (long) (duration * ANIM_SHORT_DURATION_MULTIPLIER);
        final Animator repeatAnimation = ObjectAnimator.ofFloat(repeat, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator editLabelAnimation = ObjectAnimator.ofFloat(editLabel, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator repeatDaysAnimation = ObjectAnimator.ofFloat(repeatDays, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator vibrateAnimation = ObjectAnimator.ofFloat(vibrate, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator ringtoneAnimation = ObjectAnimator.ofFloat(ringtone, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator dismissAnimation = ObjectAnimator.ofFloat(preemptiveDismissButton,
                View.ALPHA, 0f).setDuration(shortDuration);
        final Animator deleteAnimation = ObjectAnimator.ofFloat(delete, View.ALPHA, 0f)
                .setDuration(shortDuration);
        final Animator hairLineAnimation = ObjectAnimator.ofFloat(hairLine, View.ALPHA, 0f)
                .setDuration(shortDuration);

        // Set the staggered delays; use the first portion (duration * (1 - 1/4 - 1/6)) of the time,
        // so that the final animation, with a duration of 1/4 the total duration, finishes exactly
        // before the collapsed holder begins expanding.
        long startDelay = 0L;
        final long delayIncrement = (long) (duration * ANIM_LONG_DELAY_INCREMENT_MULTIPLIER)
                / (numberOfItems - 1);
        deleteAnimation.setStartDelay(startDelay);
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            startDelay += delayIncrement;
            dismissAnimation.setStartDelay(startDelay);
        }
        hairLineAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        editLabelAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        vibrateAnimation.setStartDelay(startDelay);
        ringtoneAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        if (daysVisible) {
            repeatDaysAnimation.setStartDelay(startDelay);
            startDelay += delayIncrement;
        }
        repeatAnimation.setStartDelay(startDelay);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(backgroundAnimator, boundsAnimator, repeatAnimation,
                repeatDaysAnimation, vibrateAnimation, ringtoneAnimation, editLabelAnimation,
                deleteAnimation, hairLineAnimation, dismissAnimation);
        return animatorSet;
    }

    private Animator createExpandingAnimator(AlarmItemViewHolder oldHolder, long duration) {
        final View oldView = oldHolder.itemView;
        final View newView = itemView;
        final Animator boundsAnimator = AnimatorUtils.getBoundsAnimator(newView, oldView, newView);
        boundsAnimator.setDuration(duration);
        boundsAnimator.setInterpolator(AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        final Animator backgroundAnimator = ObjectAnimator.ofPropertyValuesHolder(newView,
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 0, 255));
        backgroundAnimator.setDuration(duration);

        final View oldArrow = oldHolder.arrow;
        final Rect oldArrowRect = new Rect(0, 0, oldArrow.getWidth(), oldArrow.getHeight());
        final Rect newArrowRect = new Rect(0, 0, arrow.getWidth(), arrow.getHeight());
        ((ViewGroup) newView).offsetDescendantRectToMyCoords(arrow, newArrowRect);
        ((ViewGroup) oldView).offsetDescendantRectToMyCoords(oldArrow, oldArrowRect);
        final float arrowTranslationY = oldArrowRect.bottom - newArrowRect.bottom;

        arrow.setTranslationY(arrowTranslationY);
        arrow.setVisibility(View.VISIBLE);
        clock.setVisibility(View.VISIBLE);
        onOff.setVisibility(View.VISIBLE);

        final long longDuration = (long) (duration * ANIM_LONG_DURATION_MULTIPLIER);
        final Animator repeatAnimation = ObjectAnimator.ofFloat(repeat, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator repeatDaysAnimation = ObjectAnimator.ofFloat(repeatDays, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator ringtoneAnimation = ObjectAnimator.ofFloat(ringtone, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator dismissAnimation = ObjectAnimator.ofFloat(preemptiveDismissButton,
                View.ALPHA, 1f).setDuration(longDuration);
        final Animator vibrateAnimation = ObjectAnimator.ofFloat(vibrate, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator editLabelAnimation = ObjectAnimator.ofFloat(editLabel, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator hairLineAnimation = ObjectAnimator.ofFloat(hairLine, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator deleteAnimation = ObjectAnimator.ofFloat(delete, View.ALPHA, 1f)
                .setDuration(longDuration);
        final Animator arrowAnimation = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_Y, 0f)
                .setDuration(duration);
        arrowAnimation.setInterpolator(AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN);

        // Set the stagger delays; delay the first by the amount of time it takes for the collapse
        // to complete, then stagger the expansion with the remaining time.
        long startDelay = (long) (duration * ANIM_STANDARD_DELAY_MULTIPLIER);
        final int numberOfItems = countNumberOfItems();
        final long delayIncrement = (long) (duration * ANIM_SHORT_DELAY_INCREMENT_MULTIPLIER)
                / (numberOfItems - 1);
        repeatAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        final boolean daysVisible = repeatDays.getVisibility() == View.VISIBLE;
        if (daysVisible) {
            repeatDaysAnimation.setStartDelay(startDelay);
            startDelay += delayIncrement;
        }
        ringtoneAnimation.setStartDelay(startDelay);
        vibrateAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        editLabelAnimation.setStartDelay(startDelay);
        startDelay += delayIncrement;
        hairLineAnimation.setStartDelay(startDelay);
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            dismissAnimation.setStartDelay(startDelay);
            startDelay += delayIncrement;
        }
        deleteAnimation.setStartDelay(startDelay);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(backgroundAnimator, repeatAnimation, boundsAnimator,
                repeatDaysAnimation, vibrateAnimation, ringtoneAnimation, editLabelAnimation,
                deleteAnimation, hairLineAnimation, dismissAnimation, arrowAnimation);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                AnimatorUtils.startDrawableAnimation(arrow);
            }
        });
        return animatorSet;
    }

    private int countNumberOfItems() {
        // Always between 4 and 6 items.
        int numberOfItems = 4;
        if (preemptiveDismissButton.getVisibility() == View.VISIBLE) {
            numberOfItems++;
        }
        if (repeatDays.getVisibility() == View.VISIBLE) {
            numberOfItems++;
        }
        return numberOfItems;
    }

    private void setChangingViewsAlpha(float alpha) {
        repeat.setAlpha(alpha);
        editLabel.setAlpha(alpha);
        repeatDays.setAlpha(alpha);
        vibrate.setAlpha(alpha);
        ringtone.setAlpha(alpha);
        hairLine.setAlpha(alpha);
        delete.setAlpha(alpha);
        preemptiveDismissButton.setAlpha(alpha);
    }

    public static class Factory implements ItemAdapter.ItemViewHolder.Factory {

        private final LayoutInflater mLayoutInflater;
        private final boolean mHasVibrator;

        public Factory(Context context) {
            mLayoutInflater = LayoutInflater.from(context);
            mHasVibrator = ((Vibrator) context.getSystemService(VIBRATOR_SERVICE)).hasVibrator();
        }

        @Override
        public ItemAdapter.ItemViewHolder<?> createViewHolder(ViewGroup parent, int viewType) {
            final View itemView = mLayoutInflater.inflate(viewType, parent, false);
            return new ExpandedAlarmViewHolder(itemView, mHasVibrator);
        }
    }
    @Override
    public void repeatAlarmDialoglistener(Alarm alarm) {
        // TODO Auto-generated method stub

    }

    public void repeatAlarmDialoglistenerWithoutToast(Alarm alarm) {
    }
}
