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

import org.mypico.android.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Provide the dialog box to be shown if an invalid code is scanned by the user.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class InvalidCodeDialog extends DialogFragment {

    public interface InvalidCodeCallbacks {
        void onScanAnother();

        void onCancel();
    }

    private static final String MESSAGE =
        InvalidCodeDialog.class.getCanonicalName() + "message";
    private static final String MESSAGE_RES_ID =
        InvalidCodeDialog.class.getCanonicalName() + "messageResId";
    private static final int DEFAULT_MESSAGE_RES_ID = R.string.default_invalid_code_message;

    /**
     * Get an instance of the dialogue using the given resource id for the message to be shown.
     *
     * @param messageResId The resource id to use for the message.
     * @return The generated dialogue instance.
     */
    public static InvalidCodeDialog getInstance(int messageResId) {
        InvalidCodeDialog instance = new InvalidCodeDialog();

        Bundle args = new Bundle();
        args.putInt(MESSAGE_RES_ID, messageResId);
        instance.setArguments(args);

        return instance;
    }

    /**
     * Get an instance of the dialogue using the given message to be shown.
     *
     * @param message The message to show.
     * @return The generated dialogue instance.
     */
    public static InvalidCodeDialog getInstance(String message) {
        InvalidCodeDialog instance = new InvalidCodeDialog();

        Bundle args = new Bundle();
        args.putString(MESSAGE, message);
        instance.setArguments(args);

        return instance;
    }

    /**
     * Public no-args constructor required for all fragments.
     */
    public InvalidCodeDialog() {
        super();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final String message = getArguments().getString(MESSAGE);
        if (message != null) {
            builder.setMessage(message);
        } else {
            final int messageResId = getArguments().getInt(MESSAGE_RES_ID, DEFAULT_MESSAGE_RES_ID);
            builder.setMessage(messageResId);
        }

        builder.setCancelable(false);
        builder.setPositiveButton(
            getActivity().getString(R.string.scan_another),
            new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (getActivity() instanceof InvalidCodeCallbacks) {
                        ((InvalidCodeCallbacks) getActivity()).onScanAnother();
                    }
                }
            });

        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        // head back to the scanner
        ((InvalidCodeCallbacks) getActivity()).onScanAnother();
    }

}
