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

import org.mypico.android.data.ParcelableAuthToken;
import org.mypico.jpico.crypto.AuthToken;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Parcelable;

/**
 * Display the dialogue box indicating that a delegation attempt has failed.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 */
public class DelegationFailedDialog extends DialogFragment {

    public interface DelegationFailedSource extends Parcelable {
        /**
         * Return a dialog to be displayed when a delegation has failed.
         *
         * @param context activity context to use when constructing the dialog
         * @param token   delegation token
         * @return dialog to display when a delegation has failed
         */
        Dialog getDelegationFailedDialog(Activity context, AuthToken token);
    }

    protected static final String SOURCE =
        AuthFailedDialog.class.getCanonicalName() + "pairing";
    protected static final String TOKEN =
        AuthFailedDialog.class.getCanonicalName() + "token";

    /**
     * Public no-args constructor required for all fragments.
     */
    public DelegationFailedDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get the dialog source to create an appropriate dialog for us:
        DelegationFailedSource source =
            (DelegationFailedSource) getArguments().getParcelable(SOURCE);
        return source.getDelegationFailedDialog(
            getActivity(), (ParcelableAuthToken) getArguments().getParcelable(TOKEN));
    }
}