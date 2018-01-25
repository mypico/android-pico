package org.mypico.android.delegate;

import java.util.Calendar;
import java.util.Date;

import org.mypico.android.R;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.CalendarView.OnDateChangeListener;
import android.widget.TextView;

import com.example.android.wizardpager.wizard.ui.PageFragmentCallbacks;

/**
 * Provide a calendar fragment for the Android Wizard Pager
 * See https://plus.google.com/+RomanNurik/posts/6cVymZvn3f4
 * for more details.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 */
public class CalendarFragment extends Fragment {
    private static final String ARG_KEY = "key";

    private PageFragmentCallbacks mCallbacks;
    private String mKey;
    private CalendarPage mPage;
    private CalendarView mCalendarView;

    public static CalendarFragment create(String key) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);

        CalendarFragment fragment = new CalendarFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Default constructor
     */
    public CalendarFragment() {
        // Do nothing
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mKey = args.getString(ARG_KEY);
        mPage = (CalendarPage) mCallbacks.onGetPage(mKey);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_page_calendar, container, false);
        ((TextView) rootView.findViewById(android.R.id.title)).setText(mPage.getTitle());

        mCalendarView = ((CalendarView) rootView.findViewById(R.id.wizard_calendarView));
        setValue(new Date());

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (!(activity instanceof PageFragmentCallbacks)) {
            throw new ClassCastException("Activity must implement PageFragmentCallbacks");
        }

        mCallbacks = (PageFragmentCallbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mCalendarView.setOnDateChangeListener(new OnDateChangeListener() {

            @Override
            public void onSelectedDayChange(CalendarView view, int year, int month,
                                            int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.clear();
                calendar.set(year, month, dayOfMonth);
                mPage.getData().putLong(CalendarPage.DATE_DATA_KEY, calendar.getTimeInMillis());
            }
        });
//        mCalendarView.addTextChangedListener(new TextWatcher() {
//            @Override
//            public void beforeTextChanged(CharSequence charSequence, int i, int i1,
//                    int i2) {
//            }
//
//            @Override
//            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//            }
//
//            @Override
//            public void afterTextChanged(Editable editable) {
//                mPage.getData().putString(CustomerInfoPage.NAME_DATA_KEY,
//                        (editable != null) ? editable.toString() : null);
//                mPage.notifyDataChanged();
//            }
//        });
//
//        mEmailView.addTextChangedListener(new TextWatcher() {
//            @Override
//            public void beforeTextChanged(CharSequence charSequence, int i, int i1,
//                    int i2) {
//            }
//
//            @Override
//            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//            }
//
//            @Override
//            public void afterTextChanged(Editable editable) {
//                mPage.getData().putString(CustomerInfoPage.EMAIL_DATA_KEY,
//                        (editable != null) ? editable.toString() : null);
//                mPage.notifyDataChanged();
//            }
//        });
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
    }

    /**
     * Used to set the initial value of the calendar view
     *
     * @param date to set the calendar to
     * @return the calendar page
     */
    public CalendarPage setValue(Date date) {
        mCalendarView.setDate(date.getTime());
        mPage.getData().putLong(CalendarPage.DATE_DATA_KEY, date.getTime());
        mCalendarView.refreshDrawableState();

        return mPage;
    }

}
