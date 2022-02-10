package com.sprd.deskclock.worldclock;

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

import com.android.deskclock.Utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import com.android.deskclock.R;
import com.android.deskclock.data.City;
import com.android.deskclock.data.DataModel;

public class CityListAdapter extends BaseAdapter {
    protected ArrayList<City> mCitiesList = new ArrayList<City>();
    protected List<City> mCitiesTempList;
    private final LayoutInflater mInflater;
    private final Context mContext;
    private int mResource;
    private static final String TAG = "CityListAdapter";

    public CityListAdapter(Context context) {
        this(context, R.layout.track_list_item);
    }

    public CityListAdapter(Context context, int resource) {
        super();
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mResource = resource;
        loadData();
    }

    public void reloadData(Context context) {
        loadData();
        notifyDataSetChanged();
    }

    public void loadData() {
        mCitiesList.clear();
        mCitiesTempList = DataModel.getDataModel().getSelectedCities();
        for (int i = 0; i < mCitiesTempList.size(); i++) {
            mCitiesList.add(mCitiesTempList.get(i));
        }
    }

    public void moveItem(int from, int to) {
        synchronized (this) {
            City item = this.getItem(from);
            mCitiesList.remove(from);
            mCitiesList.add(to, item);
        }
        notifyDataSetChanged();
    }

    public void remove(int which) {
        synchronized (this) {
            mCitiesList.remove(which);
        }
        notifyDataSetChanged();
    }

    public int getCount() {
        return (mCitiesList != null) ? mCitiesList.size() : 0;
    }

    @Override
    public City getItem(int p) {
        if (mCitiesList != null && p >= 0 && p < mCitiesList.size()) {
            return mCitiesList.get(p);
        }
        return null;
    }

    @Override
    public long getItemId(int p) {
        return p;
    }

    @Override
    public boolean isEnabled(int p) {
        return mCitiesList != null && mCitiesList.get(p).getId() != null;
    }

    static class ViewHolder {
        TextView name;
        TextView time;
        TextView tz;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {

        ViewHolder holder;
        if (mCitiesList == null || position < 0 || position >= mCitiesList.size()) {
            return new View(mContext);
        }
        if (view == null) {
            holder = new ViewHolder();
            view = mInflater.inflate(mResource, parent, false);
            holder.name = (TextView)(view.findViewById(R.id.city_name));
            holder.time = (TextView)(view.findViewById(R.id.city_time));
            holder.tz = (TextView)(view.findViewById(R.id.city_tz));
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        City city = mCitiesList.get(position);

        updateView(holder, city);
        return view;

    }

    private void updateView(ViewHolder holder, City city) {

        // true indicates the system time is 24 Hour mode, false indicates the system time is 12 Hour mode
        boolean mTimeMode = android.text.format.DateFormat.is24HourFormat(mContext);

        // Home city or city not in DB , use data from the save selected cities list
        holder.name.setText(city.getName());

        // Get timezone from cities DB if available
        final Calendar now = Calendar.getInstance();
        now.setTimeZone(city.getTimeZone());
        if (mTimeMode) {
            holder.time.setText(DateFormat.format("kk:mm", now));
        } else {
            holder.time.setText(DateFormat.format("h:mm aa", now));
        }
        holder.tz.setText(Utils.getGMTHourOffset(city.getTimeZone(), false));
    }
}
