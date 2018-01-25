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

import android.support.v4.app.Fragment;

import org.mypico.android.backup.SharedPreferencesBackupKey;
import org.mypico.android.core.PicoApplication;
import org.mypico.android.util.PgpWordListByteString;
import org.mypico.jpico.backup.BackupKey;

/**
 * A page offering the user a number of mutually exclusive choices.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class PgpWordListOutputPage extends PgpWordListPage {

    // Backup not restored, therefore, generate and display a new backup key
    // Note that the backupKey is persists to the SharedPreferences
    // on creation
    final BackupKey backupKey = SharedPreferencesBackupKey.newRandomInstance();

    // Display the user secret as a set of words from the PGP wordlist   
    final String pgpWords =
        new PgpWordListByteString(PicoApplication.getContext()).toWords(backupKey.getUserSecret());

    /**
     * Constructor for creating the page.
     *
     * @param callbacks Used to manage the page.
     * @param title     The page title.
     */
    public PgpWordListOutputPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    @Override
    public Fragment createFragment() {
        return PgpWordListOutputFragment.newInstance(getKey(), pgpWords.trim().split("\\s"));
    }

    @Override
    public boolean isCompleted() {
        return true;
    }

}