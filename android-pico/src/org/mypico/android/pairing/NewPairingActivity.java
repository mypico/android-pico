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


// Copyright University of Cambridge, 2014
package org.mypico.android.pairing;

import org.mypico.android.data.SafeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.jpico.data.pairing.Pairing;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

/**
 * Activity where a user can decide whether or not to create a new {@link Pairing} with a given
 * service. There is an {@link EditText} control to allow the user to provide a human-readable name
 * of their choice for the new Pairing.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public abstract class NewPairingActivity extends Activity {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(NewPairingActivity.class.getSimpleName());

    protected SafeService service;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().hasExtra(SafeService.class.getCanonicalName())) {
            // Get the service info and pairing type from the intent.
            // Note: The intent is preserved across re-creation, so it is not
            // necessary to attempt to retrieve the service info from the saved
            // bundle.
            service = (SafeService) getIntent().getParcelableExtra(
                SafeService.class.getCanonicalName());
            LOGGER.debug("Got service {} info from intent", service);

            setContentView(R.layout.activity_new_pairing);

            // Add autocomplete for conventional pairing names
            final AutoCompleteTextView textView = (AutoCompleteTextView) findViewById(
                R.id.new_pairing_activity__new_pairing_name);
            final String[] pairingCategorySuggestions =
                getResources().getStringArray(R.array.pairing_category_names);
            final ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                    pairingCategorySuggestions);
            textView.setAdapter(adapter);
        } else {
            LOGGER.error("Failed to get service from intent");
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    /*
     * onClick event for the confirm button
     */
    public abstract void confirmNewPairing(final View view);

    /*
     * onClick event for the cancel button
     */
    public void cancelNewPairing(final View view) {
        finish();
    }
}
