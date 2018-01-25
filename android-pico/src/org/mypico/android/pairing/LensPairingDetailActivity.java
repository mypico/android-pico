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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.mypico.android.db.DbHelper;
import org.mypico.android.core.ReattachTask;
import org.mypico.android.delegate.RulesActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.LensPairingAccessor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView;

import com.google.common.base.Optional;

/**
 * Provide the UI for inspecting the details of a {@link LensPairing}.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 */
public class LensPairingDetailActivity extends Activity {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(LensPairingDetailActivity.class.getSimpleName());
    private static final DateFormat DATE_FORMAT = DateFormat.getDateInstance();

    public static final String PAIRING =
        LensPairingDetailActivity.class.getCanonicalName() + "pairing";
    public static final String SHOW_ON_RESUME =
        LensPairingDetailActivity.class.getCanonicalName() + "visible";

    /**
     * Worker thread for loading the credentials to display in the UI.
     */
    private static class LoadCredentials extends ReattachTask<SafeLensPairing, Void, LensPairing> {

        final LensPairingAccessor accessor;

        protected LoadCredentials(Activity activity, LensPairingAccessor accessor) {
            super(activity);

            this.accessor = checkNotNull(accessor, "accessor cannot be null");
        }

        @Override
        protected LensPairing doInBackground(SafeLensPairing... params) {
            try {
                final SafeLensPairing safePairing = params[0];

                // Retrieve the full pairing from the database
                final LensPairing pairing = safePairing.getLensPairing(accessor);

                // Return credentials
                return pairing;
            } catch (IOException e) {
                LOGGER.warn("IOException while loading credentials", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(LensPairing pairing) {
            Map<String, String> credentials = pairing.getCredentials();
            List<String> privateFields = pairing.getPrivateFields();
            if (credentials != null) {
                // Get adapter for the credentials list view
                ArrayAdapter<String> adapter =
                    ((LensPairingDetailActivity) getActivity()).getCredentialsAdapter();
                Map<Integer, String> privateFieldsValues =
                    ((LensPairingDetailActivity) getActivity()).privateFieldsValues;
                // Populate adapter
                adapter.clear();
                for (Map.Entry<String, String> entry : credentials.entrySet()) {
                    if (privateFields.contains(entry.getKey())) {
                        LOGGER.debug("Adding private entry {}", entry.getKey());
                        privateFieldsValues.put(adapter.getCount(), entry.getKey() + ": " + entry.getValue());
                        adapter.add(entry.getKey() + ": ********");
                    } else {
                        LOGGER.debug("Adding entry {} : {}", entry.getKey(), entry.getValue());
                        adapter.add(entry.getKey() + ": " + entry.getValue());
                    }
                }

                for (String field : privateFields) {
                    LOGGER.debug("Private field {}", field);
                }
                adapter.notifyDataSetChanged();
            }
        }
    }

    private SafeLensPairing pairing;
    private boolean showOnResume = false;
    private Switch credentialsSwitch;
    private ListView credentialsList;
    private ArrayAdapter<String> credentialsAdapter;
    private Optional<LensPairingAccessor> accessor = Optional.absent();
    private Button delegate;
    private Map<Integer, String> privateFieldsValues;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lens_pairing_detail);

        // Get safe lens pairing from received intent
        if (getIntent().hasExtra(PAIRING)) {
            pairing = (SafeLensPairing) getIntent().getParcelableExtra(PAIRING);
        } else {
            LOGGER.warn("safe pairing extra missing, finishing activity");
            finish();
        }

        // Set showOnResume flag if intent contains the corresponding extra
        showOnResume = getIntent().getBooleanExtra(SHOW_ON_RESUME, false);

        // Set text view contents
        final TextView name = (TextView) findViewById(R.id.pairing_name);
        name.setText(pairing.getDisplayName());
        if (pairing.getDateCreated().isPresent()) {
            final TextView created = (TextView) findViewById(R.id.created);
            created.setText(String.format(
                "%s: %s",
                getString(R.string.created_label),
                DATE_FORMAT.format(pairing.getDateCreated().get())));

        }

        // Set up credentials list view (initially hidden) with an adapter (initially empty).
        credentialsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        credentialsList = (ListView) findViewById(R.id.credentials_list);
        credentialsList.setAdapter(credentialsAdapter);
        credentialsList.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
                String value = privateFieldsValues.get(pos);
                if (value != null) {
                    LOGGER.debug("Show field [{}]", pos);
                    credentialsAdapter.remove(credentialsAdapter.getItem(pos));
                    credentialsAdapter.insert(value, pos);
                    return true;
                } else {
                    return false;
                }
            }
        });

        privateFieldsValues = new HashMap<Integer, String>();

        // Set up credentials switch
        credentialsSwitch = (Switch) findViewById(R.id.credentials_switch);
        credentialsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    showCredentials();
                } else {
                    hideCredentials();
                }
            }
        });

        delegate = (Button) findViewById(R.id.delegate_allow);
        delegate.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startDelegation(v);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        try {
            accessor = Optional.of(DbHelper.getInstance(this).getLensPairingAccessor());
        } catch (SQLException e) {
            LOGGER.warn("unable to get an accessor", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Show the hidden login credentials if the showOnResume flag is set and they aren't
        // already visible (because the switch/adapter retain their state through rotations).
        if (showOnResume && !credentialsSwitch.isChecked()) {
            showCredentials();
        }
    }

    /**
     * Get the adapter.
     *
     * @return The credentials adapter.
     */
    private ArrayAdapter<String> getCredentialsAdapter() {
        return credentialsAdapter;
    }

    /**
     * Show the credentials in the UI.
     */
    private void showCredentials() {
        LOGGER.debug("showing credentials...");

        // Ensure switch is checked
        credentialsSwitch.setChecked(true);

        // Make the list view visible
        credentialsList.setVisibility(View.VISIBLE);

        // Start an async task to load the credentials into it
        if (accessor.isPresent()) {
            new LoadCredentials(this, accessor.get()).execute(pairing);
        } else {
            LOGGER.warn("no accessor, cannot load credentials from database");
        }
    }

    /**
     * Hide the credentials from the UI.
     */
    private void hideCredentials() {
        LOGGER.debug("hiding credentials...");

        // Ensure switch is unchecked
        credentialsSwitch.setChecked(false);

        // Clear contents of adapter
        credentialsAdapter.clear();
        credentialsAdapter.notifyDataSetChanged();

        // Hide the list view itself
        credentialsList.setVisibility(View.INVISIBLE);
    }

    /**
     * Start a delegation activity to pasa a credential to another Pico
     *
     * @param v the view for the context
     */
    private void startDelegation(View v) {
//		Context context = v.getContext();
//    	final Intent intent = new Intent(context, DelegateActivity.class);
//
//    	// Store the current pairing info in the intent
//    	intent.putExtra(DelegateActivity.PAIRING, pairing);
//
//    	context.startActivity(intent);

        Context context = v.getContext();
        final Intent intent = new Intent(context, RulesActivity.class);

        // Store the current pairing info in the intent
        intent.putExtra(RulesActivity.PAIRING, pairing);

        context.startActivity(intent);
    }
}
