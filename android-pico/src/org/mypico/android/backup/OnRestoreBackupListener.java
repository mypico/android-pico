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

import com.google.common.base.Optional;

/**
 * Listener interface for  communicating with the calling object.
 *
 * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
 */
public interface OnRestoreBackupListener {

    static final public Optional<OnRestoreBackupListener> NO_LISTENER =
        Optional.<OnRestoreBackupListener>absent();

    /**
     * Callback method on initiating restoring a backup.
     */
    void onRestoreBackupStart();

    /**
     * Callback method on downloading  a backup to restore.
     */
    void onRestoreBackupDownloaded();

    /**
     * Callback method on restoring a backup.
     */
    void onRestoreBackupSuccess();

    /**
     * Callback method on cancelling the restoring a backup.
     */
    void onRestoreBackupCancelled();

    /**
     * Callback method on encountering an error restoring a backup.
     */
    void onRestoreBackupFailure();
}