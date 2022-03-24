package com.mqbcoding.stats;

import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import com.google.android.apps.auto.sdk.StatusBarController;


public abstract class CarFragment extends Fragment {
    private String mTitle;

    public CarFragment() {
        super();
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public void setTitle(@StringRes int resId) {
        this.mTitle = getContext().getString(resId);
    }

    abstract protected void setupStatusBar(StatusBarController sc);

}