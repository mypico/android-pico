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

import android.text.TextUtils;

import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.PageList;
import com.example.android.wizardpager.wizard.model.ReviewItem;
import com.example.android.wizardpager.wizard.model.SingleFixedChoicePage;

/**
 * Page for selecting which backup provider (e.g. SD card, Dropbox, etc.) to restore a backup
 * from. The page is set up by the {@link SetupWizardModel} and {@link RestoreBackupWizardModel}
 * classes.
 * <p>
 * The page extends the {@link SingleFixedChoicePage} class to ensure the user can only select
 * one of the options.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see SetupWizardModel
 * @see RestoreBackupWizardModel
 */
public class SelectBackupProviderPage extends SingleFixedChoicePage implements SubmitPage {

    public static final String BACKUP_PROVIDER_KEY = "BackupProvider";

    private PageList chosenBackupBranch = new PageList();
    private PageList chosenNoBackupBranch = new PageList();
    private String noBackupOption = "";

    /**
     * Constructor for creating the page.
     *
     * @param callbacks Used to manage the page.
     * @param title     The page title.
     */
    public SelectBackupProviderPage(final ModelCallbacks callbacks, final String title) {
        super(callbacks, title);
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
        dest.add(new ReviewItem(getTitle(), mData.getString(BACKUP_PROVIDER_KEY), getKey()));
    }

    /**
     * Set the backup provider choice.
     * <p>
     * This gets called from {@link SetupActivity} once the backup provider fragment has been
     * created and fires its {@code onConfigureBackupSuccess} listener.
     *
     * @param provider The backup successfully configured provider.
     * @return This object
     */
    public SelectBackupProviderPage setBackupProvider(String provider) {
        mData.putString(BACKUP_PROVIDER_KEY, provider);
        // do a "regular" notifyDataChanged, since a new option wasn't actually selected, but the
        // completion status will have done, and we need to avoid resetting BACKUP_PROVIDER_KEY
        super.notifyDataChanged();
        return this;
    }

    @Override
    public boolean isCompleted() {
        return !TextUtils.isEmpty(mData.getString(BACKUP_PROVIDER_KEY));
    }

    @Override
    public boolean isReadyToSubmit() {
        return !TextUtils.isEmpty(mData.getString(SIMPLE_DATA_KEY));
    }

    /**
     * Set which pages follow if a backup provider option is chosen, as opposed to "No backup".
     *
     * @param pages The pages that will be shown only if a proper backup option is chosen.
     */
    public SelectBackupProviderPage setBackupBranch(Page... pages) {
        chosenBackupBranch = new PageList(pages);
        return this;
    }

    /**
     * Set what pages follow if the "No backup" option is chosen.
     *
     * @param item  The name of the "No backup" item in the list of choices.
     * @param pages The pages that will be shown only if the "no backup" option is chosen.
     */
    public SelectBackupProviderPage setNoBackupBranch(String item, Page... pages) {
        noBackupOption = item;
        chosenNoBackupBranch = new PageList(pages);
        return this;
    }

    @Override
    public Page findByKey(String key) {
        if (getKey().equals(key))
            return this;
        // see if it's in either of the branches
        Page result = chosenBackupBranch.findByKey(key);
        if (result != null)
            return result;
        return chosenNoBackupBranch.findByKey(key);
    }

    @Override
    public void notifyDataChanged() {
        mCallbacks.onPageTreeChanged();
        // if it's changed they have to press the Next button again to reconfigure it
        mData.remove(BACKUP_PROVIDER_KEY);
        super.notifyDataChanged();
    }

    @Override
    public void flattenCurrentPageSequence(ArrayList<Page> dest) {
        super.flattenCurrentPageSequence(dest);
        if (noBackupOption.equals(mData.getString(SIMPLE_DATA_KEY))) {
            chosenNoBackupBranch.flattenCurrentPageSequence(dest);
        } else {
            chosenBackupBranch.flattenCurrentPageSequence(dest);
        }
    }

}