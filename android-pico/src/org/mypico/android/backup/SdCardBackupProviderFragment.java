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

import android.os.AsyncTask;
import android.os.Environment;

import com.google.common.io.ByteStreams;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.Files.copy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mypico.android.backup.IBackupProvider.BackupType.SDCARD;

import org.mypico.android.core.PicoApplication;
import org.mypico.android.util.AsyncTaskResult;
import org.mypico.jpico.backup.BackupKeyException;
import org.mypico.jpico.backup.BackupFile;
import org.mypico.jpico.backup.EncBackupFile;
import org.mypico.jpico.gson.EncBackupFileGson;

/**
 * UI Fragment for managing backups to the SD card.
 * All interaction with SD card is conducted using AsyncTasks so that file IO is performed off the
 * UI thread.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public final class SdCardBackupProviderFragment extends BackupProviderFragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        SdCardBackupProviderFragment.class.getSimpleName());

    private static final int MAX_BACKUPS = 5;

    private static final String PICO_BACKUP_DIR = File.separator + "pico-backup";

    /**
     * Backs up the Pico database file to the external storage.
     */
    private final class CreateBackupTask
        extends AsyncTask<File, Void, AsyncTaskResult<Boolean>> {

        private final String backupName;

        CreateBackupTask(final String backupName) {
            this.backupName = backupName;
            ;
        }

        @Override
        protected void onPreExecute() {
            createBackupStarted();
        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final File... params) {
            // Verify the method's preconditions
            final File dbFile = checkNotNull(params[0]);
            final File backupDir = checkNotNull(params[1]);

            LOGGER.info("Backing up Pico database to SD card");

            // Copy the file to the user's Pico SD card backup
            try {
                // Staging and encryption of the database under the backup secret key
                final BackupFile backupDbFile = BackupFile.newInstance(dbFile);
                final EncBackupFile encBackupDbFile = backupDbFile.createEncBackupFile(
                    SharedPreferencesBackupKey.restoreInstance());

                // JSON encode the encrypted database backup file
                final String jsonEnc = EncBackupFileGson.gson.toJson(encBackupDbFile);
                final InputStream jsonEncIs =
                    new ByteArrayInputStream(jsonEnc.getBytes("UTF-8"));
                try {
                    final File backupFile = new File(backupDir, backupName);
                    LOGGER.debug("Backup filename {}", backupFile.getPath());
                    if (!backupFile.exists()) {
                        backupFile.getParentFile().mkdirs();
                        backupFile.createNewFile();
                        // Backup the Pico database - copy(From, To)
                        final OutputStream backupFileOs = new FileOutputStream(backupFile);
                        try {
                            ByteStreams.copy(jsonEncIs, backupFileOs);
                        } finally {
                            backupFileOs.close();
                        }
                    }

                    // Ensure that the last 5 backups are kept.
                    final List<File> backups = Arrays.asList(backupDir.listFiles());
                    final int numBackups = backups.size();
                    if (numBackups >= MAX_BACKUPS) {
                        // Sort in order of oldest backups first
                        Collections.sort(backups, new Comparator<File>() {
                            public int compare(final File f1, final File f2) {
                                if (f1.lastModified() < f2.lastModified()) {
                                    return -1;
                                } else if (f1.lastModified() > f2.lastModified()) {
                                    return 1;
                                } else {
                                    return 0;
                                }
                            }
                        });

                        // Delete the oldest backups - so that only 5 remain
                        for (int i = 0; i < numBackups - MAX_BACKUPS; i++) {
                            final File file = backups.get(i);
                            file.delete();
                        }
                    }
                    return new AsyncTaskResult<Boolean>(true);
                } finally {
                    jsonEncIs.close();
                }
            } catch (BackupKeyException e) {
                // BackupKey is invalid
                LOGGER.error("BackupKey is invalid", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (IOException e) {
                // Couldn't create file
                LOGGER.error("Couldn't create backup file (may be out of storage?)", e);
                return new AsyncTaskResult<Boolean>(e);
            }
        }

        @Override
        public void onPostExecute(final AsyncTaskResult<Boolean> result) {
            createBackupCompleted();

            if (result.getError() == null) {
                createBackupSuccess();
            } else {
                createBackupFailure();
            }
        }
    }

    /**
     * Restores the selected backup file from the external storage.
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    private final class RestoreBackupTask
        extends AsyncTask<File, Void, AsyncTaskResult<Boolean>> {

        private File tempFile = null;

        RestoreBackupTask() throws IOException {
        }

        @Override
        protected void onPreExecute() {
            downloadBackupStarted();

        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final File... params) {
            // Verify the method's preconditions
            final File backupDir = checkNotNull(params[0]);

            // Sort the backup files in order of last modified
            final List<File> backups = Arrays.asList(backupDir.listFiles());
            Collections.sort(backups, new Comparator<File>() {
                public int compare(final File f1, final File f2) {
                    if (f1.lastModified() > f2.lastModified()) {
                        return -1;
                    } else if (f1.lastModified() < f2.lastModified()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });

            // Backup the latest backup file
            final File backupFile = backups.get(0);
            LOGGER.debug("Found file to backup {}", backupFile.getPath());
            tempFile = backupFile;

            return new AsyncTaskResult<Boolean>(true);
        }

        @Override
        public void onPostExecute(final AsyncTaskResult<Boolean> result) {
            downloadBackupCompleted();

            if (result.getError() == null) {
                downloadBackupSuccess(tempFile);
            } else {
                downloadBackupFailure();
            }
        }
    }

    /**
     * Queries whether the external storage Pico application directory is empty.
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    private final class IsEmptyTask extends AsyncTask<File, Void, AsyncTaskResult<Boolean>> {

        @Override
        protected void onPreExecute() {
            queryBackupProviderStarted();
        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final File... params) {
            // Verify the method's preconditions
            final File backupDir = checkNotNull(params[0]);

            // Create the Pico backup directory if it doesn't already exist
            LOGGER.trace("Backup directory {}", backupDir.getPath());
            if (!backupDir.exists()) {
                if (!backupDir.mkdir()) {
                    LOGGER.error("Failed creating Pico backup directory!");
                    return new AsyncTaskResult<Boolean>(
                        new Exception("Failed creating Pico backup directory!"));
                }
            }

            // Return true if the backup directory is empty.
            LOGGER.trace("Files in backup directory {}", backupDir.listFiles());
            final ArrayList<File> files =
                new ArrayList<File>(Arrays.asList(backupDir.listFiles()));
            return new AsyncTaskResult<Boolean>(files.isEmpty());
        }

        @Override
        public void onPostExecute(final AsyncTaskResult<Boolean> result) {
            queryBackupProviderCompleted();

            if (result.getError() == null) {
                if (result.getResult()) {
                    queryBackupProviderIsEmpty();
                } else {
                    queryBackupProviderIsNotEmpty();
                }
            } else {
                queryBackupProviderFailure();
            }
        }
    }

    @Override
    protected void configure() {
        // The SD card is backed up to a fixed location on the SD card
        final String state = Environment.getExternalStorageState();

        final boolean mExternalStorageAvailable;
        final boolean mExternalStorageWriteable;
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            //  to know is we can neither read nor write
            mExternalStorageAvailable = false;
            mExternalStorageWriteable = false;
        }

        // Inform the activity that configuring backup to DropBox has succeeded
        if (mExternalStorageAvailable && mExternalStorageWriteable) {
            configureBackupProviderSuccess();
        } else {
            configureBackupProviderFailure();
        }
    }

    @Override
    protected void createBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "SD card Fragment isn't attached, cannot call backup");

        // Backup of the file to the user's DropBox, result returned to onBackupListener
        final File dbFile = DbHelper.getDatabaseFile(getActivity());
        final File sdCardDir = new File(Environment.getExternalStorageDirectory().getPath());
        new CreateBackupTask(getBackupName())
            .execute(dbFile, new File(sdCardDir, PICO_BACKUP_DIR));
    }

    @Override
    public void isEmpty() {
        // Verify the method's preconditions
        checkState(isAttached, "SD card Fragment isn't attached, cannot call backup");

        // Query whether the user's Pico SD card directory is empty,
        // result returned to onQueryBackupListener
        final File sdCardDir = new File(Environment.getExternalStorageDirectory().getPath());
        new IsEmptyTask().execute(new File(sdCardDir, PICO_BACKUP_DIR));
    }

    @Override
    public void restoreLatestBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "SD card Fragment isn't attached, cannot call backup");

        // Restore the backup from the user's DropBox,
        // result returned to onBackupRestoredListener
        final File sdCardDir = new File(Environment.getExternalStorageDirectory().getPath());
        try {
            new RestoreBackupTask().execute(new File(sdCardDir, PICO_BACKUP_DIR));
        } catch (IOException e) {
            LOGGER.error("Failed to create temporary file");
            downloadBackupFailure();
        }
    }

    /**
     * Creates a new name for the backup of the Pico database.
     *
     * @return backup file name.
     */
    String getBackupName() {
        // Create a unique filename for the backup
        final String backupName =
            android.os.Build.MODEL +
                "-pico.backup-" +
                DateFormat.getDateTimeInstance().format(new Date());
        return backupName;
    }

    @Override
    public EnumSet<RestoreOption> getRestoreOptions() {
        // Android doesn't provide a file viewer. Therefore, at present the option to
        // select a specific file isn't supported.
        return EnumSet.of(RestoreOption.RESTORE_LATEST, RestoreOption.DONT_RESTORE);
    }

    @Override
    public BackupType getBackupType() {
        return SDCARD;
    }
}