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
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.NotificationCompat;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

import org.mypico.android.db.DbHelper;
import org.mypico.android.util.PauseHandler;
import org.mypico.android.util.ProgressDialogFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.jpico.backup.BackupFileException;
import org.mypico.jpico.backup.BackupKey;
import org.mypico.jpico.backup.BackupKeyException;
import org.mypico.jpico.backup.EncBackupFile;
import org.mypico.jpico.gson.EncBackupFileGson;

/**
 * BackupProviderFragment abstraction representing a backup provider.
 * A backup provider is used to backup and restore the Pico pairings and services database.
 * Backups are encrypted and decrypted using a backup key, which is known to the user of the Pico.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public abstract class BackupProviderFragment extends Fragment implements IBackupProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        BackupProviderFragment.class.getSimpleName());

    /**
     * Tag used to identify the BackupProviderFragment when added to an Activity.
     */
    public final static String TAG = "BackupProviderFragment";

    /**
     * Application context retrieved from the Activity onAttach.
     */
    private Context applicationContext;

    /**
     * BackupProviderFragment is attached to an Activity.
     */
    protected boolean isAttached;

    /**
     * BackupProviderFragment has already been configured
     */
    protected boolean isConfigured = false;

    /**
     * Used to store the encrypted backup file, prior to the user entering the secret key.
     * Once successfully decrypted this file is used as the restored Pico pairing and services
     * database.
     */
    File encryptedBackupFile;

    /**
     * Handler for this activity
     */
    final BackupPauseHandler handler;

    {
        // Create the handler in the paused state, the State Fragment is responsible for pausing
        // and resuming the Handler in synchronicity with the lifecycle of the Activity the
        // BackupProviderFragement is attached to.
        handler = new BackupPauseHandler();
        handler.pause();
    }

    /**
     * Message Handler class that supports buffering up of messages when the activity is paused,
     * i.e. in the background.
     *
     * @see <a href="http://stackoverflow.com/questions/8040280/how-to-handle-handler-messages-when-activity-fragment-is-paused">How to handle Handler messages when activity/fragment is paused - Stack Overflow</a>
     */
    final static class BackupPauseHandler extends PauseHandler {

        /**
         * Handler messages
         */
        final static int MSG_SHOW_DIALOG = 1;
        final static int MSG_DISMISS_DIALOG = 2;
        final static int CALLBACK = 4;

        /**
         * Callback types.
         */
        final static int ON_QUERY_BACKUP_SUCCESS_EMPTY = 0;
        final static int ON_QUERY_BACKUP_SUCCESS_NOT_EMPTY = 1;
        final static int ON_QUERY_BACKUP_FAILURE = 2;
        final static int ON_CONFIGURE_BACKUP_CANCELLED = 3;
        final static int ON_CONFIGURE_BACKUP_SUCCESS = 4;
        final static int ON_CONFIGURE_BACKUP_FAILURE = 5;
        final static int ON_CREATE_BACKUP_SUCCESS = 6;
        final static int ON_CREATE_BACKUP_FAILURE = 7;
        final static int ON_RESTORE_BACKUP_DOWNLOADED = 8;
        final static int ON_RESTORE_BACKUP_SUCCESS = 9;
        final static int ON_RESTORE_BACKUP_CANCELLED = 10;
        final static int ON_RESTORE_BACKUP_FAILURE = 12;
        final static int ON_DECRYPT_BACKUP_FAILURE = 13;

        /**
         * Activity instance
         */
        private Activity activity;

        /**
         * Set the activity associated with the handler
         *
         * @param activity the activity to set
         */
        final void setActivity(final Activity activity) {
            this.activity = activity;
        }

        @Override
        final protected boolean storeMessage(final Message message) {
            // All messages are stored by default
            return true;
        }

        @Override
        final protected void processMessage(final Message msg) {
            if (activity != null) {
                try {
                    switch (msg.what) {
                        case MSG_SHOW_DIALOG:
                            ProgressDialogFragment.newInstance(msg.arg1).show(
                                activity.getFragmentManager(), ProgressDialogFragment.TAG);
                            break;
                        case MSG_DISMISS_DIALOG:
                            // Remove the ProgressDialog fragment - if attached
                            final DialogFragment progressFragment = (DialogFragment)
                                activity.getFragmentManager().findFragmentByTag(
                                    ProgressDialogFragment.TAG);
                            if (progressFragment != null) {
                                progressFragment.dismiss();
                            }
                            break;
                        case CALLBACK:
                            switch (msg.arg1) {
                                case ON_CONFIGURE_BACKUP_SUCCESS:
                                    if (activity instanceof OnConfigureBackupListener) {
                                        ((OnConfigureBackupListener) activity).onConfigureBackupSuccess(
                                            (IBackupProvider) msg.obj);
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnConfigureBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_CONFIGURE_BACKUP_CANCELLED:
                                    if (activity instanceof OnConfigureBackupListener) {
                                        ((OnConfigureBackupListener) activity).onConfigureBackupCancelled();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnConfigureBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_CONFIGURE_BACKUP_FAILURE:
                                    if (activity instanceof OnConfigureBackupListener) {
                                        ((OnConfigureBackupListener) activity).onConfigureBackupFailure();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnConfigureBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_QUERY_BACKUP_SUCCESS_EMPTY:
                                    LOGGER.debug("No backups found");

                                    if (activity instanceof OnQueryBackupListener) {
                                        ((OnQueryBackupListener) activity).onQueryBackupIsEmpty();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't " +
                                            "implement OnRestoreBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_QUERY_BACKUP_SUCCESS_NOT_EMPTY:
                                    if (activity instanceof OnQueryBackupListener) {
                                        ((OnQueryBackupListener) activity).onQueryBackupIsNotEmpty();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnQueryBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_QUERY_BACKUP_FAILURE:
                                    if (activity instanceof OnQueryBackupListener) {
                                        ((OnQueryBackupListener) activity).onQueryBackupFailure();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnQueryBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_CREATE_BACKUP_SUCCESS:
                                    if (activity instanceof OnCreateBackupListener) {
                                        ((OnCreateBackupListener) activity).onCreateBackupSuccess();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnCreateBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_CREATE_BACKUP_FAILURE:
                                    if (activity instanceof OnCreateBackupListener) {
                                        ((OnCreateBackupListener) activity).onCreateBackupFailure();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnCreateBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_RESTORE_BACKUP_DOWNLOADED:
                                    if (activity instanceof OnRestoreBackupListener) {
                                        ((OnRestoreBackupListener) activity).onRestoreBackupDownloaded();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnRestoreBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_RESTORE_BACKUP_SUCCESS:
                                    if (activity instanceof OnRestoreBackupListener) {
                                        ((OnRestoreBackupListener) activity).onRestoreBackupSuccess();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnRestoreBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_RESTORE_BACKUP_CANCELLED:
                                    if (activity instanceof OnRestoreBackupListener) {
                                        ((OnRestoreBackupListener) activity).onRestoreBackupCancelled();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnRestoreBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_RESTORE_BACKUP_FAILURE:
                                    if (activity instanceof OnRestoreBackupListener) {
                                        ((OnRestoreBackupListener) activity).onRestoreBackupFailure();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnRestoreBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                                case ON_DECRYPT_BACKUP_FAILURE:
                                    if (activity instanceof OnRestoreBackupListener) {
                                        ((OnRestoreBackupListener) activity).onRestoreBackupFailure();
                                    } else {
                                        LOGGER.error("Backup called from Activity that doesn't implement " +
                                            "OnRestoreBackupListener interface");
                                        activity.finish();
                                    }
                                    break;
                            }
                            break;
                    }
                } finally {

                }
            }
        }
    }

    /**
     * State fragment synchronises the Activity state and the Handler state. This ensures that
     * callbacks to the Activity remain valid across configuration changes.
     *
     * @see <a href="http://stackoverflow.com/questions/8040280/how-to-handle-handler-messages-when-activity-fragment-is-paused">How to handle Handler messages when activity/fragment is paused - Stack Overflow</a>
     */
    public final static class State extends Fragment {

        static final String TAG = "State";

        /**
         * Handler for this activity
         */
        public BackupPauseHandler handler;

        /**
         * Public no-args constructor required for all fragments.
         */
        public State() {

        }

        public static State getInstance(final BackupPauseHandler handler) {
            final State state = new State();
            state.handler = handler;
            return state;
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setRetainInstance(true);
        }

        @Override
        public void onResume() {
            super.onResume();

            handler.setActivity(getActivity());
            handler.resume();
        }

        @Override
        public void onPause() {
            super.onPause();

            handler.pause();
        }

        public void onDestroy() {
            super.onDestroy();

            handler.setActivity(null);
        }
    }

    /**
     * Restore options supported by the BackupProviderFragment:
     * - RESTORE_LATEST requires no further user interaction
     * - RESTORE_USER_SELECTED - requires a file browser for the backup provider
     * - DONT_RESTORE - cancels the restore operation
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    public enum RestoreOption {
        RESTORE_LATEST,
        RESTORE_USER_SELECTED,
        DONT_RESTORE
    }

    /**
     * Decrypt the backup file of the UI thread.
     * Note that the thread is static to prevent any potential leaking of the Activity:
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     * @see <a href="http://www.androiddesignpatterns.com/2013/04/activitys-threads-memory-leaks.html">Activitys, Threads, & Memory Leaks - Android Design Patterns</a>
     **/
    private static class DecryptThread extends Thread {

        private final BackupProviderFragment backupProvider;
        private final BackupPauseHandler handler;
        private final File encryptedBackupFile;
        private final byte[] userSecret;

        DecryptThread(final BackupProviderFragment backupProvider, final BackupPauseHandler handler, final File encryptedBackupFile,
                      final byte[] userSecret) {
            this.backupProvider = backupProvider;
            this.handler = handler;
            this.encryptedBackupFile = encryptedBackupFile;
            this.userSecret = userSecret;
        }

        @Override
        public void run() {
            // show the progress indicator
            handler.sendMessage(handler.obtainMessage(BackupPauseHandler.MSG_SHOW_DIALOG,
                R.string.restore_backup_task__progress, 0));

            // Staging and decryption of the database under the secret key
            try {
                // Create a backup key from the entered user secret (note this is persisted)
                final BackupKey backupKey = SharedPreferencesBackupKey.newInstance(userSecret);

                final FileInputStream encBackupIs = new FileInputStream(encryptedBackupFile);
                try {
                    // Read the data from the encrypted Pico backup
                    byte[] encryptedData = new byte[(int) encryptedBackupFile.length()];
                    encBackupIs.read(encryptedData);

                    // De-serialised JSON encoded backup
                    final String jsonEnc = new String(encryptedData, "UTF-8");
                    final EncBackupFile encBackupFile =
                        EncBackupFileGson.gson.fromJson(jsonEnc, EncBackupFile.class);

                    // Create the decrypted database file
                    final File dbFile = DbHelper.getDatabaseFile();
                    if (!dbFile.exists()) {
                        dbFile.getParentFile().mkdirs();
                        dbFile.createNewFile();
                    }

                    encBackupFile.createUnencryptedBackupFile(dbFile, backupKey);
                    LOGGER.debug("Pico backup successfully restored");
                    handler.sendMessage(handler.obtainMessage(
                        BackupPauseHandler.CALLBACK,
                        BackupPauseHandler.ON_RESTORE_BACKUP_SUCCESS, 0));
                } finally {
                    encBackupIs.close();
                }
            } catch (BackupFileException e) {
                // BackupFile could not be restored, usually a result of the decryption of the
                // backup file failing
                LOGGER.error("BackupFile could not be restored", e);
                handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
                    BackupPauseHandler.ON_DECRYPT_BACKUP_FAILURE, 0, backupProvider));
            } catch (BackupKeyException e) {
                // The BackupKey used to decrypt the file is invalid
                LOGGER.error("The BackupKey couldn't be used to decrypt the backup", e);
                handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
                    BackupPauseHandler.ON_DECRYPT_BACKUP_FAILURE, 0, backupProvider));
            } catch (IOException e) {
                // IOException restoring the backup file
                LOGGER.error("Backup file couldn't be restored", e);
                handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
                    BackupPauseHandler.ON_DECRYPT_BACKUP_FAILURE, 0, backupProvider));

            } finally {
                // dismiss the progress indicator
                handler.sendMessage(handler.obtainMessage(
                    BackupPauseHandler.MSG_DISMISS_DIALOG, 0, 0));
            }
        }
    }

    ;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        // Once attached store the application context
        applicationContext = activity.getApplicationContext();

        // Set the is attached flag true
        isAttached = true;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setRetainInstance(true) this ensures that the instance state of the
        // BackupProviderFragment is preserved on rotation of the display
        setRetainInstance(true);

        if (savedInstanceState == null) {
            LOGGER.trace("Adding State Fragment for synchronising Handler and Activity lifecycle");
            final Fragment state = State.getInstance(handler);
            final FragmentManager fm = getFragmentManager();
            final FragmentTransaction ft = fm.beginTransaction();
            ft.add(state, State.TAG);
            ft.commit();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Set the is attached flag false
        isAttached = false;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Ensure that the BackupProviderFragment is configured
        if (!isConfigured)
            configure();
    }

    /**
     * Decrypt and restore the Pico pairings and services database.
     *
     * @param userSecret The user secret used to encrypt the backup of the Pico pairings and
     *                   services database.
     */
    @Override
    public void decryptRestoredBackup(final byte[] userSecret) {
        // Verify the method's preconditions
        checkNotNull(userSecret);

        // Run of the UI thread
        new DecryptThread(this, handler, encryptedBackupFile, userSecret).start();
    }

    protected void configureBackupProviderSuccess() {
        isConfigured = true;
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_CONFIGURE_BACKUP_SUCCESS, 0, this));
    }

    protected void configureBackupProviderFailure() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_CONFIGURE_BACKUP_FAILURE, 0));
    }

    protected void queryBackupProviderStarted() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.MSG_SHOW_DIALOG,
            R.string.isempty_task__progress, 0));
    }

    protected void queryBackupProviderCompleted() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.MSG_DISMISS_DIALOG, 0, 0));
    }

    protected void queryBackupProviderIsEmpty() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_QUERY_BACKUP_SUCCESS_EMPTY, 0));
    }

    protected void queryBackupProviderIsNotEmpty() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_QUERY_BACKUP_SUCCESS_NOT_EMPTY, 0, this));
    }

    protected void queryBackupProviderFailure() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_QUERY_BACKUP_FAILURE, 0));
    }

    protected void downloadBackupStarted() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.MSG_SHOW_DIALOG,
            R.string.restore_backup_task__progress, 0));
    }

    protected void downloadBackupCompleted() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.MSG_DISMISS_DIALOG, 0, 0));
    }

    protected void downloadBackupSuccess(final File file) {
        // Store the temporary file used to restore the backup,
        // this file is decrypted and used as the restored Pico pairings and
        // services database.
        encryptedBackupFile = file;

        // Display the PgpWordListDialogFragment
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_RESTORE_BACKUP_DOWNLOADED,
            0, this));
    }

    protected void downloadBackupCancelled() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_RESTORE_BACKUP_CANCELLED, 0));
    }

    protected void downloadBackupFailure() {
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_RESTORE_BACKUP_FAILURE, 0));
    }

    private final int id = 1;

    protected void createBackupStarted() {
        final NotificationManager mNotifyManager =
            (NotificationManager) applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        final NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(applicationContext);

        mBuilder.setContentTitle(applicationContext.getText(
            R.string.create_backup_task__backup_notification_title))
            .setContentText(applicationContext.getText(
                R.string.create_backup_task__backup_notification))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getResources().getColor(R.color.pico_orange));

        mBuilder.setProgress(100, 0, false);
        // Issues the notification
        mNotifyManager.notify(id, mBuilder.build());
    }

    protected void createBackupCompleted() {
    }

    protected void createBackupSuccess() {
        final NotificationManager mNotifyManager =
            (NotificationManager) applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        final NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(applicationContext);

        mBuilder.setContentText(applicationContext
            .getText(R.string.create_backup_task__backup_notification_success))
            .setProgress(0, 0, false);
        mNotifyManager.notify(id, mBuilder.build());

        BackupFactory.saveBackupTimeNow();
        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_CREATE_BACKUP_SUCCESS, 0));
    }

    protected void createBackupFailure() {
        final NotificationManager mNotifyManager =
            (NotificationManager) applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        final NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(applicationContext);

        mBuilder.setContentText(applicationContext
            .getText(R.string.create_backup_task__backup_notification_failure))
            .setProgress(0, 0, false);
        mNotifyManager.notify(id, mBuilder.build());

        handler.sendMessage(handler.obtainMessage(BackupPauseHandler.CALLBACK,
            BackupPauseHandler.ON_CREATE_BACKUP_FAILURE, 0));
    }

    @Override
    public void backup() {
        // Verify the method's preconditions
        checkState(isAttached, "BackupProviderFragment isn't attached, cannot call backup");

        // Create the backup with the backup provider; progress of the task is given to the
        // user using an Android Notification
        createBackup();
    }

    @Override
    public void restoreBackup() {
        // Android doesn't provide a file viewer. Therefore, at present the option to
        // select a specific file isn't supported.
        LOGGER.debug("restoreBackup not supported");
    }

    /**
     * Creates a new name for the backup of the Pico database.
     *
     * @return backup file name.
     */
    String getBackupName() {
        final String backupName = android.os.Build.MODEL + "-pico.backup";
        return backupName;
    }

    /**
     * Configure the backup provider.
     */
    abstract protected void configure();

    /**
     * Create a new backup with the backup provider.
     */
    abstract protected void createBackup();
}
