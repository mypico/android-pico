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


package org.mypico.android.core;

import android.app.Activity;
import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.common.base.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The <code>ReattachTask</code> class extends {@link AsyncTask} in order to provide an easy way
 * to deal with background tasks that would otherwise block the UI thread.
 * <p>
 * Unlike {@link AsyncTask}, the task can be tied to an Activity, which can be accessed in the
 * {@link AsyncTask#onPostExecute(Object)} method. The Fragment will be reattached to the
 * activity in case, for example, of screen rotation.
 *
 * @param <Params>   The type of data to be passed to the {@link AsyncTask#doInBackground(Object[])}
 *                   call.
 * @param <Progress> The type of data to be passed to the
 *                   {@link AsyncTask#onProgressUpdate(Object[])} call.
 * @param <Result>   The type of data to be passed to the {@link AsyncTask#onPostExecute(Object)}
 *                   call.
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @see AsyncTask
 */
public abstract class ReattachTask<Params, Progress, Result>
    extends AsyncTask<Params, Progress, Result> {

    /**
     * Class to be overridden that will define the fragment to be reattached.
     */
    public static class ReattachFragment extends Fragment {

        // Require public no-args constructor
        public ReattachFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

        @Override
        public String toString() {
            return "reattachTaskFragment" + hashCode();
        }
    }

    private Optional<ReattachFragment> fragment = Optional.absent();

    protected ReattachTask() {
        super();
    }

    /**
     * Constructor.
     *
     * @param activity The activity to attach the task to.
     */
    protected ReattachTask(Activity activity) {
        super();
        attach(activity);
    }

    /**
     * Attaches the task/fragment to the activity.
     *
     * @param activity
     */
    public void attach(Activity activity) {
        checkNotNull(activity, "activity cannot be null");

        final ReattachFragment f = new ReattachFragment();
        activity.getFragmentManager().beginTransaction().add(f, f.toString()).commit();
        fragment = Optional.of(f);
    }

    /**
     * Gets the activity to the task/fragment is attached to.
     *
     * @return The activity the task is been attachd to.
     */
    protected Activity getActivity() {
        if (fragment.isPresent()) {
            return fragment.get().getActivity();
        } else {
            throw new IllegalStateException("task has not been attached");
        }
    }
}