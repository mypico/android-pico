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

import java.util.EnumSet;

import com.google.common.base.Optional;

/**
 * Interface implemented by each of the different backup providers supported by Pico:
 * DropBox, SD Card, ...
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public interface IBackupProvider {

    /**
     * Enumeration of the backup providers supported by Pico.
     */
    public enum BackupType {
        NONE("None"),
        DROPBOX("Dropbox"),
        ONEDRIVE("Microsoft OneDrive"),
        GOOGLEDRIVE("Google Drive"),
        SDCARD("Local storage");

        private final String providerName;

        BackupType(final String providerName) {
            this.providerName = providerName;
        }

        public String getProviderName() {
            return providerName;
        }
    }

    /**
     * No backup provider selected.
     */
    static final Optional<IBackupProvider> NO_BACKUP_PROVIDER =
        Optional.<IBackupProvider>absent();

    /**
     * Get the type of the backup provider.
     */
    BackupType getBackupType();

    /**
     * Perform a backup of the Pico database using the backup mechanism.
     * The calling Activity must implement OnCreateBackupListener, through which the
     * result is returned.
     */
    void backup();

    /**
     * Query whether the backup provider contains Pico backups.     *
     * The calling Activity must implement OnQueryBackupListener, through which the
     * result is returned.
     */
    void isEmpty();

    /**
     * Restore the Pico database from the latest backup.
     * The calling Activity must implement OnRestoreBackupListener, through which the
     * result is returned.
     */
    void restoreLatestBackup();

    /**
     * Restore the Pico database from a user selected backup.
     * The calling Activity must implement OnRestoreBackupListener, through which the
     * result is returned.
     */
    void restoreBackup();

    /*
     * TODO 
     */
    public void decryptRestoredBackup(final byte[] userSecret);

    /**
     * Returns the RestoreOptions supported by the BackupProvider.
     * By default all restore options are supported. Sub-classes of the BackupProviderFragment
     * for specific providers (such as OneDriver, DropBox) may choose which of these methods they
     * support.
     *
     * @return EnumSet of RestoreOptions supported by the BackupProviderFragment.
     */
    public EnumSet<BackupProviderFragment.RestoreOption> getRestoreOptions();
}