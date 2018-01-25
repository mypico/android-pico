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


/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mypico.android.setup;

import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.ReviewItem;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import org.mypico.android.R;

/**
 * When the Pico app is first run the user is walked through the set up process using a
 * wizard.
 * <p>
 * This class provides a description of the backup process. It has no functionality other than
 * to display a message and allow the user to move to the next page.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see R.layout#fragment_backup_pico
 */
public final class BackupDescriptionPage extends Page {

    public static class BackupDescriptionPageFragment extends CustomPageFragment {

        public static Fragment create(final String key) {
            final BackupDescriptionPageFragment fragment = new BackupDescriptionPageFragment();
            final Bundle args = new Bundle();
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                                 final Bundle savedInstanceState) {

            final View view = inflater.inflate(R.layout.fragment_backup_pico, container, false);
            ((TextView) view.findViewById(android.R.id.title)).setText(mPage.getTitle());
            return view;
        }
    }

    /**
     * Constructor for creating the page.
     *
     * @param callbacks Used to manage the page.
     * @param title     The page title.
     */
    public BackupDescriptionPage(final ModelCallbacks callbacks, final String title) {
        super(callbacks, title);
    }

    @Override
    public Fragment createFragment() {
        return BackupDescriptionPageFragment.create(getKey());
    }

    @Override
    public void getReviewItems(final ArrayList<ReviewItem> dest) {
        // No items to review
    }

    @Override
    public boolean isCompleted() {
        // The user can advance to the next page in the Wizard immediately
        return true;
    }
}