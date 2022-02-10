package com.sprd.deskclock;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;

import com.android.deskclock.R;
import com.android.deskclock.data.City;

import android.util.ArrayMap;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import com.android.deskclock.Utils;

public class TimeZonePlugin {

    private static final String TAG = "TimeZonePlugin";

    public TimeZonePlugin() {
    }

    public String[] getIds(Resources resources) {
        if (resources.getBoolean(R.bool.using_sprd_cities_id)) {
            return resources.getStringArray(R.array.cities_id_sprd);
        } else {
            return resources.getStringArray(R.array.cities_id);
        }
    }

    public String[] getNames(Resources resources) {
        if (resources.getBoolean(R.bool.using_sprd_cities_id)) {
            return resources.getStringArray(R.array.cities_names_sprd);
        } else {
            return resources.getStringArray(R.array.cities_names);
        }
    }

    public String[] getTimezones(Resources resources) {
        if (resources.getBoolean(R.bool.using_sprd_cities_tz)) {
            return resources.getStringArray(R.array.cities_tz_sprd);
        } else {
            return resources.getStringArray(R.array.cities_tz);
        }
    }

}
