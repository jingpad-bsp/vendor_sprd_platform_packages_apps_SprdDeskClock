/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.deskclock.actionbarmenu;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.KeyEvent;
import com.android.deskclock.R;

import static android.view.Menu.FIRST;
import static android.view.Menu.NONE;

/**
 * {@link MenuItemController} for search menu.
 */
public final class SearchMenuItemController extends AbstractMenuItemController {
private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_SEARCH_MODE = "search_mode";
    private static final int SEARCH_MENU_RES_ID = R.id.menu_item_search;
    private final SearchView.OnQueryTextListener mQueryListener;
    private final SearchModeChangeListener mSearchModeChangeListener;
    private String mQuery = "";
    private boolean mSearchMode;

    /* SPRD Bug 596057 Handle the KEYCODE_SEARCH key event by the onKeyUp method @{ */
    private  MenuItem mSearch;
    private SearchView mSearchView;
    /* @} */
    public SearchMenuItemController(OnQueryTextListener queryListener, Bundle savedState) {
        mSearchModeChangeListener = new SearchModeChangeListener();
        mQueryListener = queryListener;
        if (savedState != null) {
            mSearchMode = savedState.getBoolean(KEY_SEARCH_MODE, false);
            mQuery = savedState.getString(KEY_SEARCH_QUERY, "");
        }
    }

    public void saveInstance(Bundle outState) {
        outState.putString(KEY_SEARCH_QUERY, mQuery);
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode);
    }

    @Override
    public int getId() {
        return SEARCH_MENU_RES_ID;
    }

    @Override
    public void setInitialState(Menu menu) {
        super.setInitialState(menu);
        /* SPRD Bug 596057 Handle the KEYCODE_SEARCH key event by the onKeyUp method @{ */
        mSearch = menu.findItem(SEARCH_MENU_RES_ID);
        mSearchView = (SearchView) MenuItemCompat.getActionView(mSearch);
        /* @} */
        //SPRD bug 603278
        /* SPRD: Modify for bug 606647,645223 @{ */
        //mSearchView.setMaxWidth(300);
        /* @} */
        mSearchView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mSearchView.setQuery(mQuery, false);
        mSearchView.setOnCloseListener(mSearchModeChangeListener);
        mSearchView.setOnSearchClickListener(mSearchModeChangeListener);
        mSearchView.setOnQueryTextListener(mQueryListener);
        if (mSearchMode) {
            mSearchView.requestFocus();
            mSearchView.setIconified(false);
        }
    }

    @Override
    public void showMenuItem(Menu menu) {
        menu.findItem(SEARCH_MENU_RES_ID).setVisible(true);
    }

    @Override
    public boolean handleMenuItemClick(MenuItem item) {
        // The search view is handled by {@link #mSearchListener}. Skip handling here.
        return false;
    }

    public String getQueryText() {
        return mQuery;
    }

    public void setQueryText(String query) {
        mQuery = query;
    }

    /**
     * Listener for user actions on search view.
     */
    private final class SearchModeChangeListener implements View.OnClickListener,
            SearchView.OnCloseListener {
        @Override
        public void onClick(View v) {
            mSearchMode = true;
        }

        @Override
        public boolean onClose() {
            mSearchMode = false;
            return false;
        }
    }
    /* SPRD Bug 596057 Handle the KEYCODE_SEARCH key event by the onKeyUp method @{ */
    public void onKeyUp(int keyCode, KeyEvent event){
        if(null != mSearch && null != mSearchView){
            mSearchMode = true;
            mSearchView.requestFocus();
            mSearchView.setIconified(false);
        }
    }
    /* @} */
}
