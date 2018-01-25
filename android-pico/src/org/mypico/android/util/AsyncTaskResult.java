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


package org.mypico.android.util;

/**
 * Class for storing the results of an {@link android.os.AsyncTask}.
 * <p>
 * For an example, see {@link org.mypico.android.backup.SdCardBackupProviderFragment.CreateBackupTask}.
 *
 * @param <T> The type of the result that will be returned by the {@link android.os.AsyncTask}.
 * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see android.os.AsyncTask
 */
public class AsyncTaskResult<T> {

    final private T result;
    final private Exception error;

    /**
     * Get the result of the {@link android.os.AsyncTask}. This will be null if an error occurred.
     *
     * @return The result, or null if there was an error.
     */
    public T getResult() {
        return result;
    }

    /**
     * Get the exception logged by the {@link android.os.AsyncTask}.
     *
     * @return details of the exception that occured, or null if there was no error.
     */
    public Exception getError() {
        return error;
    }

    /**
     * Constructor for the result if no error occurred.
     *
     * @param result The result to return to the {@link android.os.AsyncTask} caller.
     */
    public AsyncTaskResult(final T result) {
        super();
        this.result = result;
        this.error = null;
    }

    /**
     * Constructor for the result if an error occurred.
     *
     * @param error The error to return to the {@link android.os.AsyncTask} caller.
     */
    public AsyncTaskResult(final Exception error) {
        super();
        this.result = null;
        this.error = error;
    }
}