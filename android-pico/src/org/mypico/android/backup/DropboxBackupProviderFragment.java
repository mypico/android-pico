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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.dropbox.chooser.android.DbxChooser;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.RESTUtility;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.client2.session.AppKeyPair;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;

import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.backup.BackupFile;
import org.mypico.jpico.backup.BackupKey;
import org.mypico.jpico.backup.BackupKeyException;
import org.mypico.jpico.backup.EncBackupFile;
import org.mypico.jpico.gson.EncBackupFileGson;
import org.mypico.android.R;
import org.mypico.android.core.PicoApplication;
import org.mypico.android.util.AsyncTaskResult;

import static org.mypico.android.backup.IBackupProvider.BackupType.DROPBOX;

/**
 * Fragment for managing (creating and restoring) backups to DropBox.
 * All interaction with DropBox is conducted using AsyncTasks so that network activity and
 * file IO is performed off the UI thread. This requires that callbacks to the main activity and
 * adding further Fragments (for example, to display ProgressDialogs) needs to be careful
 * synchronised with the Activity lifecycle.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public final class DropboxBackupProviderFragment extends BackupProviderFragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        DropboxBackupProviderFragment.class.getSimpleName());
    private static final String DROPBOX_ACCESS_TOKEN_KEY = "DropBoxAccessToken";
    private static final int DBX_CHOOSER_REQUEST = 0;
    private static final String dropboxUri = "com.dropbox.android";

    private DropboxAPI<AndroidAuthSession> mDBApi;
    private String APP_KEY;
    private String APP_SECRET;
    private boolean dropboxAppInstalled;

    /**
     * Backs up the Pico database file to DropBox.
     */
    private class CreateBackupTask extends AsyncTask<File, Void, AsyncTaskResult<Boolean>> {

        @Override
        protected void onPreExecute() {
            createBackupStarted();
        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final File... params) {
            // Verify the method's preconditions
            final File dbFile = checkNotNull(params[0]);

            LOGGER.info("Backing up Pico database to DropBox");
            // Copy the file to the user's DropBox
            try {
                // Staging and encryption of the database under the backup secret key        	
                final BackupFile backupDbFile = BackupFile.newInstance(dbFile);
                final BackupKey backupKey = SharedPreferencesBackupKey.restoreInstance();
                final EncBackupFile encBackupDbFile = backupDbFile.createEncBackupFile(backupKey);

                // JSON encode the encrypted database backup file
                final String jsonEnc = EncBackupFileGson.gson.toJson(encBackupDbFile);
                final InputStream jsonEncIs = new ByteArrayInputStream(jsonEnc.getBytes("UTF-8"));
                try {
                    final Entry response = mDBApi.putFile(getBackupName(), jsonEncIs,
                        jsonEnc.getBytes("UTF-8").length, null, null);
                    LOGGER.info("The backed up file's revision in DropBox is: {}", response.rev);
                    return new AsyncTaskResult<Boolean>(true);
                } finally {
                    jsonEncIs.close();
                }
            } catch (BackupKeyException e) {
                // BackupKey is invalid
                LOGGER.error("BackupKey is invalid", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (FileNotFoundException e) {
                // Database file not found  
                LOGGER.error("Database file not found", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (IOException e) {
                // Reading from BackupFile failed (resulting in IOException)
                LOGGER.error("Reading from the BackupFile failed", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxUnlinkedException e) {
                // DropBox account unlinked, configure() should run onResume()
                // therefore, this is an error
                LOGGER.error("DropBox account unlinked", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxFileSizeException e) {
                // File size to large for DropBox API   
                LOGGER.error("DropBox API cannot handle file of this size", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxServerException e) {
                // DropBox server error
                LOGGER.error("DropBox server responded with error", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxIOException e) {
                // Network related error
                LOGGER.warn("Error communicating with DropBox", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxException e) {
                LOGGER.error("Unknown DropBox error occurred", e);
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
     * Restores the latest backup file from DropBox.
     */
    private class RestoreLatestBackupTask extends RestoreBackupTask {

        RestoreLatestBackupTask() throws IOException {
        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final File... params) {

            // Copy the file to the user's DropBox
            try {
                final Entry dropboxDir = mDBApi.metadata("/", 0, null, true, null);
                if (dropboxDir.isDir) {
                    if (!dropboxDir.contents.isEmpty()) {
                        Collections.sort(dropboxDir.contents, new Comparator<Entry>() {
                            public int compare(final Entry e1, final Entry e2) {
                                final Date dateE1 = RESTUtility.parseDate(e1.modified);
                                final Date dateE2 = RESTUtility.parseDate(e2.modified);
                                return dateE2.compareTo(dateE1);
                            }
                        });
                        // The file to back is the latest
                        final File backupFile = new File(dropboxDir.contents.get(0).path);
                        LOGGER.debug("File to restore = {}", backupFile);

                        return super.doInBackground(backupFile);
                    } else {
                        LOGGER.error("DropBox directory is empty");
                        return new AsyncTaskResult<Boolean>(false);
                    }
                } else {
                    LOGGER.error("DropBox backup is not a directory!");
                    return new AsyncTaskResult<Boolean>(
                        new Exception("DropBox backup is not a directory!"));
                }
            } catch (DropboxUnlinkedException e) {
                // DropBox account unlinked, configure() should run onResume()
                // therefore, this is an error
                LOGGER.error("DropBox account unlinked", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxPartialFileException e) {
                // Incomplete file transfered from DropBox; DO NOT restore   
                LOGGER.error("Partial backup restored", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxServerException e) {
                // DropBox server error
                LOGGER.error("DropBox server responded with error", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxIOException e) {
                // Network related error
                LOGGER.warn("Error communicating with DropBox", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxException e) {
                LOGGER.error("Unknown DropBox error occurred", e);
                return new AsyncTaskResult<Boolean>(e);
            }
        }
    }

    /**
     * Restores the selected backup file from DropBox.
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    private class RestoreBackupTask extends AsyncTask<File, Void, AsyncTaskResult<Boolean>> {

        private final File tempFile = File.createTempFile("picobackup", null,
            PicoApplication.getContext().getCacheDir());

        RestoreBackupTask() throws IOException {
        }

        @Override
        protected void onPreExecute() {
            downloadBackupStarted();
        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final File... params) {
            // Verify the method's preconditions
            final File backupFile = checkNotNull(params[0]);

            try {
                // Copy the file to restore from DropBox to a temporary file prior
                // to decryption
                final FileOutputStream outputStream = new FileOutputStream(tempFile);
                try {
                    final DropboxFileInfo info = mDBApi.getFile(
                        backupFile.getName(), null, outputStream, null);

                    LOGGER.trace("Successfully restored backup {}", info);
                    return new AsyncTaskResult<Boolean>(true);
                } finally {
                    outputStream.close();
                }
            } catch (FileNotFoundException e) {
                // Database file not found  
                LOGGER.error("Database file not found", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxUnlinkedException e) {
                // DropBox account unlinked, configure() should run onResume()
                // therefore, this is an error
                LOGGER.error("DropBox account unlinked", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxPartialFileException e) {
                // Incomplete file transfered from DropBox; DO NOT restore   
                LOGGER.error("Partial backup restored", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxServerException e) {
                // DropBox server error
                LOGGER.error("DropBox server responded with error", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxIOException e) {
                // Network related error
                LOGGER.warn("Error communicating with DropBox", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxException e) {
                LOGGER.error("Unknown DropBox error occurred", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (IOException e) {
                LOGGER.error("IOException closing output stream", e);
                return new AsyncTaskResult<Boolean>(e);
            }
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
     * Queries whether the DropBox Pico application directory is empty.
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    private class IsEmptyTask extends AsyncTask<Void, Void, AsyncTaskResult<Boolean>> {

        @Override
        protected void onPreExecute() {
            queryBackupProviderStarted();
        }

        @Override
        protected AsyncTaskResult<Boolean> doInBackground(final Void... params) {

            try {
                final Entry dropboxDir = mDBApi.metadata("/", 0, null, true, null);
                if (dropboxDir.isDir) {
                    return new AsyncTaskResult<Boolean>(dropboxDir.contents.isEmpty());
                } else {
                    LOGGER.error("DropBox backup is not a directory!");
                    return new AsyncTaskResult<Boolean>(
                        new Exception("DropBox backup is not a directory!"));
                }
            } catch (DropboxUnlinkedException e) {
                // DropBox account unlinked; configure() should run onResume()
                // therefore, this is an error
                LOGGER.error("DropBox account unlinked", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxServerException e) {
                // DropBox server error
                LOGGER.error("DropBox server responded with error", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxIOException e) {
                // Network related error
                LOGGER.warn("Error communicating with DropBox", e);
                return new AsyncTaskResult<Boolean>(e);
            } catch (DropboxException e) {
                LOGGER.error("Unknown DropBox error occurred", e);
                return new AsyncTaskResult<Boolean>(e);
            }
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
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        // Initialise the DropBox APP_KEY and APP_SECRET 
        APP_KEY = getResources().getString(R.string.DROPBOX_APP_KEY);
        APP_SECRET = getResources().getString(R.string.DROPBOX_APP_SECRET);

        // Query whether the Dropbox app is installed, if so the user can select
        // files to restore using the app
        dropboxAppInstalled = dropBoxInstalled();

        LOGGER.trace("DropBox fragment attached");
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
                                 final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        LOGGER.trace("DropBox fragment onActivityResult");

        if (requestCode == DBX_CHOOSER_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                final DbxChooser.Result result = new DbxChooser.Result(data);
                LOGGER.debug("Link to selected file: {}", result.getLink());
                final File backup = new File(result.getLink().getPath());

                // Restore the backup from the user's DropBox,
                try {
                    new RestoreBackupTask().execute(backup);
                } catch (IOException e) {
                    LOGGER.error("RestoreLatestBackupTask raised IOException " +
                        "(couldn't create temporary file)", e);
                    downloadBackupFailure();
                }
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                // Failed or was cancelled by the user.
                LOGGER.debug("DropBox chooser failed");
                downloadBackupCancelled();
            }
        }
    }

    private boolean dropBoxInstalled() {

        final PackageManager pm = getActivity().getPackageManager();
        boolean app_installed = false;
        try {
            pm.getPackageInfo(dropboxUri, PackageManager.GET_ACTIVITIES);
            LOGGER.debug("Dropbox app is installed");
            app_installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            LOGGER.debug("Dropbox app is not installed");
            app_installed = false;
        }
        return app_installed;
    }

    @Override
    public void restoreLatestBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "DropBox Fragment isn't attached, cannot call isEmpty");

        try {
            new RestoreLatestBackupTask().execute();
        } catch (IOException e) {
            LOGGER.error("RestoreLatestBackupTask raised IOException " +
                "(couldn't create temporary file)", e);
            downloadBackupFailure();
        }
    }

    @Override
    public void restoreBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "Google Drive Fragment isn't attached, cannot call isEmpty");

        if (dropboxAppInstalled) {
            // Launch DropBox chooser to select the backup to restore,
            // result returned to onBackupListener
            final DbxChooser mChooser = new DbxChooser(APP_KEY);
            mChooser.forResultType(DbxChooser.ResultType.DIRECT_LINK)
                .launch(this, DBX_CHOOSER_REQUEST);
        } else {

        }
    }

    // Note: getActivity() can be called here safely: configure() is called from onResume() on
    // the UI thread, therefore, a configuration change can't have happened
    @Override
    protected void configure() {
        if (mDBApi != null) {
            LOGGER.trace("Verifying DropBox session");
            if (!mDBApi.getSession().isLinked()) {
                if (mDBApi.getSession().authenticationSuccessful()) {
                    try {
                        // Required to complete auth, sets the access token on the session
                        mDBApi.getSession().finishAuthentication();

                        // Store the DropBox access token in the SharedPreferences
                        LOGGER.debug("Storing the DropBox access token");

                        final SharedPreferences preferences =
                            PreferenceManager.getDefaultSharedPreferences(getActivity());
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString(DROPBOX_ACCESS_TOKEN_KEY,
                            mDBApi.getSession().getOAuth2AccessToken());
                        editor.commit();

                        // Inform the activity that configuring backup to DropBox has succeeded
                        configureBackupProviderSuccess();
                    } catch (IllegalStateException e) {
                        LOGGER.debug("Error authenticating", e);
                        // Inform the activity that configuring backup to DropBox has failed
                        configureBackupProviderFailure();
                    }
                } else {
                    LOGGER.debug("Authentication to DropBox failed");
                    // Inform the activity that configuring backup to DropBox has failed
                    configureBackupProviderFailure();
                }
            } else {
                LOGGER.debug("Already authenticated to DropBox");
            }
        } else {
            LOGGER.trace("DropBox API handle is null!");

            final AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
            final AndroidAuthSession session = new AndroidAuthSession(appKeys);
            mDBApi = new DropboxAPI<AndroidAuthSession>(session);

            final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
            final String dropBoxAccessToken =
                preferences.getString(DROPBOX_ACCESS_TOKEN_KEY, "");
            if (isNullOrEmpty(dropBoxAccessToken)) {
                LOGGER.debug("AccessToken for DropBox not found");
                mDBApi.getSession().startOAuth2Authentication(getActivity());
            } else {
                // Restore the DropBox access token from the SharedPreferences
                // Note that it may be expired or invalidated
                LOGGER.debug("AccessToken for DropBox found");
                mDBApi.getSession().setOAuth2AccessToken(dropBoxAccessToken);
                configureBackupProviderSuccess();
            }
        }
    }

    @Override
    protected void createBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "DropBoxBackupProviderFragment isn't attached, cannot call isEmpty");

        // Backup of the file to the user's DropBox, result returned to onBackupListener  
        final File dbFile = DbHelper.getDatabaseFile();
        new CreateBackupTask().execute(dbFile);
    }

    @Override
    public void isEmpty() {
        // Verify the method's preconditions
        checkState(isAttached, "DropBoxBackupProviderFragment isn't attached, cannot call isEmpty");

        // Query whether the user's DropBox is empty, result return to onBackupListener
        new IsEmptyTask().execute();
    }

    /**
     * Creates a new name for the backup of the Pico database.
     *
     * @return backup file name.
     */
    String getBackupName() {
        try {
            final String userName = mDBApi.accountInfo().displayName;
            final String backupName =
                "/" + userName + "-" + android.os.Build.MODEL + "-pico.backup";
            return backupName;
        } catch (DropboxException e) {
            return super.getBackupName();
        }
    }

    @Override
    public BackupType getBackupType() {
        return DROPBOX;
    }

    @Override
    public EnumSet<RestoreOption> getRestoreOptions() {
        if (dropboxAppInstalled) {
            return EnumSet.of(RestoreOption.RESTORE_LATEST, RestoreOption.RESTORE_USER_SELECTED, RestoreOption.DONT_RESTORE);
        } else {
            return EnumSet.of(RestoreOption.RESTORE_LATEST, RestoreOption.DONT_RESTORE);
        }
    }
}