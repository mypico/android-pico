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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveApi.MetadataBufferResult;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFile.DownloadProgressListener;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.common.collect.Lists;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.ByteStreams.copy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mypico.android.backup.IBackupProvider.BackupType.GOOGLEDRIVE;

import org.mypico.jpico.backup.BackupFile;
import org.mypico.jpico.backup.BackupKeyException;
import org.mypico.jpico.backup.EncBackupFile;
import org.mypico.jpico.gson.EncBackupFileGson;

/**
 * UI Fragment for managing backups to Google Drive.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public final class GoogleDriveBackupProviderFragment extends BackupProviderFragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        GoogleDriveBackupProviderFragment.class.getSimpleName());
    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 1;
    private static final int PICK_FILE_REQUEST_CODE = 2;
    private static final String GOOGLE_DRIVE_BACKUP_FOLDER_FNAME = "pico-backup";

    private GoogleApiClient mGoogleApiClient;
    private DriveId backupFolder;

    /**
     * Callback invoke of downloading a backup file to restore.
     */
    private final class RestoreBackupResultCallback implements ResultCallback<DriveContentsResult> {

        @Override
        public void onResult(final DriveContentsResult result) {
            downloadBackupCompleted();

            if (result.getStatus().isSuccess()) {
                try {
                    // Get the InputStream of the file to restore from Google Drive
                    final InputStream googleDriveIs = result.getDriveContents().getInputStream();
                    try {
                        // Copy the file to restore from Google Drive to a temporary file prior
                        // to decryption
                        final File tempFile = File.createTempFile("picobackup", null,
                            getActivity().getCacheDir());
                        final OutputStream tempFileOs = new FileOutputStream(tempFile);
                        try {
                            // Guava ByteStreams.copy() does not flush or close either stream
                            copy(googleDriveIs, tempFileOs);
                            tempFileOs.flush();

                            downloadBackupSuccess(tempFile);
                        } finally {
                            tempFileOs.close();
                        }
                    } finally {
                        googleDriveIs.close();
                    }
                } catch (IOException e) {
                    LOGGER.error("Restoring the latest backup failed", e);
                    downloadBackupFailure();
                }
            } else {
                LOGGER.error("Restoring the latest backup failed");
                downloadBackupFailure();
            }
        }
    }

    ;

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
                                 final Intent data) {
        switch (requestCode) {
            case PICK_FILE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    final DriveId driveId = (DriveId) data.getParcelableExtra(
                        OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    LOGGER.info("Selected Pico backup file {}", driveId);

                    final DriveFile backupFile = Drive.DriveApi.getFile(mGoogleApiClient, driveId);
                    backupFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY,
                        new DownloadProgressListener() {

                            {
                                downloadBackupStarted();
                            }

                            @Override
                            public void onProgress(final long bytesDownloaded,
                                                   final long bytesExpected) {
                                // Update the progress bar
                                final int progress = (int) ((bytesDownloaded / bytesExpected) * 100);
                                LOGGER.debug("GoogleDrive download progress = {}%", progress);
                            }
                        })
                        .setResultCallback(new RestoreBackupResultCallback());
                } else {
                    LOGGER.error("Failed backing up the Pico database");
                    downloadBackupFailure();
                }
                break;
            case RESOLVE_CONNECTION_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGGER.trace("Google Drive fragment created");

        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
            .addApi(Drive.API)
            .addScope(Drive.SCOPE_APPFOLDER)
            .addScope(Drive.SCOPE_FILE)
            .addConnectionCallbacks(new ConnectionCallbacks() {

                @Override
                public void onConnected(final Bundle connectionHint) {
                    LOGGER.info("Google Drive connected");
                    createPicoBackupFolderIfNotExists();
                }

                @Override
                public void onConnectionSuspended(final int cause) {
                    LOGGER.info("Google Drive connection suspended");
                }
            })
            .addOnConnectionFailedListener(new OnConnectionFailedListener() {

                @Override
                public void onConnectionFailed(final ConnectionResult connectionResult) {
                    LOGGER.warn("Google Drive connection failed {}", connectionResult.getErrorCode());
                    if (connectionResult.hasResolution()) {
                        try {
                            connectionResult.startResolutionForResult(getActivity(),
                                RESOLVE_CONNECTION_REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            LOGGER.error("Error connecting to the user's Google Drive account");
                            configureBackupProviderFailure();
                        }
                    } else {
                        GooglePlayServicesUtil.getErrorDialog(
                            connectionResult.getErrorCode(), getActivity(), 0,
                            new DialogInterface.OnCancelListener() {

                                @Override
                                public void onCancel(final DialogInterface dialog) {
                                    configureBackupProviderFailure();
                                }
                            }).show();
                    }
                }
            }).build();
    }

    private void createPicoBackupFolderIfNotExists() {

        // Search for the pico-backup folder
        final Query query = new Query.Builder().addFilter(Filters.and(
            Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"),
            Filters.eq(SearchableField.TITLE, GOOGLE_DRIVE_BACKUP_FOLDER_FNAME))).build();
        Drive.DriveApi.query(mGoogleApiClient, query)
            .setResultCallback(new ResultCallback<MetadataBufferResult>() {

                @Override
                public void onResult(final MetadataBufferResult result) {
                    // Iterate over the matching Metadata instances in mdResultSet
                    // Find the latest file to restore
                    final MetadataBuffer mdb = result.getMetadataBuffer();
                    try {
                        final List<Metadata> metadataList = Lists.newArrayList(mdb);
                        if (!metadataList.isEmpty()) {
                            if (metadataList.get(0).isTrashed()) {
                                LOGGER.warn("Folder is trashed");
                                createPicoBackupFolder();
                            } else {
                                LOGGER.debug("{} folder is not trashed",
                                    GOOGLE_DRIVE_BACKUP_FOLDER_FNAME);
                                backupFolder = metadataList.get(0).getDriveId();
                                configureBackupProviderSuccess();
                            }
                        } else {
                            LOGGER.debug("{} folder is not found",
                                GOOGLE_DRIVE_BACKUP_FOLDER_FNAME);
                            createPicoBackupFolder();
                        }
                    } finally {
                        mdb.close();
                    }
                }
            });
    }

    private void createPicoBackupFolder() {
        final MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
            .setTitle(GOOGLE_DRIVE_BACKUP_FOLDER_FNAME).build();
        Drive.DriveApi.getRootFolder(mGoogleApiClient).createFolder(
            mGoogleApiClient, changeSet).setResultCallback(
            new ResultCallback<DriveFolder.DriveFolderResult>() {

                @Override
                public void onResult(final DriveFolder.DriveFolderResult result) {
                    if (!result.getStatus().isSuccess()) {
                        LOGGER.error("Error while trying to create the folder");
                        configureBackupProviderFailure();
                    } else {
                        LOGGER.debug("Created a folder: {}",
                            result.getDriveFolder().getDriveId());
                        backupFolder = result.getDriveFolder().getDriveId();
                        configureBackupProviderSuccess();
                    }
                }
            }
        );
    }

    @Override
    protected void configure() {
        // Verify the method's preconditions
        checkState(isAttached, "Google Drive Fragment isn't attached, cannot call isEmpty");

        mGoogleApiClient.connect();
    }

    @Override
    public void restoreLatestBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "Google Drive Fragment isn't attached, cannot call isEmpty");
        LOGGER.info("Restoring latest Google Drive backup");

        // Query the children of the pico-backup app folder,
        // note this can be safely called from the UI thread
        Drive.DriveApi.getFolder(mGoogleApiClient, backupFolder)
            .listChildren(mGoogleApiClient)
            .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {

                {
                    downloadBackupStarted();
                }

                @Override
                public void onResult(final DriveApi.MetadataBufferResult result) {
                    downloadBackupCompleted();

                    if (!result.getStatus().isSuccess()) {
                        LOGGER.error("Querying pico-backup Google Drive app folder failed");
                        downloadBackupFailure();
                        return;
                    }

                    // Find the latest file to restore
                    final MetadataBuffer mdb = result.getMetadataBuffer();
                    try {
                        final List<Metadata> metadataList = Lists.newArrayList(mdb);
                        if (!metadataList.isEmpty()) {
                            // Sort the Google Drive metadata in order of most recently modified
                            Collections.sort(metadataList, new Comparator<Metadata>() {

                                @Override
                                public int compare(final Metadata m1, final Metadata m2) {
                                    return m2.getModifiedDate().compareTo(m1.getModifiedDate());
                                }
                            });

                            final Metadata fileMetadata = metadataList.get(0);
                            final DriveFile backupFile = Drive.DriveApi.getFile(mGoogleApiClient,
                                fileMetadata.getDriveId());
                            backupFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY,
                                new DownloadProgressListener() {

                                    @Override
                                    public void onProgress(final long bytesDownloaded,
                                                           final long bytesExpected) {
                                        // Update the progress bar
                                        LOGGER.debug("GoogleDrive progress {}",
                                            (int) (bytesDownloaded / bytesExpected) * 100);
                                    }
                                })
                                .setResultCallback(new RestoreBackupResultCallback());
                        } else {
                            LOGGER.warn("No backups found in the pico-backup Google Drive app folder");
                            downloadBackupFailure();
                        }
                    } finally {
                        mdb.close();
                    }
                }
            });
    }

    @Override
    public void restoreBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "Google Drive Fragment isn't attached, cannot call isEmpty");
        LOGGER.info("Restoring Google Drive backup");

        // Launch Google Drive chooser to select the backup to restore,
        // result returned to onBackupListener
        final String[] mimeTypes = {"text/plain"};
        final IntentSender intentSender = Drive.DriveApi.newOpenFileActivityBuilder()
            .setMimeType(mimeTypes)
            .setActivityStartFolder(backupFolder)
            .build(mGoogleApiClient);
        try {
            getActivity().startIntentSenderForResult(
                intentSender, PICK_FILE_REQUEST_CODE, null, 0, 0, 0);
        } catch (SendIntentException e) {
            LOGGER.error("Restoring backup failed");
            downloadBackupFailure();
        }
    }

    @Override
    public void isEmpty() {
        // Verify the method's preconditions
        checkState(isAttached, "Google Drive Fragment isn't attached, cannot call isEmpty");
        LOGGER.info("Querying user's Google Drive pico-backup app folder for backups");

        // Query the children of the pico-backup app folder,
        // note this can be safely called from the UI thread
        Drive.DriveApi.getFolder(mGoogleApiClient, backupFolder)
            .listChildren(mGoogleApiClient)
            .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {

                {
                    queryBackupProviderStarted();
                }

                @Override
                public void onResult(final DriveApi.MetadataBufferResult result) {
                    queryBackupProviderCompleted();

                    if (!result.getStatus().isSuccess()) {
                        LOGGER.error("Querying pico-backup app folder failed");
                        queryBackupProviderFailure();
                        return;
                    }

                    // Is the pico-backup app folder empty?
                    final MetadataBuffer mdb = result.getMetadataBuffer();
                    try {
                        final List<Metadata> metadataList = Lists.newArrayList(mdb);
                        if (metadataList.isEmpty()) {
                            queryBackupProviderIsEmpty();
                        } else {
                            boolean contentsTrashed = true;
                            for (final Metadata md : metadataList) {
                                // Check whether the contents of the pico-backup
                                // folder have been trashed
                                if (!md.isTrashed()) {
                                    // Contents isn't trashed and therefore the directory isn't empty
                                    contentsTrashed = false;
                                }
                            }

                            if (contentsTrashed) {
                                queryBackupProviderIsEmpty();
                            } else {
                                queryBackupProviderIsNotEmpty();
                            }
                        }
                    } finally {
                        mdb.close();
                    }
                }
            });
    }

    @Override
    protected void createBackup() {
        // Verify the method's preconditions
        checkState(isAttached, "GoogleDriveBackupProviderFragment isn't attached, " +
            " cannot call isEmpty");

        // Backup of the file to the user's Google Drive, result returned to onBackupListener
        final File dbFile = DbHelper.getDatabaseFile(getActivity());
        ;
        LOGGER.info("Backing up Pico database {} to Google Drive", dbFile.getPath());

        Drive.DriveApi.newDriveContents(mGoogleApiClient)
            .setResultCallback(new ResultCallback<DriveContentsResult>() {

                {
                    createBackupStarted();
                }

                @Override
                public void onResult(final DriveContentsResult result) {
                    createBackupCompleted();

                    if (!result.getStatus().isSuccess()) {
                        LOGGER.error("Error while trying to create new file contents");
                        createBackupFailure();
                        return;
                    }

                    // Copy the contents of the pico database to the new Google Drive file
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
                            // Guava ByteStreams.copy() does not flush or close either stream
                            copy(jsonEncIs, result.getDriveContents().getOutputStream());

                            final String backupName = android.os.Build.MODEL + "-pico.bak";
                            final MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                .setTitle(backupName)
                                .setMimeType("text/plain")
                                .build();
                            Drive.DriveApi
                                .getFolder(mGoogleApiClient, backupFolder)
                                .createFile(mGoogleApiClient, changeSet, result.getDriveContents())
                                .setResultCallback(
                                    new ResultCallback<DriveFolder.DriveFileResult>() {

                                        @Override
                                        public void onResult(
                                            final DriveFolder.DriveFileResult result) {

                                            if (!result.getStatus().isSuccess()) {
                                                LOGGER.error("Error while trying to create new " +
                                                    "file contents");
                                                createBackupFailure();
                                                return;
                                            }

                                            LOGGER.info("File created");
                                            createBackupSuccess();
                                        }
                                    });
                        } finally {
                            jsonEncIs.close();
                        }
                    } catch (BackupKeyException e) {
                        LOGGER.error("BackupKey is invalid", e);
                        createBackupFailure();
                    } catch (IOException e) {
                        LOGGER.error("Failed copy Pico database to the user's Google Drive", e);
                        createBackupFailure();
                    }
                }
            });
    }

    @Override
    public BackupType getBackupType() {
        return GOOGLEDRIVE;
    }

    @Override
    public EnumSet<RestoreOption> getRestoreOptions() {
        return EnumSet.of(RestoreOption.RESTORE_LATEST, RestoreOption.DONT_RESTORE);
    }
}