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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import com.example.android.wizardpager.wizard.model.AbstractWizardModel;
import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.ui.PageFragmentCallbacks;
import com.example.android.wizardpager.wizard.ui.ReviewFragment;
import com.example.android.wizardpager.wizard.ui.StepPagerStrip;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import org.mypico.android.backup.BackupFactory;
import org.mypico.android.util.InvalidWordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;

import static org.mypico.android.R.array.activity_setup__restore_from__choices;
import static org.mypico.android.R.array.activity_setup__backup_to__choices;
import static org.mypico.android.R.array.activity_setup__restore_backup__choices;

import org.mypico.android.util.PgpWordListByteString;
import org.mypico.android.backup.BackupProviderFragment;
import org.mypico.android.backup.BackupProviderFragment.RestoreOption;
import org.mypico.android.backup.IBackupProvider;
import org.mypico.android.backup.OnConfigureBackupListener;
import org.mypico.android.backup.OnQueryBackupListener;
import org.mypico.android.backup.OnRestoreBackupListener;
import org.mypico.android.backup.IBackupProvider.BackupType;

/**
 * SetupActivity provides Wizards for setting up Pico and restoring a backup.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class SetupActivity extends FragmentActivity implements
    PageFragmentCallbacks, ReviewFragment.Callbacks, ModelCallbacks,
    OnConfigureBackupListener, OnRestoreBackupListener, OnQueryBackupListener {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(SetupActivity.class.getSimpleName());
    private static final String ACTIVITY_SETUP_FRAGMENT_TAG =
        "ActivitySetupDialogFragment";
    private static final String SETUP_ACTIVITY_MODEL = "SetupModel";

    public final static int SETUP_RESULT_CODE = 0x01;
    public final static int RESTORE_BACKUP_RESULT_CODE = 0x02;

    protected AbstractWizardModel mWizardModel;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;
    private boolean mEditingAfterReview;
    private boolean mConsumePageSelectedEvent;
    private Button mNextButton;
    private Button mPrevButton;
    private List<Page> mCurrentPageSequence;
    private StepPagerStrip mStepPagerStrip;
    private IBackupProvider backupProvider;

    /**
     * Call to move to the next wizard page.
     */
    private void nextPage() {
        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
    }

    /**
     * Call to move to the previouos wizard page.
     */
    private void previousPage() {
        mPager.setCurrentItem(mPager.getCurrentItem() - 1);
    }

    /**
     * Call to move to the review page. This is the final page of the wizard that allows the user
     * to review their choices.
     */
    private void reviewPage() {
        mPager.setCurrentItem(mPagerAdapter.getCount() - 1);
    }

    public static final class BackupFailureDialogFragment extends DialogFragment {
        private static final String PROGRESS_FRAGMENT_MESSAGE = "message";

        public static BackupFailureDialogFragment newInstance(final int message) {
            final BackupFailureDialogFragment frag = new BackupFailureDialogFragment();
            // Supply index input as an argument.
            final Bundle args = new Bundle();
            args.putInt(PROGRESS_FRAGMENT_MESSAGE, message);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                .setMessage(getArguments().getInt(PROGRESS_FRAGMENT_MESSAGE))
                .setNegativeButton(R.string.activity_setup__exit_confirm,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(
                            final DialogInterface dialog,
                            final int which) {
                            Activity activity = getActivity();
                            if (activity != null) {
                                activity.setResult(RESULT_CANCELED);
                                activity.finish();
                            }
                        }
                    })
                .setPositiveButton(R.string.activity_setup__exit_cancel, null)
                .create();
        }
    }

    /**
     * Create the wizard's pages.
     *
     * @return The wizard model that describes which wizard pages will be shown by this activity.
     */
    AbstractWizardModel createWizardModel() {
        return new SetupWizardModel(this);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // Restore value of members from saved state
        }

        setContentView(R.layout.activity_setup);

        LOGGER.debug("onCreate setupActivity");

        // Initialise the SetupActivity's WizardModel
        mWizardModel = createWizardModel();

        final android.app.Fragment fragment = getFragmentManager()
            .findFragmentByTag(BackupProviderFragment.TAG);
        if (fragment != null) {
            LOGGER.debug("BackupProvider fragment attached");
            backupProvider = (IBackupProvider) fragment;

            setRestoreOptions(mWizardModel.findByKey(
                getString(R.string.activity_setup__new_user__returning_user_restore) + ":" +
                    getString(R.string.activity_setup__restore_from__title)));
            setRestoreOptions(mWizardModel.findByKey(
                getString(R.string.activity_setup__restore_from__title)));
        }

        if (savedInstanceState != null) {
            mWizardModel.load(savedInstanceState.getBundle(SETUP_ACTIVITY_MODEL));
        }
        mWizardModel.registerListener(this);

        mPagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
        mStepPagerStrip = (StepPagerStrip) findViewById(R.id.strip);
        mStepPagerStrip.setOnPageSelectedListener(new StepPagerStrip.OnPageSelectedListener() {

            @Override
            public void onPageStripSelected(int position) {
                position = Math.min(mPagerAdapter.getCount() - 1, position);
                if (mPager.getCurrentItem() != position) {
                    mPager.setCurrentItem(position);
                }
            }
        });

        mNextButton = (Button) findViewById(R.id.next_button);
        mPrevButton = (Button) findViewById(R.id.prev_button);

        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {

            @Override
            public void onPageSelected(final int position) {
                mStepPagerStrip.setCurrentPage(position);

                if (mConsumePageSelectedEvent) {
                    mConsumePageSelectedEvent = false;
                    return;
                }

                mEditingAfterReview = false;
                updateBottomBar();

                // hide the keyboard when the user leaves the recovery words entry page
                final View wordEntryGrid = findViewById(R.id.pgpwordlistGridView);
                if (wordEntryGrid != null) {
                    final InputMethodManager imm = (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        // be careful: the "Review" page has a position that is out of range
                        final Page page = position < mCurrentPageSequence.size() ?
                            mCurrentPageSequence.get(position) : null;
                        if (page instanceof PgpWordListInputPage) {
                            // show the keyboard if there's a focussed textbox
                            final View focus = getCurrentFocus();
                            if (focus instanceof MultiAutoCompleteTextView) {
                                imm.showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT);
                            }
                        } else {
                            // hide the keyboard
                            imm.hideSoftInputFromWindow(wordEntryGrid.getWindowToken(), 0);
                        }
                    }
                }
            }
        });

        mNextButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View view) {
                if (mPager.getCurrentItem() == mCurrentPageSequence.size()) {
                    // Setup was successfully completed, return the OK to the calling Activity
                    setResult(RESULT_OK);
                    finish();

                } else {
                    final Page page = mCurrentPageSequence.get(mPager.getCurrentItem());
                    if (page instanceof SelectBackupProviderPage) {
                        // Configure the user's selected backup provider
                        LOGGER.debug("Configuring the user's backup provider");
                        final String backupChoice = page.getData().getString(Page.SIMPLE_DATA_KEY);
                        final String[] backupToChoices = getResources().getStringArray(activity_setup__backup_to__choices);
                        final String[] restoreFromChoices = getResources().getStringArray(activity_setup__restore_from__choices);
                        final boolean isRestoring = page.getTitle().equals(getResources().getString(R.string.activity_setup__restore_from__title));

                        if (backupChoice.equals(backupToChoices[0]) || backupChoice.equals(restoreFromChoices[0])) {
                            // Dropbox backup
                            if (BackupFactory.restoreBackupType() == BackupType.DROPBOX && mEditingAfterReview) {
                                // Return to the review page
                                reviewPage();
                            } else {
                                LOGGER.debug("Configuring the user's Dropbox account");
                                BackupFactory.newBackup(BackupType.DROPBOX, SetupActivity.this);
                            }

                        } else if (backupChoice.equals(backupToChoices[1]) || backupChoice.equals(restoreFromChoices[1])) {
                            // Google Drive backup
                            if (BackupFactory.restoreBackupType() == BackupType.GOOGLEDRIVE && mEditingAfterReview) {
                                // Return to the review page
                                reviewPage();
                            } else {
                                LOGGER.debug("Configuring the user's Google Drive account");
                                BackupFactory.newBackup(BackupType.GOOGLEDRIVE, SetupActivity.this);
                            }

                        } else if (backupChoice.equals(backupToChoices[2]) || backupChoice.equals(restoreFromChoices[2])) {
                            // Microsoft OneDrive backup
                            if (BackupFactory.restoreBackupType() == BackupType.ONEDRIVE && mEditingAfterReview) {
                                // Return to the review page
                                reviewPage();
                            } else {
                                LOGGER.debug("Configuring the user's Microsoft OneDrive account");
                                BackupFactory.newBackup(BackupType.ONEDRIVE, SetupActivity.this);
                            }

                        } else if (backupChoice.equals(backupToChoices[3]) || backupChoice.equals(restoreFromChoices[3])) {
                            // Local SD card backup
                            if (BackupFactory.restoreBackupType() == BackupType.SDCARD && mEditingAfterReview) {
                                // Return to the review page
                                reviewPage();
                            } else if (isRestoring) {
                                LOGGER.debug("Configuring the user's SD Card");
                                BackupFactory.newBackup(BackupType.SDCARD, SetupActivity.this);
                            } else {
                                // ask them to confirm they really want to backup to SD card
                                new AlertDialog.Builder(SetupActivity.this)
                                    .setTitle(R.string.activity_setup__backup_to__sdcard_warning_title)
                                    .setMessage(R.string.activity_setup__backup_to__sdcard_warning_text)
                                    .setPositiveButton(R.string.activity_setup__backup_to__warning_continue, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            LOGGER.debug("Configuring the user's SD Card");
                                            BackupFactory.newBackup(BackupType.SDCARD, SetupActivity.this);
                                        }
                                    })
                                    .setNegativeButton(R.string.activity_setup__backup_to__warning_cancel, null)
                                    .create().show();
                            }

                        } else if (backupChoice.equals(backupToChoices[4])) {
                            // No backup
                            if (BackupFactory.restoreBackupType() == BackupType.NONE && mEditingAfterReview) {
                                // Return to the review page
                                reviewPage();
                            } else {
                                // ask them to confirm they really want no backup
                                new AlertDialog.Builder(SetupActivity.this)
                                    .setTitle(R.string.activity_setup__backup_to__none_warning_title)
                                    .setMessage(R.string.activity_setup__backup_to__none_warning_text)
                                    .setPositiveButton(R.string.activity_setup__backup_to__warning_continue, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            LOGGER.debug("Configuring no backup");
                                            BackupFactory.newBackup(BackupType.NONE, SetupActivity.this);
                                        }
                                    })
                                    .setNegativeButton(R.string.activity_setup__backup_to__warning_cancel, null)
                                    .create().show();
                            }

                        } else {
                            LOGGER.error("Backup provider {} not recognized", backupChoice);
                        }

                    } else if (page instanceof RestoreBackupChoicePage) {
                        final String backupChoice = page.getData().getString(Page.SIMPLE_DATA_KEY);
                        final String[] restoreChoices = getResources().getStringArray(activity_setup__restore_backup__choices);
                        if (backupChoice.equals(restoreChoices[0])) {
                            // Restore the latest backup
                            backupProvider.restoreLatestBackup();
                        } else if (backupChoice.equals(restoreChoices[1])) {
                            // Restore a user selected backup
                            backupProvider.restoreBackup();
                        } else {
                            LOGGER.error("Restore option {} not recognized", backupChoice);
                        }

                    } else if (page instanceof PgpWordListInputPage) {
                        final String pgpWords = page.getData().getString(Page.SIMPLE_DATA_KEY);
                        final byte[] userSecret;
                        try {
                            userSecret = new PgpWordListByteString(SetupActivity.this).fromWords(pgpWords);
                            backupProvider.decryptRestoredBackup(userSecret);
                        } catch (InvalidWordException e) {
                            onRestoreBackupFailure();
                        }

                    } else {
                        if (mEditingAfterReview) {
                            // Return to the review page
                            reviewPage();
                        } else {
                            // Advance to the next page
                            nextPage();
                        }
                    }
                }
            }
        });

        mPrevButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(final View view) {

                // Return the Wizard to the previous page
                previousPage();
            }
        });

        onPageTreeChanged();
        updateBottomBar();
    }

    /**
     * Set the optionos for the backup restore page.
     *
     * @param page The backup restore page.
     */
    private void setRestoreOptions(final Page page) {
        if (page instanceof RestoreBackupChoicePage) {
            final RestoreBackupChoicePage rbcPage = (RestoreBackupChoicePage) page;

            // Set the restore options
            final List<String> choices = new ArrayList<String>();
            for (final RestoreOption option : backupProvider.getRestoreOptions()) {
                switch (option) {
                    case RESTORE_LATEST:
                        choices.add(getResources().getStringArray(R.array.activity_setup__restore_backup__choices)[0]);
                        break;
                    case RESTORE_USER_SELECTED:
                        choices.add(getResources().getStringArray(R.array.activity_setup__restore_backup__choices)[1]);
                        break;
                    default:
                        break;
                }
            }
            rbcPage.setChoices(choices.toArray(new String[choices.size()]));
            mWizardModel.onPageTreeChanged();
        }
    }

    @Override
    public void onPageTreeChanged() {
        mCurrentPageSequence = mWizardModel.getCurrentPageSequence();
        recalculateCutOffPage();
        mStepPagerStrip.setPageCount(mCurrentPageSequence.size() + 1); // + 1 = review step
        mPagerAdapter.notifyDataSetChanged();
        updateBottomBar();
    }

    /**
     * Update the buttons at the bottom of the wizard. If the user is on the penultimate page, the
     * buttons should indicate that the next page will be the review page. Otherwise it should
     * just show 'Next'.
     */
    private void updateBottomBar() {
        int position = mPager.getCurrentItem();
        if (position == mCurrentPageSequence.size()) {
            // determine whether we're reviewing setting up backup or restoring
            final Page choicePage = (mCurrentPageSequence.size() > 0) ?
                mCurrentPageSequence.get(0) : null;
            final boolean isSettingUp = (choicePage instanceof WelcomePage) &&
                (getString(R.string.activity_setup__new_user__setup).equals(
                    choicePage.getData().getString(Page.SIMPLE_DATA_KEY)));
            // and use this to choose which submit button text to use
            mNextButton.setText(isSettingUp ?
                R.string.activity_setup__setup_completed :
                R.string.activity_setup__restore_completed);
            mNextButton.setBackgroundResource(R.drawable.finish_background);
            mNextButton.setTextAppearance(this, R.style.TextAppearanceFinish);
        } else {
            mNextButton.setText(mEditingAfterReview
                ? R.string.review
                : R.string.next);
            mNextButton.setBackgroundResource(R.drawable.selectable_item_background);
            TypedValue v = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, v, true);
            mNextButton.setTextAppearance(this, v.resourceId);
            final Page page = mCurrentPageSequence.get(position);
            if (page instanceof SubmitPage) {
                mNextButton.setEnabled(((SubmitPage) page).isReadyToSubmit());
            } else {
                mNextButton.setEnabled(position != mPagerAdapter.getCutOffPage());
            }
        }

        mPrevButton.setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        mWizardModel.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(SETUP_ACTIVITY_MODEL, mWizardModel.save());
    }

    @Override
    public AbstractWizardModel onGetModel() {
        return mWizardModel;
    }

    @Override
    public void onEditScreenAfterReview(final String key) {
        for (int i = mCurrentPageSequence.size() - 1; i >= 0; i--) {
            if (mCurrentPageSequence.get(i).getKey().equals(key)) {
                mConsumePageSelectedEvent = true;
                mEditingAfterReview = true;
                mPager.setCurrentItem(i);
                updateBottomBar();
                break;
            }
        }
    }

    @Override
    public void onPageDataChanged(final Page page) {
        if (page.isRequired()) {
            if (recalculateCutOffPage()) {
                mPagerAdapter.notifyDataSetChanged();
            }
            updateBottomBar();
        }
    }

    @Override
    public Page onGetPage(final String key) {
        return mWizardModel.findByKey(key);
    }

    private boolean recalculateCutOffPage() {
        // Cut off the pager adapter at first required page that isn't completed
        int cutOffPage = mCurrentPageSequence.size() + 1;
        for (int i = 0; i < mCurrentPageSequence.size(); i++) {
            Page page = mCurrentPageSequence.get(i);
            if (page.isRequired() && !page.isCompleted()) {
                cutOffPage = i;
                break;
            }
        }

        if (mPagerAdapter.getCutOffPage() != cutOffPage) {
            mPagerAdapter.setCutOffPage(cutOffPage);
            return true;
        }

        return false;
    }

    public final class MyPagerAdapter extends FragmentStatePagerAdapter {
        private int mCutOffPage;
        private Fragment mPrimaryItem;

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(final int i) {
            if (i >= mCurrentPageSequence.size()) {
                return new ReviewFragment();
            }
            return mCurrentPageSequence.get(i).createFragment();
        }

        @Override
        public int getItemPosition(final Object object) {
            // TODO: be smarter about this
            if (object == mPrimaryItem) {
                // Re-use the current fragment (its position never changes)
                return POSITION_UNCHANGED;
            }

            return POSITION_NONE;
        }

        @Override
        public void setPrimaryItem(final ViewGroup container, final int position,
                                   final Object object) {
            super.setPrimaryItem(container, position, object);
            mPrimaryItem = (Fragment) object;
        }

        @Override
        public int getCount() {
            if (mCurrentPageSequence == null) {
                return 0;
            }
            return Math.min(mCutOffPage + 1, mCurrentPageSequence.size() + 1);
        }

        public void setCutOffPage(int cutOffPage) {
            if (cutOffPage < 0) {
                cutOffPage = Integer.MAX_VALUE;
            }
            mCutOffPage = cutOffPage;
        }

        public int getCutOffPage() {
            return mCutOffPage;
        }
    }

    @Override
    public void onConfigureBackupFailure() {
        LOGGER.error("Error configuring backup provider!");

        // Display to the user a dialog informing them that configuring the backup provider failed
        final DialogFragment dg = BackupFailureDialogFragment.newInstance(R.string.activity_setup__configure_backup_failure);
        dg.show(getSupportFragmentManager(), ACTIVITY_SETUP_FRAGMENT_TAG);

        // Detach the BackupProviderFragment
        final android.app.Fragment fragment = getFragmentManager()
            .findFragmentByTag(BackupProviderFragment.TAG);
        if (fragment != null) {
            LOGGER.debug("Detaching BackupProvider fragment");

            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.detach(fragment);
            ft.commit();

            backupProvider = null;
        }
    }

    @Override
    public void onConfigureBackupCancelled() {
        LOGGER.warn("Configuration of backup provider cancelled!");

        Toast.makeText(this, getString(R.string.activity_setup__configure_backup_cancelled),
            Toast.LENGTH_LONG).show();

        // Detach the BackupProviderFragment
        final android.app.Fragment fragment = getFragmentManager()
            .findFragmentByTag(BackupProviderFragment.TAG);
        if (fragment != null) {
            LOGGER.debug("Detaching BackupProvider fragment");

            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.detach(fragment);
            ft.commit();

            backupProvider = null;
        }
    }

    @Override
    public void onConfigureBackupSuccess(final IBackupProvider backupProvider) {
        // Verify the method's preconditions
        checkNotNull(backupProvider);

        LOGGER.info("Backup successfully configured!");

        // Set the configured backup in the shared preferences
        BackupFactory.persistBackupType(backupProvider.getBackupType());

        // Store the configured backup provider
        this.backupProvider = backupProvider;

        // If a backup is being restored query the backup provider
        final Page page = mCurrentPageSequence.get(mPager.getCurrentItem());
        if (page instanceof SelectBackupProviderPage) {
            final SelectBackupProviderPage spbPage = (SelectBackupProviderPage) page;
            if (page == mWizardModel.findByKey(getString(R.string.activity_setup__new_user__setup) + ":" +
                getString(R.string.activity_setup__backup_to__title))) {
                spbPage.setBackupProvider(backupProvider.getBackupType().getProviderName());

                // Advance the Wizard to the next page
                nextPage();
            } else {
                backupProvider.isEmpty();
            }
        }
    }

    @Override
    public void onRestoreBackupStart() {
        LOGGER.trace("Started restoring Pico pairings and services database");
    }

    @Override
    public void onRestoreBackupSuccess() {
        LOGGER.info("Restoring backup successful!");

        // Advance the Wizard to the next page
        nextPage();
    }

    @Override
    public void onRestoreBackupCancelled() {
        LOGGER.info("Restoring backup cancelled!");

        Toast.makeText(this, getString(R.string.activity_setup__restore_backup_cancelled),
            Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRestoreBackupFailure() {
        LOGGER.error("Restoring backup failed!");

        // Display to the user a dialog informing them that restoring the backup failed
        final DialogFragment dg = BackupFailureDialogFragment.newInstance(R.string.activity_setup__restore_backup_failure);
        dg.show(getSupportFragmentManager(), ACTIVITY_SETUP_FRAGMENT_TAG);
    }

    @Override
    public void onQueryBackupIsNotEmpty() {
        LOGGER.debug("Backup provider is not empty");

        // Set the backup provider
        final Page page = mCurrentPageSequence.get(mPager.getCurrentItem());
        if (page instanceof SelectBackupProviderPage) {
            final SelectBackupProviderPage sbpPage = (SelectBackupProviderPage) page;
            sbpPage.setBackupProvider(backupProvider.getBackupType().getProviderName());
        }

        // Set the restore options
        setRestoreOptions(mCurrentPageSequence.get(mPager.getCurrentItem() + 1));

        // Advance the Wizard to the next page
        nextPage();
    }

    @Override
    public void onQueryBackupIsEmpty() {
        LOGGER.debug("Backup provider is empty");

        // Display a dialog to the user informing them that there are no backups to restore
        final DialogFragment dg = BackupFailureDialogFragment.newInstance(R.string.activity_setup__query_backup_is_empty);
        dg.show(getSupportFragmentManager(), ACTIVITY_SETUP_FRAGMENT_TAG);
    }

    @Override
    public void onQueryBackupFailure() {
        LOGGER.error("Error querying the backup provider");

        // Display a dialog to the user informing them that querying their cloud provider has failed
        final DialogFragment dg = BackupFailureDialogFragment.newInstance(R.string.activity_setup__query_backup_failure);
        dg.show(getSupportFragmentManager(), ACTIVITY_SETUP_FRAGMENT_TAG);
    }

    @Override
    public void onRestoreBackupDownloaded() {
        LOGGER.info("Downloading backup successful!");

        final Page page = mCurrentPageSequence.get(mPager.getCurrentItem());
        if (page instanceof RestoreBackupChoicePage) {
            final RestoreBackupChoicePage restoreBackupChoicePage = (RestoreBackupChoicePage) page;
            restoreBackupChoicePage.setValue(RestoreBackupChoicePage.RESTORED_KEY, "Success");
            restoreBackupChoicePage.notifyDataChanged();
        }

        // Advance the Wizard to the next page
        nextPage();
    }
}
