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
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Parcelable;

/**
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class AuthFailedDialog extends DialogFragment {

    public interface AuthFailedSource extends Parcelable {

        /**
         * Return a dialog to be displayed when an authentication has failed.
         *
         * @param context activity context to use when constructing the dialog
         * @param userMsg User feedback message for the failure. Can be null.
         * @return dialog to display when an authentication has failed
         */
        Dialog getAuthFailedDialog(Activity context, String userMsg);
    }

    protected static final String SOURCE =
        AuthFailedDialog.class.getCanonicalName() + "source";

    protected static final String USER_MESSAGE =
        AuthFailedDialog.class.getCanonicalName() + "user_message";

    /**
     * Public no-args constructor required for all fragments.
     */
    public AuthFailedDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get the dialog source to create an appropriate dialog for us:
        Bundle args = getArguments();
        AuthFailedSource source = args.getParcelable(SOURCE);
        String userMsg = args.getString(USER_MESSAGE);
        if (source != null) {
            return source.getAuthFailedDialog(getActivity(), userMsg);
        } else {
            return null;
        }
    }
}