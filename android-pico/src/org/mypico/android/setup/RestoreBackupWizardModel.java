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

import android.content.Context;
import android.content.res.Resources;

import com.example.android.wizardpager.wizard.model.AbstractWizardModel;
import com.example.android.wizardpager.wizard.model.PageList;

import static org.mypico.android.R.string.activity_setup__restore_from__title;
import static org.mypico.android.R.array.activity_setup__restore_from__choices;
import static org.mypico.android.R.string.activity_setup__restore_backup__title;
import static org.mypico.android.R.array.activity_setup__restore_backup__choices;
import static org.mypico.android.R.string.activity_setup__restore_recovery_words__title;
import static org.mypico.android.R.string.fragment_pgp_word_list_input__title;

/**
 * Pico creates backups that can be stored in various places, such as on an SD card or cloud service
 * like Dropbox. When the user wishes to restore a backup they are walked through the process using
 * a wizard.
 * <p>
 * This class defines the pages that make up the backup restore wizard.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public final class RestoreBackupWizardModel extends AbstractWizardModel {

    /**
     * Constructor for creating the wizard.
     *
     * @param context The UI context.
     */
    public RestoreBackupWizardModel(final Context context) {
        super(context);
    }

    @Override
    protected PageList onNewRootPageList() {
        final Resources resources = mContext.getResources();
        return new PageList(
            new SelectBackupProviderPage(this, resources.getString(activity_setup__restore_from__title))
                .setChoices(resources.getStringArray(activity_setup__restore_from__choices))
                .setRequired(true),
            new RestoreBackupChoicePage(this, resources.getString(activity_setup__restore_backup__title))
                .setChoices(resources.getStringArray(activity_setup__restore_backup__choices))
                .setRequired(true),
            new RestoreRecoveryWordsPage(this, resources.getString(activity_setup__restore_recovery_words__title)),
            new PgpWordListInputPage(this, resources.getString(fragment_pgp_word_list_input__title))
                .setRequired(true));
    }
}