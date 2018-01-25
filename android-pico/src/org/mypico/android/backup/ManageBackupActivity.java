package org.mypico.android.backup;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.app.DialogFragment;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.support.v4.content.ContextCompat;

import com.google.common.base.Optional;

import org.mypico.android.db.DbHelper;
import org.mypico.android.setup.RestoreBackupActivity;
import org.mypico.android.setup.SetupActivity;
import org.mypico.android.util.PgpWordListByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

import org.mypico.jpico.backup.BackupKey;
import org.mypico.jpico.backup.BackupKeyRestoreStateException;

import org.mypico.android.R;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class ManageBackupActivity extends Activity implements OnConfigureBackupListener, OnCreateBackupListener {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(ManageBackupActivity.class.getSimpleName());

    private void updateInformationText() {
        String backupType = BackupFactory.restoreBackupType().getProviderName();
        long timeBackup = BackupFactory.getSavedTime();
        long timeModified = DbHelper.getDatabaseFile().lastModified();

        SimpleDateFormat dataFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formattedBackupDate = timeBackup != -1 ? dataFormatter.format(timeBackup) : "never";
        String formattedDBDate = timeModified != 0 ? dataFormatter.format(timeModified) : "never";

        TextView info = (TextView) findViewById(R.id.last_backup_description);
        info.setText(getString(R.string.manage_backup_information_text, backupType, formattedBackupDate, formattedDBDate));

        TextView allBackedUp = (TextView) findViewById(R.id.all_backed_up);
        Button performBackupBtn = (Button) findViewById(R.id.button_perform_backup_now);

        if (timeModified == 0) {
            allBackedUp.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            allBackedUp.setText(getString(R.string.manage_backup_nothing_to_backup));
            performBackupBtn.setEnabled(false);
        } else if (timeBackup > timeModified) {
            allBackedUp.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            allBackedUp.setText(getString(R.string.manage_backup_all_backed_up));
            performBackupBtn.setEnabled(true);
        } else {
            allBackedUp.setTextColor(ContextCompat.getColor(this, R.color.failure_red));
            allBackedUp.setText(getString(R.string.manage_backup_backup_needed));
            performBackupBtn.setEnabled(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_backup);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.button_perform_backup_now).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performBackupNow(v);
            }
        });

        findViewById(R.id.button_show_backup_words).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBackupWords(v);
            }
        });


        findViewById(R.id.button_recover_backup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recoverBackup(v);
            }
        });

        updateInformationText();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void performBackupNow(View v) {
        // Backup the data store containing the new Key pairing
        final Optional<IBackupProvider> backupProvider =
            BackupFactory.newBackup(ManageBackupActivity.this);
        if (!backupProvider.isPresent()) {
            LOGGER.warn("No backup provider is configured");
            finish();
        }
    }

    private void showBackupWords(View v) {
        LOGGER.debug("Displaying the user secret (used to encrypt/decrypt) backups");
        // Note that the backupKey is persists to the SharedPreferences
        // on creation
        try {
            final BackupKey backupKey = SharedPreferencesBackupKey.restoreInstance();
            // Display the user secret as a set of words from the PGP wordlist
            final String pgpWords =
                new PgpWordListByteString(this).toWords(backupKey.getUserSecret());
            final DialogFragment newFragment =
                PgpWordListOutputDialogFragment.newInstance(pgpWords.split("\\s"));
            newFragment.setRetainInstance(true);
            newFragment.show(getFragmentManager(), PgpWordListOutputDialogFragment.TAG);
        } catch (BackupKeyRestoreStateException e) {
            LOGGER.error("BackupKey was not persisted");
        }
    }

    private void recoverBackup(View v) {
        final Intent restoreBackupIntent = new Intent(this, RestoreBackupActivity.class);
        startActivityForResult(restoreBackupIntent, SetupActivity.SETUP_RESULT_CODE);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);

        // Forward to the fragment onActivity.
        // This is required startIntentSenderForResult() - used in the Google Drive API does not
        // forward the result to the fragment that called it
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment fragment = fragmentManager.findFragmentByTag(BackupProviderFragment.TAG);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, result);
        }

        if (requestCode == SetupActivity.SETUP_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                LOGGER.info("Backup successfully restored");
            }
        }
    }

    @Override
    public void onConfigureBackupSuccess(final IBackupProvider backupProvider) {
        // Verify the method's preconditions
        checkNotNull(backupProvider);

        LOGGER.debug("Configuring backup successful");
        // Perform the backup
        backupProvider.backup();
        String toastString = getResources().getString(R.string.manage_backup_performing_toast);
        Toast.makeText(this, toastString, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConfigureBackupCancelled() {
        LOGGER.error("Configuring backup cancelled");
    }

    @Override
    public void onConfigureBackupFailure() {
        LOGGER.error("Configuring backup failed");
    }

    @Override
    public void onCreateBackupStart() {
    }

    @Override
    public void onCreateBackupSuccess() {
        updateInformationText();
    }

    @Override
    public void onCreateBackupFailure() {
        String toastString = getResources().getString(R.string.manage_backup_error_performing_toast);
        Toast.makeText(this, toastString, Toast.LENGTH_SHORT).show();
    }
}
