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

import com.example.android.wizardpager.wizard.model.BranchPage;
import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.ReviewItem;
import com.example.android.wizardpager.wizard.model.SingleFixedChoicePage;
import com.example.android.wizardpager.wizard.ui.SingleChoiceFragment;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.mypico.android.R;

import java.util.ArrayList;

/**
 * When the Pico app is first run the user is walked through the set up process using a
 * wizard.
 * <p>
 * This class provides the first page of the wizard that introduces the process and asks whether th
 * user is new (in which case we should follow the setup wizard) or returning (in which case we
 * should follow the restore wizard).
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see R.layout#fragment_welcome
 */
public final class WelcomePage extends BranchPage {

    String mReviewTitle;

    /**
     * Fragment with the welcome page's content, based on the {@code SingleChoiceFragment} in
     * android-wizardpager modified to use a custom layout.
     *
     * @see SingleChoiceFragment
     */
    public static final class WelcomePageFragment extends CustomPageFragment {

        protected ArrayList<String> mChoices;
        protected ListAdapter mAdapter;

        public static WelcomePageFragment create(String key) {
            final WelcomePageFragment fragment = new WelcomePageFragment();
            final Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final SingleFixedChoicePage fixedChoicePage = (SingleFixedChoicePage) mPage;
            mChoices = new ArrayList<>();
            for (int i = 0; i < fixedChoicePage.getOptionCount(); i++) {
                mChoices.add(fixedChoicePage.getOptionAt(i));
            }
        }

        @Override
        public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                                 final Bundle savedInstanceState) {
            final View view = inflater.inflate(R.layout.fragment_welcome, container, false);
            ((TextView) view.findViewById(android.R.id.title)).setText(mPage.getTitle());

            mAdapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_single_choice,
                android.R.id.text1,
                mChoices);

            final ListView listView = (ListView) view.findViewById(android.R.id.list);
            listView.setAdapter(mAdapter);
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    onListItemClick(position);
                }
            });

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

            return view;
        }

        protected void onListItemClick(int position) {
            mPage.getData().putString(Page.SIMPLE_DATA_KEY, mAdapter.getItem(position).toString());
            mPage.notifyDataChanged();
        }

    }

    /**
     * Constructor for creating the page.
     *
     * @param callbacks Used to manage the page.
     * @param title     The page title.
     */
    public WelcomePage(final ModelCallbacks callbacks, final String title) {
        super(callbacks, title);
    }

    @Override
    public Fragment createFragment() {
        return WelcomePageFragment.create(getKey());
    }

    public WelcomePage setReviewTitle(String title) {
        mReviewTitle = title;
        return this;
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
        dest.add(new ReviewItem(mReviewTitle, mData.getString(SIMPLE_DATA_KEY), getKey()));
    }

}