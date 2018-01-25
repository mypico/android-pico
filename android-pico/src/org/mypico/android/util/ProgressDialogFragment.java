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

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.os.Bundle;

/**
 * Displays progress of addition and removal of terminals.
 * The ProgressDialog is displayed within a Fragment allowing configurations changes to
 * be handled easily.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public final class ProgressDialogFragment extends DialogFragment {

    public static final String TAG = "ProgressDialogTag";
    private static final String PROGRESS_FRAGMENT_MESSAGE = "message";

    /**
     * Static factory method for constructing the ProgressDialog.
     *
     * @return the fragment constructed by the factory.
     */
    public static ProgressDialogFragment newInstance(final int message) {
        final ProgressDialogFragment frag = new ProgressDialogFragment();

        // Supply index input as an argument.
        final Bundle args = new Bundle();
        args.putInt(PROGRESS_FRAGMENT_MESSAGE, message);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setMessage(getString(getArguments().getInt(PROGRESS_FRAGMENT_MESSAGE)));
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
} 