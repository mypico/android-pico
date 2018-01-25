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

import java.util.ArrayList;
import java.util.Arrays;

import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.backup.BackupFactory;
import org.mypico.android.data.SafeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.android.backup.IBackupProvider;
import org.mypico.android.backup.OnConfigureBackupListener;

import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.base.Optional;

/**
 * UI for creating and naming a new Pico pairing.
 * Successfully created pairings are persisted to the Pico pairings database
 * using the NewKeyPairingIntentService.
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see KeyPairingIntentService
 */
final public class NewKeyPairingActivity extends NewPairingActivity
    implements OnConfigureBackupListener {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(NewKeyPairingActivity.class.getSimpleName());

    private final static int AUTHENTICATE_RESULT_CODE = 1;

    protected SafePairing returnedPairing = null;

    /*
     * onClick event for the confirm button
     */
    @Override
    public void confirmNewPairing(final View view) {
        LOGGER.debug("confirmNewPairing clicked");

        if (returnedPairing == null) {
            LOGGER.warn("Should have set returnedPairing in onActivityResult");
            finish();
            return;
        }

        // Get the user's desired human-readable pairing name
        final EditText nameEdit = (EditText) findViewById(
            R.id.new_pairing_activity__new_pairing_name);

        String newPairingName = nameEdit.getText().toString();
        if (newPairingName.isEmpty()) {
            newPairingName = getResources().getString(R.string.default_pairing_name);
        }
        LOGGER.debug("newPairingName={}", newPairingName);

        final Intent changeName = new Intent(this, PairingsIntentService.class);
        changeName.setAction(PairingsIntentService.CHANGE_PAIRING_NAME_ACTION);
        changeName.putExtra(PairingsIntentService.PAIRING, returnedPairing);
        changeName.putExtra(String.class.getCanonicalName(), newPairingName);
        startService(changeName);

        // Backup the pairing
        LOGGER.info("Backing up the database with the new pairing {}", returnedPairing);
        // Backup the data store containing the new Key pairing
        final Optional<IBackupProvider> backupProvider =
            BackupFactory.newBackup(NewKeyPairingActivity.this);
        if (!backupProvider.isPresent()) {
            LOGGER.warn("No backup provider is configured");
            finish();
        }

    }

    @Override
    protected void onActivityResult(
        final int requestCode, final int resultCode, final Intent data) {

        if (requestCode == AUTHENTICATE_RESULT_CODE) {
            // Extract the keyPairing from the Intent data
            if (data != null && data.hasExtra(SafeSession.class.getCanonicalName())) {

                final SafeKeyPairing safeKeyPairing = (SafeKeyPairing) data.getParcelableExtra(
                    SafeKeyPairing.class.getCanonicalName());
                returnedPairing = safeKeyPairing;

                if (resultCode == RESULT_OK && !returnedPairing.getName().equals("")) {
                    LOGGER.info("Authentication successful. Username already set. Finishing");
                    final Optional<IBackupProvider> backupProvider =
                        BackupFactory.newBackup(NewKeyPairingActivity.this);
                    if (!backupProvider.isPresent()) {
                        LOGGER.warn("No backup provider is configured");
                        finish();
                    }
                } else if (resultCode == RESULT_OK) {
                    LOGGER.info("Authentication successful. asking the user to set name");

                    // Update the TextView asking the user to confirm the new pairing with the name of the
                    // service (service.getName())
                    final TextView descTtextView =
                        (TextView) findViewById(R.id.new_pairing_activity__text1);
                    final String textViewFmt = getString(R.string.activity_new_key_pairing__text1);
                    final String textViewMsg = String.format(textViewFmt, service.getName());
                    // Bold the Service's name
                    final SpannableStringBuilder str = new SpannableStringBuilder(textViewMsg);
                    str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        textViewMsg.indexOf(service.getName()),
                        textViewMsg.indexOf(service.getName()) + service.getName().length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    descTtextView.setText(str);
                } else {
                    // Delete the pairing
                    LOGGER.debug("Authentication failed deleting the pairing {}", safeKeyPairing);

                    final Intent intent = new Intent(this, PairingsIntentService.class);
                    intent.putParcelableArrayListExtra(PairingsIntentService.PAIRING,
                        (ArrayList<SafeKeyPairing>) Arrays.asList(safeKeyPairing));
                    intent.setAction(PairingsIntentService.DELETE_PAIRINGS_ACTION);
                    startService(intent);
                }
            } else {
                LOGGER.error("AuthenticationActivity did not return a valid session returned");
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            LOGGER.error("Unrecognized requestCode");
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGGER.trace("onCreate NewKeyPairingActivity");

        // Call AuthenticateActivity to create the Pairing
        final SafeKeyPairing emptyPairing = new SafeKeyPairing("", service);

        final Intent authIntent = new Intent(
            NewKeyPairingActivity.this, AuthenticateActivity.class);
        authIntent.putExtra(SafeKeyPairing.class.getCanonicalName(), emptyPairing);

        // Also include all of the extras from the received intent to forward the terminal
        // details if they are present
        authIntent.putExtras(getIntent());

        // Flag that this is a pairing process so that the correct messages are displayed
        authIntent.putExtra(AuthenticateActivity.IS_PAIRING_EXTRA, true);

        // Setting this flag means that the next activity will pass any
        // result back to the result target of this activity.
        startActivityForResult(authIntent, AUTHENTICATE_RESULT_CODE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConfigureBackupSuccess(final IBackupProvider backupProvider) {
        // Verify the method's preconditions
        checkNotNull(backupProvider);

        LOGGER.debug("Configuring backup successful");
        // Perform the backup
        backupProvider.backup();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onConfigureBackupCancelled() {
        LOGGER.error("Configuring backup cancelled");
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onConfigureBackupFailure() {
        LOGGER.error("Configuring backup failed");
        setResult(RESULT_CANCELED);
        finish();
    }
}
