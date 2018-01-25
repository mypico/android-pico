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


package org.mypico.android.pairing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.text.Html;

import com.google.common.base.Optional;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.android.data.SafePairing;

/**
 * Show the dialogue for deleting a pairing.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public final class DeletePairingDialog extends DialogFragment {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(DeletePairingDialog.class.getSimpleName());
    private static final String PAIRINGS = "PAIRINGS";

    private Optional<DeletePairingListener> listener = Optional.absent();

    public static interface DeletePairingListener {
        public void onDeleteOk(final ArrayList<SafePairing> pairings);

        public void onDeleteCancel();
    }

    /**
     * Public no-args constructor required for all fragments
     */
    public DeletePairingDialog() {
    }

    /**
     * Factory for creating a <code>DeletePairingDialog</code> dialogue.
     *
     * @param pairings       The pairings to be deleted.
     * @param targetFragment The fragment to associate the dialogue with.
     * @return The generated <code>DeletePairingDialog</code> object.
     */
    public static DeletePairingDialog getInstance(final ArrayList<SafePairing> pairings,
                                                  final Fragment targetFragment) {
        final DeletePairingDialog dialog = new DeletePairingDialog();

        // Arguments for delete dialog
        final Bundle args = new Bundle();
        args.putParcelableArrayList(PAIRINGS, pairings);

        dialog.setArguments(args);

        // Optionally set target fragment for callbacks
        if (targetFragment != null) {
            dialog.setTargetFragment(targetFragment, 0);
        }

        return dialog;
    }

    public static DeletePairingDialog getInstance(final ArrayList<SafePairing> pairings) {
        return getInstance(pairings, null);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Set the listener for this dialog. If the target fragment is set and implements the
        // listener interface use that, otherwise if the parent activity implement the listener
        // interface use that, otherwise log a warning.
        final Fragment targetFragment = getTargetFragment();
        if (targetFragment != null && targetFragment instanceof DeletePairingListener) {
            listener = Optional.of((DeletePairingListener) targetFragment);
            LOGGER.debug("using target fragment as listener");
        } else if (activity instanceof DeletePairingListener) {
            listener = Optional.of((DeletePairingListener) activity);
            LOGGER.debug("using activity as listener");
        } else {
            LOGGER.warn("neither target fragment or activity are valid listeners");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final ArrayList<SafePairing> pairings = getArguments().getParcelableArrayList(PAIRINGS);
        final int count = pairings.size();
        final int title;
        final String message;

        // The title and message of the dialog depends on the number of items being
        // deleted
        if (count == 1) {
            final SafePairing pairing = pairings.get(0);
            title = R.string.delete_pairing_dialog__title_1;
            message = getActivity().getString(R.string.delete_pairing_dialog__message_1,
                pairing.getSafeService().getName(), pairing.getName());
        } else {
            title = R.string.delete_pairing_dialog__title_multi;
            message = getActivity().getString(R.string.delete_pairing_dialog__message_multi,
                count);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title);
        builder.setMessage(Html.fromHtml(message));

        // Buttons both handled by the parent activity
        builder.setPositiveButton(
            R.string.delete_pairing_dialog__positive,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener.isPresent()) {
                        listener.get().onDeleteOk(pairings);
                    } else {
                        LOGGER.warn("positive button clicked, but no listener set");
                    }
                }
            });

        builder.setNegativeButton(
            R.string.delete_pairing_dialog__negative,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener.isPresent()) {
                        listener.get().onDeleteCancel();
                    } else {
                        LOGGER.warn("negative button clicked, but no listener set");
                    }
                }
            });

        // Create and return the dialog
        return builder.create();
    }

    @Override
    public void onDetach() {
        // Prevent leak of reference to listener
        listener = Optional.absent();

        super.onDetach();
    }
}