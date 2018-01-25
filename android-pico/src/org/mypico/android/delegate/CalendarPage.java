package org.mypico.android.delegate;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import android.support.v4.app.Fragment;
import android.text.TextUtils;

import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.ReviewItem;

/**
 * Used to create a calendar page for the Android Wizard Pager
 * See https://plus.google.com/+RomanNurik/posts/6cVymZvn3f4
 * for more details.
 *
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class CalendarPage extends Page {
    public static final String DATE_DATA_KEY = "date";

    /**
     * Constructor for creating the calendar page.
     *
     * @param callbacks Called when data on the page is changed.
     * @param title     The title of the calendar page.
     */
    protected CalendarPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    @Override
    public Fragment createFragment() {
        return CalendarFragment.create(getKey());
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.setTimeInMillis(mData.getLong(DATE_DATA_KEY));
        DateFormat format = SimpleDateFormat.getDateInstance();
        format.setTimeZone(TimeZone.getDefault());

        dest.add(new ReviewItem("Custom date restriction", format.format(calendar.getTime()), getKey(), -1));
    }

    @Override
    public boolean isCompleted() {
        return !TextUtils.isEmpty(mData.getString(DATE_DATA_KEY));
    }
}
