package org.mypico.android.backup;

import java.util.EnumSet;

/**
 * Fragment for managing the "no backup" option. This is only necessary becasue the existing backup
 * system doesn't take a null provider. All backup operations under this class do nothing except
 * trigger some appropriate callbacks.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class NullBackupProviderFragment extends BackupProviderFragment {

    @Override
    public BackupType getBackupType() {
        return BackupType.NONE;
    }

    @Override
    public void isEmpty() {
        queryBackupProviderIsEmpty();
    }

    @Override
    public void restoreLatestBackup() {
        downloadBackupCompleted();
        downloadBackupFailure();
    }

    @Override
    protected void configure() {
        configureBackupProviderSuccess();
    }

    @Override
    protected void createBackup() {
        createBackupCompleted();
        createBackupSuccess();
    }

    @Override
    public EnumSet<RestoreOption> getRestoreOptions() {
        return EnumSet.of(RestoreOption.DONT_RESTORE);
    }
}
