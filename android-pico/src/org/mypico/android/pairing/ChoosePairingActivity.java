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

import org.mypico.android.R;
import org.mypico.android.core.AcquireCodeActivity;
import org.mypico.android.data.SafeService;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Provide the UI for allowing the user to choose a pairing. This is needed in case the users
 * requests to authenticate to a service, but more than one account is available for authenticating
 * to it.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public abstract class ChoosePairingActivity extends Activity {

    /**
     * Remove the spinner from the display.
     */
    protected void hideSpinner() {
        final ProgressBar spinner = (ProgressBar) findViewById(
            R.id.choose_pairing_activity__spinner);
        spinner.setVisibility(View.GONE);
    }

    /**
     * Display Toast to user when Pico doesn't have pairings with the service.
     */
    protected void showNoPairingsToast() {
        final SafeService service = getIntent().getParcelableExtra(AcquireCodeActivity.SERVICE);

        // What should we show as the service name?
        String serviceName = service.getName();
        // Don't rely on service name being set
        if (serviceName == null || serviceName.equals("")) {
            serviceName = getString(R.string.this_service);
        }

        // Show the toast
        Toast.makeText(
            getApplicationContext(),
            getString(R.string.no_pairings_with, serviceName),
            Toast.LENGTH_LONG).show();

        // go back to the scanner
        startActivity(new Intent(this, AcquireCodeActivity.class));
        finish();
    }
}