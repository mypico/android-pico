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


import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.R;

import android.content.Intent;
import android.os.Bundle;


/**
 * Provide the UI for allowing the user to choose a key pairing. This is needed in case the users
 * requests to authenticate to a service, but more than one account is available for authenticating
 * to it.
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 */
final public class ChooseKeyPairingActivity
    extends ChoosePairingActivity
    implements KeyPairingListFragment.Listener {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_key_pairing);
    }

    @Override
    public void onSinglePairing(final SafeKeyPairing pairing) {
        // Verify the method's preconditions
        checkNotNull(pairing, "Pairing cannot be null");

        // Automatically authenticate
        authenticate(pairing);
    }

    @Override
    public void onPairingClicked(final SafeKeyPairing pairing) {
        // Verify the method's preconditions
        checkNotNull(pairing, "Pairing cannot be null");

        authenticate(pairing);
    }

    @Override
    public void onNoPairings() {
        showNoPairingsToast();
    }

    @Override
    public void onMultiplePairings(int count) {
        // Verify the method's preconditions
        checkArgument(count > 1, "Multiple pairings expected");

        hideSpinner();
    }

    /**
     * Action the authentication once the pairing has been selected.
     *
     * @param pairing The pairing to authenticate using.
     */
    private void authenticate(final SafeKeyPairing pairing) {
        // Verify the method's preconditions
        assert (pairing != null);

        final Intent intent = new Intent(this, AuthenticateActivity.class);
        intent.putExtra(SafeKeyPairing.class.getCanonicalName(), pairing);

        // Also include all of the extras from the received intent to forward the terminal details
        // if they are present and the extra data
        intent.putExtras(getIntent());

        // Setting this flag means that the next activity will pass any
        // result back to the result target of this activity.
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        startActivity(intent);
        finish();
    }
}
