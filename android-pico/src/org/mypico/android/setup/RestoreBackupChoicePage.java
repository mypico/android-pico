/*
 * (C) Copyright Cambridge Authentication Ltd, 2017
 *
 * This file is part of android-pico.
 *
 * android-pico is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * android-pico is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with android-pico. If not, see
 * <http://www.gnu.org/licenses/>.
 */


package org.mypico.android.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.SingleFixedChoicePage;
import com.example.android.wizardpager.wizard.ui.PageFragmentCallbacks;

import org.mypico.android.R;

/**
 * When the Pico app is first run the user is walked through the set up process using a
 * wizard.
 * <p>
 * This class provides options for selecting where the backup should be restored from (e.g.
 * SD card, Dropbox, etc.).
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see R.layout#fragment_page
 */
public final class RestoreBackupChoicePage extends SingleFixedChoicePage implements SubmitPage {

    final static String RESTORED_KEY = "Restored";

    public static final class RestoreBackupChoicePageFragment extends ListFragment {

        private static final String ARG_KEY = "key";

        private PageFragmentCallbacks mCallbacks;
        private List<String> mChoices;
        private String mKey;
        private Page mPage;

        public static RestoreBackupChoicePageFragment create(String key) {
            Bundle args = new Bundle();
            args.putString(ARG_KEY, key);

            RestoreBackupChoicePageFragment fragment = new RestoreBackupChoicePageFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            mKey = args.getString(ARG_KEY);
            mPage = mCallbacks.onGetPage(mKey);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_page, container, false);
            ((TextView) rootView.findViewById(android.R.id.title)).setText(mPage.getTitle());

            SingleFixedChoicePage fixedChoicePage = (SingleFixedChoicePage) mPage;
            mChoices = new ArrayList<String>();
            for (int i = 0; i < fixedChoicePage.getOptionCount(); i++) {
                mChoices.add(fixedChoicePage.getOptionAt(i));
            }

            final ListView listView = (ListView) rootView.findViewById(android.R.id.list);
            setListAdapter(new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_single_choice,
                android.R.id.text1,
                mChoices));
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

            // Pre-select currently selected item.
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    String selection = mPage.getData().getString(Page.SIMPLE_DATA_KEY);
                    for (int i = 0; i < mChoices.size(); i++) {
                        if (mChoices.get(i).equals(selection)) {
                            listView.setItemChecked(i, true);
                            break;
                        }
                    }
                }
            });

            return rootView;
        }


        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            SingleFixedChoicePage fixedChoicePage = (SingleFixedChoicePage) mPage;
            mChoices = new ArrayList<String>();
            for (int i = 0; i < fixedChoicePage.getOptionCount(); i++) {
                mChoices.add(fixedChoicePage.getOptionAt(i));
            }
            mPage.notifyDataChanged();
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
        public void onListItemClick(ListView l, View v, int position, long id) {
            mPage.getData().putString(Page.SIMPLE_DATA_KEY,
                getListAdapter().getItem(position).toString());
            mPage.notifyDataChanged();
        }
    }

    /**
     * Constructor for creating the page.
     *
     * @param callbacks Used to manage the page.
     * @param title     The page title.
     */
    public RestoreBackupChoicePage(final ModelCallbacks callbacks, final String title) {
        super(callbacks, title);
    }

    @Override
    public Fragment createFragment() {
        return RestoreBackupChoicePageFragment.create(getKey());
    }

    @Override
    public boolean isReadyToSubmit() {
        return !TextUtils.isEmpty(mData.getString(SIMPLE_DATA_KEY));
    }

    public SingleFixedChoicePage setValue(final String key, final String value) {
        mData.putString(key, value);
        return this;
    }

    @Override
    public SingleFixedChoicePage setChoices(String... choices) {
        mChoices = new ArrayList<String>(Arrays.asList(choices));
        return this;
    }

    @Override
    public boolean isCompleted() {
        return !TextUtils.isEmpty(mData.getString(RESTORED_KEY));
    }
}