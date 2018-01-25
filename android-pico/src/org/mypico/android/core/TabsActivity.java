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


package org.mypico.android.core;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

/**
 * Abstract base class for making activities with multiple "swipe-able" tabs. This class handles
 * the creation of required ViewPager and FragmentPagerAdapter, and makes the changes to the action
 * bar required for the tab navigation. A subclass just has to implement {@link #getFragments()}
 * method to supply the list of fragments and their tab titles.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 */
public abstract class TabsActivity extends FragmentActivity {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(TabsActivity.class.getSimpleName());
    public static final String ACTIVE_TAB =
        TabsActivity.class.getCanonicalName() + "activeTab";

    private static class TabsActivityPagerAdapter extends FragmentPagerAdapter {

        private final int count;
        //private final ArrayList<Fragment> fragments;
        private final ArrayList<Class<? extends Fragment>> fcs;
        private final ArrayList<CharSequence> titles;

        public TabsActivityPagerAdapter(
            FragmentManager fm,
            List<Class<? extends Fragment>> fragmentClasses,
            List<CharSequence> titles) {
            super(fm);

            if (fragmentClasses.size() != titles.size()) {
                throw new IllegalArgumentException(
                    "numbers of fragments and titles must be equal");
            }
            count = fragmentClasses.size();

            //this.fragments = new ArrayList<Fragment>(count);
            this.fcs = new ArrayList<Class<? extends Fragment>>(count);
            this.titles = new ArrayList<CharSequence>(count);

            // Copy into a new linked list to ensure consecutive indices starting from zero.
            this.fcs.addAll(fragmentClasses);
            this.titles.addAll(titles);
        }

        @Override
        public Fragment getItem(int position) {
            if (position < 0 || position >= count) {
                // If position is out of bounds, just use first tab
                position = 0;
            }

            // Instantiate a new instance of appropriate fragment
            try {
                return fcs.get(position).newInstance();
            } catch (InstantiationException e) {
                LOGGER.warn("fragment at position " + position + " cannot be instantiated", e);
                return null;
            } catch (IllegalAccessException e) {
                LOGGER.warn("fragment at position " + position + " cannot be instantiated", e);
                return null;
            }
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position > 0 && position < count) {
                return titles.get(position);
            } else {
                return titles.get(0);
            }
        }
    }

    private TabsActivityPagerAdapter pagerAdapter;
    private ViewPager pager;

    /**
     * Get the list of fragments to display in this activity. The fragments and tabs will appear in
     * the same order as they appear in the list.
     * <p>
     * <p>This method will be called during the {@link #onCreate(Bundle)} of {@link TabsActivity}.
     *
     * @return list of fragments to display.
     */
    protected abstract List<Class<? extends Fragment>> getFragments();

    /**
     * Get the list of titles to use for the fragments displayed in this activity. The titles will
     * be used in the order they appear in the returned list, so the first titles will be used for
     * the first fragment and so on.
     * <p>
     * <p>This method will be called during the {@link #onCreate(Bundle)} of {@link TabsActivity}.
     *
     * @return list of titles for fragments.
     */
    protected abstract List<CharSequence> getTitles();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tabs);

        // Get fragments and titles for the tabs from the subclass
        final List<Class<? extends Fragment>> fragments = getFragments();
        List<CharSequence> titles = getTitles();

        if (fragments != null && fragments.size() > 0) {
            // Got some fragments from the subclass
            LOGGER.debug("got {} fragments", fragments.size());

            // Ensure number of titles is equal
            if (titles == null) {
                titles = new ArrayList<CharSequence>(fragments.size());
            }
            if (titles.size() > fragments.size()) {
                LOGGER.warn("too many titles ({} for {} tabs)", titles.size(), fragments.size());
                // Drop the end of the titles list
                titles = titles.subList(0, fragments.size());
            } else if (titles.size() < fragments.size()) {
                LOGGER.warn("not enough titles ({} for {} tabs)", titles.size(), fragments.size());
                while (titles.size() < fragments.size()) {
                    // Pad the titles list with "Tab X" items
                    titles.add("Tab " + (titles.size() + 1));
                }
            }

            // Set the navigation mode of the action bar so that it can accommodate the tabs
            final ActionBar actionBar = getActionBar();
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            // Set up the view pager by adding the custom pager adapter defined above
            pager = (ViewPager) findViewById(R.id.tabs_activity__pager);
            pagerAdapter = new TabsActivityPagerAdapter(
                getSupportFragmentManager(), fragments, titles);
            pager.setAdapter(pagerAdapter);


            // Add a listener to the view pager so that When a new fragment (page) is selected by
            // swiping the pager, the tabs in the action bar are updated accordingly.
            pager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    actionBar.setSelectedNavigationItem(position);
                }
            });

            // Similarly, add a listener to each tab in the each tab in the action bar so that when
            // they are selected the view pager is updated.
            ActionBar.TabListener tabListener = new ActionBar.TabListener() {
                @Override
                public void onTabSelected(Tab tab, FragmentTransaction tr) {
                    pager.setCurrentItem(tab.getPosition());
                }

                @Override
                public void onTabUnselected(Tab tab, FragmentTransaction tr) {
                }

                @Override
                public void onTabReselected(Tab tab, FragmentTransaction tr) {
                }
            };

            // This is where the action bar tabs are actually created and added
            for (int i = 0; i < pagerAdapter.getCount(); i++) {
                actionBar.addTab(
                    actionBar.newTab()
                        .setText(pagerAdapter.getPageTitle(i))
                        .setTabListener(tabListener));
            }

            // Set the active tab. getInt on the saved state return 0 if the key was not found and
            // there was no previously saved active position, but this is a fine default anyway.
            if (savedInstanceState != null) {
                pager.setCurrentItem(savedInstanceState.getInt(ACTIVE_TAB));
            }

            Intent intent = getIntent();
            int activeTab = intent.getIntExtra(ACTIVE_TAB, -1);
            if (activeTab >= 0 && activeTab <= pagerAdapter.getCount()) {
                pager.setCurrentItem(activeTab);
            }

        } else {
            // Subclass did not return any fragments
            LOGGER.warn("no fragments for tabs");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save the index of the active tab.
        outState.putInt(ACTIVE_TAB, pager.getCurrentItem());
    }
}
