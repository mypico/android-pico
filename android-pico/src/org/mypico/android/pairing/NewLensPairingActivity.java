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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import com.google.common.base.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.Set;
import java.util.ArrayList;

import org.mypico.android.backup.BackupFactory;
import org.mypico.android.backup.OnConfigureBackupListener;
import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.ParcelableCredentials;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.R;
import org.mypico.android.backup.IBackupProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI for creating and naming a new Pico Lens pairing.
 * Successfully created pairings are persisted to the Pico pairings database
 * using the NewLensPairingIntentService.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see LensPairingIntentService
 */
final public class NewLensPairingActivity extends NewPairingActivity
    implements OnConfigureBackupListener {

    private final static Logger LOGGER = LoggerFactory.getLogger(
        NewLensPairingActivity.class.getSimpleName());
    private final static String[] userNameRegexs =
        {"username", "uname", "email", "user.*", "u.*", ".*id.*"};

    private final IntentFilter intentFilter;
    private final ResponseReceiver responseReceiver = new ResponseReceiver();

    private ParcelableCredentials credentials;
    private ArrayList<String> privateFields;

    {
        // Create an IntentFilter for the actions returned by the NewLensPairingIntentService
        intentFilter = new IntentFilter();
        intentFilter.addAction(LensPairingIntentService.IS_PAIRING_PRESENT_ACTION);
        intentFilter.addAction(LensPairingIntentService.PERSIST_PAIRING_ACTION);
        intentFilter.addAction(LensPairingIntentService.GET_CREDENTIALS_FROM_TERMINAL);
    }

    /**
     * Broadcast receiver for receiving status updates from the NewLensPairingIntentService.
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    private class ResponseReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            LOGGER.error("Receiver on Received {}", intent.getAction());
            if (!intent.hasExtra(LensPairingIntentService.EXCEPTION)) {
                if (intent.getAction().equals(LensPairingIntentService.GET_CREDENTIALS_FROM_TERMINAL)) {
                    credentials = (ParcelableCredentials) intent.getParcelableExtra(LensPairingIntentService.CREDENTIALS);
                    String serviceName = intent.getExtras().getString(LensPairingIntentService.SERVICE_NAME);
                    String serviceAddress = intent.getExtras().getString(LensPairingIntentService.SERVICE_ADDRESS);
                    LOGGER.debug("serviceName={}", serviceName);
                    LOGGER.debug("serviceAddress={}", serviceAddress);
                    privateFields = (ArrayList<String>) intent.getSerializableExtra(LensPairingIntentService.PRIVATE_FIELDS);
                    service = new SafeService(serviceName, service.getCommitment(), Uri.parse(serviceAddress), service.getLogoUri());
                    credentialsReceived();
                } else if (intent.getAction().equals(LensPairingIntentService.IS_PAIRING_PRESENT_ACTION)) {
                    if (intent.getBooleanExtra(LensPairingIntentService.IS_PAIRING_PRESENT_ACTION, false)) {
                        pairingIsPresent();
                    } else {
                        pairingIsNotPresent();
                    }

                } else if (intent.getAction().equals(LensPairingIntentService.PERSIST_PAIRING_ACTION)) {
                    if (intent.getBooleanExtra(LensPairingIntentService.PERSIST_PAIRING_ACTION, false)) {

                        if (intent.hasExtra(LensPairingIntentService.PAIRING)) {
                            final SafeLensPairing pairing =
                                (SafeLensPairing) intent.getParcelableExtra(LensPairingIntentService.PAIRING);
                            pairingIsPersisted(pairing);
                        } else {
                            LOGGER.error("SafeLensPairing not returned by NewLesPairingIntentService");
                            newPairingFailure();
                        }
                    } else {
                        LOGGER.error("Persisting new lens pairing failed");
                        newPairingFailure();
                    }
                } else if (intent.getAction().equals(LensPairingIntentService.AUTHENTICATE_TERMINAL_UNTRUSTED)) {
                    authenticateTerminalUntrusted();
                } else {
                    LOGGER.warn("Unrecognised action {}", intent.getAction());
                    newPairingFailure();
                }
            } else {
                final Bundle extras = intent.getExtras();
                final Throwable exception =
                    (Throwable) extras.getSerializable(LensPairingIntentService.EXCEPTION);
                LOGGER.warn("Exception raise by IntentService {}", exception);
                newPairingFailure();
            }
        }
    }

    /**
     * Called if the user is attempting to create a new lens pairing, but the terminal they are
     * doing it using isn't trusted. This should generate a warning to the user and abort the
     * process.
     */
    private void authenticateTerminalUntrusted() {
        LOGGER.error("Authenticating with {} failed", service);

        // Notify the user and finish the activity
        Toast.makeText(
            this.getApplicationContext(),
            getString(R.string.lens_pairing_failed, service),
            Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Called when the credentials have been received from the terminal.
     */
    private void credentialsReceived() {
        // Update the TextView asking the user to confirm the new pairing with the name of the
        // service (service.getName())
        final TextView descTtextView =
            (TextView) findViewById(R.id.new_pairing_activity__text1);
        final String textViewMsg = getString(R.string.activity_new_lens_pairing__text1, service.getName());
        // Bold the Service's name
        final SpannableStringBuilder str = new SpannableStringBuilder(textViewMsg);
        str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            textViewMsg.indexOf(service.getName()),
            textViewMsg.indexOf(service.getName()) + service.getName().length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        descTtextView.setText(str);

        // Search for a username field (or similar) to add as the default pairing name for
        // the TextView
        String pairingName = getString(R.string.default_pairing_name);
        final Set<String> credKeys = credentials.getCredentials().keySet();
        for (String regex : userNameRegexs) {
            for (String key : credKeys) {
                if (!isNullOrEmpty(key) && key.matches(regex)) {
                    // Lookup the value corresponding to this key
                    pairingName = credentials.getCredentials().get(key);
                    LOGGER.debug("Found {} ={}", key, pairingName);

                    // Change the default text for the AutoCompleteTextView used to specify
                    // the names of new Lens pairings
                    final AutoCompleteTextView textView = (AutoCompleteTextView) findViewById(
                        R.id.new_pairing_activity__new_pairing_name);
                    if (pairingName.contains("@")) {
                        // Assumed that the username is an email address;
                        // ignore everything after the @ character
                        pairingName = pairingName.split("@")[0];
                    }
                    textView.setText(pairingName);
                    break;
                }
            }
        }

        final Intent iintent = new Intent(this, LensPairingIntentService.class);
        iintent.putExtra(LensPairingIntentService.SERVICE, service);
        iintent.putExtra(LensPairingIntentService.CREDENTIALS, credentials);
        iintent.putExtra(LensPairingIntentService.PRIVATE_FIELDS, privateFields);
        iintent.setAction(LensPairingIntentService.IS_PAIRING_PRESENT_ACTION);
        startService(iintent);
    }

    /**
     * Triggered in case a lens pairing of this sort already exists in the database.
     */
    private void pairingIsPresent() {
        LOGGER.debug("Pairing {} is already present");
        Toast.makeText(this.getApplicationContext(),
            getString(R.string.lens_pairing_already_exisits, service.getName()),
            Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * Triggered in case a lens pairing is not already present in the database.
     */
    private void pairingIsNotPresent() {
        LOGGER.debug("Pairing is not already present");
        findViewById(R.id.new_pairing_activity__layout).setVisibility(LinearLayout.VISIBLE);
    }

    /**
     * Once the pairing has been stored, this is called to notify the user and allow a backup
     * to be performed.
     *
     * @param pairing The lens pairing that has been stored in the database.
     */
    private void pairingIsPersisted(final SafeLensPairing pairing) {
        LOGGER.debug("Persisting new lens pairing succeeded");

        // Notify the user and finish the activity
        Toast.makeText(
            this.getApplicationContext(),
            getString(R.string.lens_pairing_success, pairing.getSafeService()),
            Toast.LENGTH_SHORT).show();

        // Backup the data store containing the new Lens pairing 
        final Optional<IBackupProvider> backupProvider =
            BackupFactory.newBackup(NewLensPairingActivity.this);
        if (!backupProvider.isPresent()) {
            LOGGER.warn("No backup provider is configured");
            finish();
        }
    }

    /**
     * Triggered in case an error occurs creating a new pairing.
     */
    private void newPairingFailure() {
        // Notify the user and finish the activity
        Toast.makeText(
            this.getApplicationContext(),
            getString(R.string.lens_pairing_failed, service),
            Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGGER.trace("onCreate NewLensPairingActivity");

        // Register the BraodcastReceiver, this must be unregistered in the onDestroy lifecycle
        // method
        LocalBroadcastManager.getInstance(this).registerReceiver(responseReceiver, intentFilter);

        // Check whether a pairing for this account and service are already persisted in
        // the Pico pairings database. Until this check is finished, set the whole layout to
        // invisible (prevents any confusing stuff flashing up on the screen).
        findViewById(R.id.new_pairing_activity__layout).setVisibility(LinearLayout.INVISIBLE);
        final Intent intent = new Intent(this, LensPairingIntentService.class);
        intent.putExtra(LensPairingIntentService.TERMINAL_COMMITMENT,
            getIntent().getByteArrayExtra(VisualCodeIntentGenerator.TERMINAL_COMMITMENT));
        intent.putExtra(LensPairingIntentService.TERMINAL_ADDRESS,
            getIntent().getParcelableExtra(VisualCodeIntentGenerator.TERMINAL_ADDRESS));
        intent.setAction(LensPairingIntentService.GET_CREDENTIALS_FROM_TERMINAL);
        startService(intent);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(responseReceiver);
        super.onDestroy();
    }

    /*
     * onClick event for the confirm button
     */
    @Override
    public void confirmNewPairing(final View view) {
        // Get the user's desired human-readable pairing name
        final EditText nameEdit = (EditText) findViewById(
            R.id.new_pairing_activity__new_pairing_name);

        String newPairingName = nameEdit.getText().toString();
        if (isNullOrEmpty(newPairingName)) {
            newPairingName = getString(R.string.default_pairing_name);
        }

        // Persist the new pairing using the NewLensPairingIntentService
        final SafeLensPairing emptyPairing = new SafeLensPairing(newPairingName, service);

        final Intent intent = new Intent(this, LensPairingIntentService.class);
        intent.putExtra(LensPairingIntentService.PAIRING, emptyPairing);
        intent.putExtra(LensPairingIntentService.CREDENTIALS, credentials);
        intent.putExtra(LensPairingIntentService.PRIVATE_FIELDS, privateFields);
        intent.setAction(LensPairingIntentService.PERSIST_PAIRING_ACTION);
        startService(intent);
    }

    @Override
    public void onConfigureBackupSuccess(final IBackupProvider backupProvider) {
        // Verify the method's preconditions
        checkNotNull(backupProvider);

        LOGGER.debug("Configuring backup successful");
        // Perform the backup
        backupProvider.backup();
        finish();
    }

    @Override
    public void onConfigureBackupCancelled() {
        LOGGER.error("Configuring backup cancelled");
        finish();
    }

    @Override
    public void onConfigureBackupFailure() {
        LOGGER.error("Configuring backup failed");
        finish();
    }
}
