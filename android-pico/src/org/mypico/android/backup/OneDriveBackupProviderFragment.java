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
//import android.content.SharedPreferences;
//import android.preference.PreferenceManager;


import com.microsoft.live.LiveAuthClient;
import com.microsoft.live.LiveAuthException;
import com.microsoft.live.LiveAuthListener;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveConnectSession;
import com.microsoft.live.LiveDownloadOperation;
import com.microsoft.live.LiveDownloadOperationListener;
import com.microsoft.live.LiveOperation;
import com.microsoft.live.LiveOperationException;
import com.microsoft.live.LiveOperationListener;
import com.microsoft.live.LiveStatus;
import com.microsoft.live.LiveUploadOperationListener;
import com.microsoft.live.sample.skydrive.SkyDriveFile;
import com.microsoft.live.sample.skydrive.SkyDriveFolder;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.jpico.backup.BackupFile;
import org.mypico.jpico.backup.BackupKeyException;
import org.mypico.jpico.backup.EncBackupFile;
import org.mypico.jpico.gson.EncBackupFileGson;

import static org.mypico.android.backup.IBackupProvider.BackupType.ONEDRIVE;

/**
 * UI Fragment for managing backups to Microsoft OneDrive.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public final class OneDriveBackupProviderFragment extends BackupProviderFragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        OneDriveBackupProviderFragment.class.getSimpleName());

    private static final String ONEDRIVE_BACKUP_FOLDER_FNAME = "pico-backup";
    private static final String ONEDRIVE_BACKUP_FOLDER_DESC =
        "Store Pico database backups";
    /* Scopes represent permission that the Pico requests of the user:
     * 
     * wl.signin - single-sign on if the user is already signed into their Microsoft account
     * wl.offline_access - the ability of an app to read and update a user's info at any time.
     * wl.skydrive - access users files stored on their OneDrive
     * wl.skydrive_update - read and write access to a user's files stored in OneDrive.
     */
    private static final Iterable<String> scopes =
        Arrays.asList("wl.signin", "wl.offline_access", "wl.skydrive", "wl.skydrive_update");

    private LiveAuthClient auth;
    private LiveConnectClient client;
    private String APP_KEY;
    private String backupFolder;

    /**
     * Helper class for handling ISO 8601 strings of the following format:
     * "2008-03-01T13:00:00+01:00". It also supports parsing the "Z" timezone.
     */
    private static final class ISO8601 {
        /**
         * Transform Calendar to ISO 8601 string.
         */
        public static String fromCalendar(final Calendar calendar) {
            final Date date = calendar.getTime();
            final String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                .format(date);
            return formatted.substring(0, 22) + ":" + formatted.substring(22);
        }

        /**
         * Get current date and time formatted as ISO 8601 string.
         */
        @SuppressWarnings("unused")
        public static String now() {
            return fromCalendar(GregorianCalendar.getInstance());
        }

        /**
         * Transform ISO 8601 string to Calendar.
         */
        public static Calendar toCalendar(final String iso8601string)
            throws ParseException {
            final Calendar calendar = GregorianCalendar.getInstance();
            String s = iso8601string.replace("Z", "+00:00");
            try {
                s = s.substring(0, 22) + s.substring(23);  // to get rid of the ":"
            } catch (IndexOutOfBoundsException e) {
                throw new ParseException("Invalid length", 0);
            }
            final Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(s);
            calendar.setTime(date);
            return calendar;
        }
    }

    private void createPicoBackupFolderIfNotExists() {
        // Query whether the user's OneDrive contains a Pico backup folder
        client.getAsync("me/skydrive/Files?filter=folders", new LiveOperationListener() {

            @Override
            public void onComplete(final LiveOperation operation) {

                final JSONObject result = operation.getResult();
                LOGGER.debug("Result {}", result);
                try {
                    final JSONArray data = result.getJSONArray("data");
                    if (data != null) {
                        Optional<SkyDriveFolder> picoBackupFolder = Optional.absent();
                        for (int i = 0; i < data.length(); i++) {
                            final JSONObject row = data.getJSONObject(i);
                            LOGGER.debug("Found {}", row);
                            if (!isNullOrEmpty(row.getString("type")) &&
                                row.getString("type").equalsIgnoreCase("folder")) {
                                if (!isNullOrEmpty(row.getString("name")) &&
                                    row.getString("name")
                                        .equalsIgnoreCase(ONEDRIVE_BACKUP_FOLDER_FNAME)) {
                                    // Pico backup folder is present
                                    picoBackupFolder = Optional.of(new SkyDriveFolder(row));
                                    break;
                                }
                            }
                        }

                        if (picoBackupFolder.isPresent()) {
                            LOGGER.debug("Pico backup folder already exists");
                            backupFolder = picoBackupFolder.get().getId();
                            configureBackupProviderSuccess();
                        } else {
                            LOGGER.debug("Pico backup folder not found");
                            createPicoBackupFolder();
                        }
                    } else {
                        LOGGER.error("OneDrive response does not cotain a data field");
                        configureBackupProviderFailure();
                    }
                } catch (JSONException e) {
                    LOGGER.error("Error reading response from OneDrive: {}", e.getMessage());
                    configureBackupProviderFailure();
                }
            }

            @Override
            public void onError(final LiveOperationException exception,
                                final LiveOperation operation) {
                LOGGER.error("Error reading folder: {}", exception.getMessage());
                // Invoke the callback on the onBackupListener
                configureBackupProviderFailure();
            }
        });
    }

    private void createPicoBackupFolder() {
        // Create a folder for the Pico database backups to be stored in
        JSONObject body = new JSONObject();
        try {
            body.put("name", ONEDRIVE_BACKUP_FOLDER_FNAME);
            body.put("description", ONEDRIVE_BACKUP_FOLDER_DESC);
            client.postAsync("me/skydrive", body, new LiveOperationListener() {

                @Override
                public void onError(final LiveOperationException exception,
                                    final LiveOperation operation) {
                    LOGGER.error("Error creating folder: {}",
                        exception.getMessage());
                    configureBackupProviderFailure();
                }

                @Override
                public void onComplete(final LiveOperation operation) {
                    final JSONObject result = operation.getResult();
                    if (result.optString("error") != null) {
                        final String text = "Folder created:\n" +
                            "\nID = " + result.optString("id") +
                            "\nName = " + result.optString("name");
                        LOGGER.info(text);
                        backupFolder = result.optString("id");
                        configureBackupProviderSuccess();
                    } else {
                        // Error returned to onComplete rather than onError
                        LOGGER.info("Error creating folder");
                        configureBackupProviderFailure();
                    }
                }
            });
        } catch (JSONException e) {
            LOGGER.error("Error creating Pico backup folder in the " +
                "user's OneDrive", e);
            configureBackupProviderFailure();
        }
    }

    @Override
    public void onAttach(final Activity activity) {
        // Initialise the OneDrive APP_KEY
        APP_KEY = getResources().getString(R.string.ONEDRIVE_APP_KEY);

        super.onAttach(activity);
        LOGGER.trace("OneDrive fragment attached");
    }

    @Override
    protected void configure() {
        // Verify the method's preconditions
        checkState(isAttached, "OneDrive Fragment isn't attached, cannot call isEmpty");

        LOGGER.info("Configuring OneDrive backup");

        auth = new LiveAuthClient(getActivity(), APP_KEY);
        auth.initialize(scopes, new LiveAuthListener() {

            @Override
            public void onAuthComplete(final LiveStatus status,
                                       final LiveConnectSession session, final Object object) {
                if (status == LiveStatus.CONNECTED) {
                    LOGGER.info("OneDrive authentication successful");
                    client = new LiveConnectClient(session);
                    createPicoBackupFolderIfNotExists();
                } else {
                    LOGGER.error("OneDrive authentication failed status = {}", status);
                    auth.login(getActivity(), scopes, this);
                }
            }

            @Override
            public void onAuthError(final LiveAuthException exception, final Object object) {
                LOGGER.warn("OneDrive authentication failed ", exception);
                configureBackupProviderFailure();
            }
        });
    }

    @Override
    public void restoreLatestBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "OneDrive Fragment isn't attached, cannot call isEmpty");

        // Query whether the user's OneDrive is empty, result return to onDownloadBackupListener
        LOGGER.info("Restoring latest OneDrive backup");
        client.getAsync(backupFolder + "/Files", new LiveOperationListener() {

            @Override
            public void onComplete(final LiveOperation operation) {
                // Invoke the callback on the onBackupListener if set
                final JSONObject result = operation.getResult();
                try {
                    final JSONArray data = result.getJSONArray("data");
                    if (data != null) {
                        // Store all the the users files found in their OneDrive so that they
                        // can be sorted in order of most recently updated
                        LOGGER.trace("{}/Files data = {}", backupFolder, data);
                        final List<SkyDriveFile> files = new ArrayList<SkyDriveFile>();
                        for (int i = 0; i < data.length(); i++) {
                            final JSONObject row = data.getJSONObject(i);
                            if (!isNullOrEmpty(row.getString("type")) &&
                                row.getString("type").equalsIgnoreCase("file")) {
                                files.add(new SkyDriveFile(row));
                            }
                        }

                        // Check that files to backup where found in the users OneDrive
                        if (files.size() > 0) {
                            // Sort the OneDrive files in order of most recently updated 
                            Collections.sort(files, new Comparator<SkyDriveFile>() {

                                @Override
                                public int compare(final SkyDriveFile f1,
                                                   final SkyDriveFile f2) {

                                    try {
                                        final Date dateF1 = ISO8601.toCalendar(
                                            f1.getUpdatedTime()).getTime();
                                        final Date dateF2 = ISO8601.toCalendar(
                                            f2.getUpdatedTime()).getTime();
                                        return dateF2.compareTo(dateF1);
                                    } catch (ParseException e) {
                                        // Error parsing the ISO8601 date's in the JSON response,
                                        // as we can't compare the dates assume both file's
                                        // update times are equal
                                        LOGGER.error("Can't parse ISO8601 date found", e);
                                        return 0;
                                    }
                                }
                            });

                            // Download the file from the user's OnDrive; this must not
                            // be done on the UI thread
                            final String latestBackup = files.get(0).getId();
                            LOGGER.info("Latest file = {}", latestBackup);
                            client.downloadAsync(latestBackup + "/content?download=true",
                                new LiveDownloadOperationListener() {

                                    {
                                        downloadBackupStarted();
                                    }

                                    @Override
                                    public void onDownloadCompleted(
                                        final LiveDownloadOperation operation) {
                                        downloadBackupCompleted();

                                        new Thread(new Runnable() {

                                            @Override
                                            public void run() {

                                                try {
                                                    final File dbFile = DbHelper.getDatabaseFile();
                                                    if (!dbFile.exists()) {
                                                        dbFile.getParentFile().mkdirs();
                                                        dbFile.createNewFile();
                                                    }

                                                    final InputStream is = operation.getStream();
                                                    try {
                                                        final FileOutputStream os =
                                                            new FileOutputStream(dbFile);
                                                        try {
                                                            ByteStreams.copy(is, os);
                                                            LOGGER.debug("Backup file downloaded.");
                                                            downloadBackupSuccess(dbFile);
                                                        } finally {
                                                            os.close();
                                                        }
                                                    } finally {
                                                        is.close();
                                                    }
                                                } catch (IOException e) {
                                                    LOGGER.error("Error downloading backup", e);
                                                    downloadBackupFailure();
                                                }
                                            }
                                        }).start();
                                    }

                                    @Override
                                    public void onDownloadFailed(
                                        final LiveOperationException exception,
                                        final LiveDownloadOperation operation) {
                                        LOGGER.error("Error restoring backup {}",
                                            exception.getMessage());
                                        downloadBackupFailure();
                                    }

                                    @Override
                                    public void onDownloadProgress(final int totalBytes,
                                                                   final int bytesRemaining,
                                                                   final LiveDownloadOperation operation) {
                                        LOGGER.debug("Restoring backup ... {}  bytes downloaded {}%",
                                            bytesRemaining, (bytesRemaining / totalBytes) * 100);
                                    }
                                });
                        } else {
                            LOGGER.error("No files found to restore");
                            downloadBackupFailure();
                        }
                    } else {
                        LOGGER.error("OneDrive response does not contain a data field");
                        downloadBackupFailure();
                    }
                } catch (JSONException e) {
                    LOGGER.error("JSON response from OneDrive is malformed");
                    downloadBackupFailure();
                }
            }

            @Override
            public void onError(final LiveOperationException exception,
                                final LiveOperation operation) {
                downloadBackupCompleted();

                LOGGER.info("Error reading folder: {}", exception.getMessage());
                downloadBackupFailure();
            }
        });
    }

    @Override
    public void isEmpty() {
        // Verify the method's preconditions
        checkState(isAttached, "OneDrive Fragment isn't attached, cannot call isEmpty");

        // Query whether the user's OneDrive is empty, result return to onBackupListener
        LOGGER.info("Querying OneDrive for backups");
        client.getAsync(backupFolder + "/Files", new LiveOperationListener() {

            {
                queryBackupProviderStarted();
            }

            @Override
            public void onComplete(final LiveOperation operation) {
                queryBackupProviderCompleted();

                final List<SkyDriveFile> files = new ArrayList<SkyDriveFile>();
                final JSONObject result = operation.getResult();
                try {
                    final JSONArray data = result.getJSONArray("data");
                    if (data != null) {
                        // Store all the the users files found in their OneDrive so that they
                        // can be sorted in order of most recently updated
                        LOGGER.trace("{}/Files data = {}", backupFolder, data);

                        for (int i = 0; i < data.length(); i++) {
                            final JSONObject row = data.getJSONObject(i);
                            if (!isNullOrEmpty(row.getString("type")) &&
                                row.getString("type").equalsIgnoreCase("file")) {
                                files.add(new SkyDriveFile(row));
                            }
                        }
                    } else {
                        LOGGER.error("OneDrive response does not cotain a data field");
                    }
                } catch (JSONException e) {
                    LOGGER.error("Error reading response from OneDrive: {}", e.getMessage());
                } finally {
                    if (files.size() == 0) {
                        queryBackupProviderIsEmpty();
                    } else {
                        queryBackupProviderIsNotEmpty();
                    }
                }
            }

            @Override
            public void onError(final LiveOperationException exception,
                                final LiveOperation operation) {
                queryBackupProviderCompleted();

                LOGGER.error("Error reading OneDrive folder: {}", exception.getMessage());
                queryBackupProviderFailure();
            }
        });
    }

    @Override
    protected void createBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "OneDrive Fragment isn't attached, cannot call backup");

        // Backup of the file to the user's OneDrive, result returned to onBackupListener  
        // Initialise the Pico database filename
        final File dbFile = DbHelper.getDatabaseFile();
        LOGGER.info("Backing up Pico database {} to OneDrive", dbFile.getPath());
        final BackupFile backupDbFile = BackupFile.newInstance(dbFile);
        try {
            // Staging and encryption of the database under the backup secret key
            final EncBackupFile encBackupDbFile = backupDbFile.createEncBackupFile(
                SharedPreferencesBackupKey.restoreInstance());

            // JSON encode the encrypted database backup file
            final String jsonEnc = EncBackupFileGson.gson.toJson(encBackupDbFile);
            final InputStream jsonEncIs = new ByteArrayInputStream(jsonEnc.getBytes("UTF-8"));
            try {
                client.uploadAsync(backupFolder, getBackupName(), jsonEncIs,
                    new LiveUploadOperationListener() {

                        {
                            // Invoke the callback on the onCreateBackupListener
                            createBackupStarted();
                        }

                        @Override
                        public void onUploadFailed(final LiveOperationException exception,
                                                   final LiveOperation operation) {
                            createBackupCompleted();

                            LOGGER.error("Error uploading backup file", exception);
                            createBackupFailure();
                        }

                        @Override
                        public void onUploadCompleted(final LiveOperation operation) {
                            createBackupCompleted();

                            LOGGER.info("File uploaded.");
                            createBackupSuccess();
                        }

                        @Override
                        public void onUploadProgress(final int totalBytes,
                                                     final int bytesRemaining, final LiveOperation operation) {

                            // Update the progress bar
                            LOGGER.debug("OneDrive backup progress {}",
                                ((float) (bytesRemaining / totalBytes)) * 100);
                        }
                    });
            } finally {
                jsonEncIs.close();
            }
        } catch (BackupKeyException e) {
            LOGGER.error("BackupKey is invalid, cannot create backup with OneDrive", e);
            createBackupFailure();
        } catch (IOException e) {
            LOGGER.error("Error uploading backup file, cannot create backup with OneDrive", e);
            createBackupFailure();
        }
    }

    @Override
    public EnumSet<RestoreOption> getRestoreOptions() {
        return EnumSet.of(RestoreOption.RESTORE_LATEST, RestoreOption.DONT_RESTORE);
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
                "-pico-" +
                new Date().getTime() +
                ".backup";
        LOGGER.trace("Filename = {}", backupName);
        return backupName;
    }

    @Override
    public BackupType getBackupType() {
        return ONEDRIVE;
    }
}