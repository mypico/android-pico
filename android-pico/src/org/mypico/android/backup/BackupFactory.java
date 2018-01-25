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


package org.mypico.android.backup;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import org.mypico.android.core.PicoApplication;
import org.mypico.android.backup.IBackupProvider.BackupType;

/**
 * Factory class for creating a BackupProviderFragment.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public final class BackupFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(
        BackupFactory.class.getSimpleName());

    /**
     * Key used to stored the configured BackupProvider in the of the app's SharedPreferences.
     */
    private final static String BACKUP_KEY = "Backup";

    /**
     * Key used to stored the time BackupProvider stored a backup last time.
     */
    private final static String LAST_BACKUP_TIME_KEY = "LastBackupTime";

    /**
     * Set the last saved backup time to the current time.
     */
    public static void saveBackupTimeNow() {
        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(PicoApplication.getContext());
        final SharedPreferences.Editor editor = preferences.edit();

        Date date = new Date();

        editor.putLong(LAST_BACKUP_TIME_KEY, date.getTime());
        editor.commit();
    }

    /**
     * Get the time of the last saved backup.
     *
     * @return The time of the last backup (seconds from epoch).
     */
    public static long getSavedTime() {
        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(PicoApplication.getContext());
        return preferences.getLong(LAST_BACKUP_TIME_KEY, -1);
    }

    /**
     * Factory method for creating a BackupProviderFragement.
     *
     * @param backupType The BackupProviderFragement to create.
     * @param activity   The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider and wrapped as an Optional.
     */
    public static Optional<IBackupProvider> newBackup(final BackupType backupType,
                                                      final Activity activity) {
        // Verify the method preconditions
        checkNotNull(backupType);
        checkNotNull(activity);

        final Optional<IBackupProvider> backupProvider;
        switch (backupType) {
            case DROPBOX:
                LOGGER.debug("DropBox backup configured");
                backupProvider = Optional.of(addDropBoxFragment(activity));
                break;
            case ONEDRIVE:
                LOGGER.debug("OneDrive backup configured");
                backupProvider = Optional.of(addOneDriveFragment(activity));
                break;
            case GOOGLEDRIVE:
                LOGGER.debug("Google Drive backup configured");
                backupProvider = Optional.of(addGoogleDriveFragment(activity));
                break;
            case SDCARD:
                LOGGER.debug("SD card backup configured");
                backupProvider = Optional.of(addSdCardFragment(activity));
                break;
            case NONE:
                LOGGER.warn("No backup configured");
                backupProvider = Optional.of(addNullBackupFragment(activity));
                break;
            default:
                LOGGER.error("No backup mechanism specified");
                backupProvider = IBackupProvider.NO_BACKUP_PROVIDER;
                break;
        }
        return backupProvider;
    }

    /**
     * Factory method for restoring the backup provider.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider and wrapped as an Optional.
     */
    public static Optional<IBackupProvider> newBackup(final Activity activity) {
        // Verify the method preconditions
        checkNotNull(activity);

        return newBackup(restoreBackupType(), activity);
    }

    /**
     * Returns the configured backup mechanism (or none) from the shared preferences.
     *
     * @return The configured backup mechanism (BackupType).
     */
    public static BackupType restoreBackupType() {
        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(PicoApplication.getContext());
        final String backupPref = preferences.getString(BACKUP_KEY, "");
        if (!isNullOrEmpty(backupPref)) {
            try {
                final BackupType backupType = BackupType.valueOf(backupPref);
                return backupType;
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Backup is invalid {}", backupPref);
                return BackupType.NONE;
            }
        }
        return BackupType.NONE;
    }

    /**
     * Stores the configured backup mechanism in the shared preferences.
     *
     * @param backupType The backup type to persist.
     */
    public static void persistBackupType(final BackupType backupType) {
        // Verify the method preconditions
        checkNotNull(backupType);

        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(PicoApplication.getContext());
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(BACKUP_KEY, backupType.toString());
        editor.commit();
    }

    /**
     * Add the DropBox fragment UI to the activity.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider.
     */
    private static IBackupProvider addDropBoxFragment(final Activity activity) {
        // Create the DropBoxBackupProviderFragment; setReatainInstance(true) ensures that the
        // instance state of the Fragment is maintained across reconfiguration of the
        // Activity
        final DropboxBackupProviderFragment backupProviderFragment =
            new DropboxBackupProviderFragment();
        return addBackupProviderFragment(activity, backupProviderFragment);
    }

    /**
     * Add the OneDrive fragment UI to the activity.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider.
     */
    private static IBackupProvider addOneDriveFragment(final Activity activity) {
        // Create the OneDriveBackupProviderFragment; setReatainInstance(true) ensures that the
        // instance state of the Fragment is maintained across reconfiguration of the Activity
        final OneDriveBackupProviderFragment backupProviderFragment =
            new OneDriveBackupProviderFragment();
        return addBackupProviderFragment(activity, backupProviderFragment);
    }

    /**
     * Add the GoogleDrive fragment UI to the activity.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider.
     */
    private static IBackupProvider addGoogleDriveFragment(final Activity activity) {
        // Create the GoogleDriveBackupProviderFragment; setReatainInstance(true) ensures that the
        // instance state of the Fragment is maintained across reconfiguration of the Activity
        final GoogleDriveBackupProviderFragment backupProviderFragment =
            new GoogleDriveBackupProviderFragment();
        return addBackupProviderFragment(activity, backupProviderFragment);
    }

    /**
     * Add the SD card fragment UI to the activity.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider.
     */
    private static IBackupProvider addSdCardFragment(final Activity activity) {
        // Create the SdCardBackupProviderFragmeent; setReatainInstance(true) ensures that the
        // instance state of the Fragment is maintained across reconfiguration of the Activity
        final SdCardBackupProviderFragment backupProviderFragment =
            new SdCardBackupProviderFragment();
        return addBackupProviderFragment(activity, backupProviderFragment);
    }

    /**
     * Add the null backup fragment to the activity.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider.
     */
    private static IBackupProvider addNullBackupFragment(final Activity activity) {
        // Create the NullBackupProviderFragment; setReatainInstance(true) ensures that the
        // instance state of the Fragment is maintained across reconfiguration of the Activity
        final NullBackupProviderFragment backupProviderFragment =
            new NullBackupProviderFragment();
        return addBackupProviderFragment(activity, backupProviderFragment);
    }

    /**
     * Add the fragment UI to the activity.
     *
     * @param activity The Activity to which the Fragment will be attached.
     * @return The new BackupProviderFragment as an IBackupProvider.
     */
    private static IBackupProvider addBackupProviderFragment(final Activity activity,
                                                             final BackupProviderFragment backupProviderFragment) {
        // Add the BackupProviderFragment to the Activity
        backupProviderFragment.setRetainInstance(true);
        final FragmentTransaction transaction = activity.getFragmentManager().beginTransaction();
        transaction.add(backupProviderFragment, BackupProviderFragment.TAG);
        transaction.commit();
        return backupProviderFragment;
    }
}