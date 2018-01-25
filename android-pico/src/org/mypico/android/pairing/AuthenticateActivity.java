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
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.mypico.android.core.AcquireCodeActivity;
import org.mypico.android.data.ParcelableAuthToken;
import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.data.SafeSession;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import org.mypico.android.R;
import org.mypico.android.bluetooth.PicoBluetoothService;
import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.jpico.comms.org.apache.commons.codec.binary.Base64;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.crypto.AuthTokenFactory;
import org.mypico.jpico.crypto.NewSigmaProver;
import org.mypico.jpico.crypto.ProtocolViolationException;
import org.mypico.jpico.db.DbDataAccessor;

/**
 * Activity for performing the authentication and the UI when authentication takes place.
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class AuthenticateActivity extends Activity
    implements LensPairingListFragment.Listener, DialogInterface.OnDismissListener {

    static private final Logger LOGGER = LoggerFactory.getLogger(
        AuthenticateActivity.class.getSimpleName());
    static private final String SERVICE = "SERVICE";
    static private final String PAIRING = "PAIRING";
    static private final String TERMINAL_SHARED_KEY = "TERMINAL_SHARED_KEY";
    static private final String PAIRING_LIST_FRAGMENT = "PAIRING_LIST_FRAGMENT";

    /**
     * Extra boolean that indicates whether this authentication process is for pairing
     */
    public static final String IS_PAIRING_EXTRA = "IS_PAIRING_EXTRA";

    /**
     * Broadcast intent that advertises a successful authentication
     */
    public static final String AUTHENTICATION_SUCCESS_BROADCAST =
        AuthenticateActivity.class.getCanonicalName() + ".AUTHENTICATION_SUCCESS_BROADCAST";

    private SafePairing pairing;
    private SafeService service;
    private byte[] terminalSharedKey;
    private String loginForm;
    private String cookieString;
    private AuthToken token;
    private Throwable exception;

    private boolean active;
    // Id used to check if the received broadcast is and answer to the current
    // request or if it is some old Intent.
    private static int currentId = 0;

    private final IntentFilter intentFilter = new IntentFilter();
    private final ResponseReceiver responseReceiver = new ResponseReceiver();

    private ProgressBar authProgressBar;
    private TextView authProgressText;

    /**
     * Broadcast receiver for local {@link AuthenticateIntentService#AUTH_PROGRESS_ACTION} intents,
     * that receives progress updates from {@link AuthenticateIntentService}.
     *
     * @see AuthenticateIntentService#progressCallback
     */
    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // get stage info out of the Intent
            final int progress = intent.getIntExtra(AuthenticateIntentService.PROGRESS_EXTRA, 0);
            final int maxProgress = intent.getIntExtra(AuthenticateIntentService.MAX_PROGRESS_EXTRA, 0);
            final String description = intent.getStringExtra(AuthenticateIntentService.DESCRIPTION_EXTRA);
            // update the progress bar
            authProgressBar.setProgress(progress);
            if (authProgressBar.isIndeterminate()) {
                authProgressBar.setMax(maxProgress);
                authProgressBar.setIndeterminate(false);
            }
            // update the feedback message
            authProgressText.setText(description);
        }
    };

    {
        intentFilter.addAction(AuthenticateIntentService.AUTHENTICATE_TERMINAL_ACTION);
        intentFilter.addAction(AuthenticateIntentService.AUTHENTICATE_PAIRING_ACTION);
        intentFilter.addAction(AuthenticateIntentService.AUTHENTICATE_DELEGATED);
        intentFilter.addAction(LensPairingIntentService.GET_LENS_PAIRINGS_ACTION);
    }

    /**
     * Broadcast receiver for receiving status updates from the KeypairingIntentService and LensPairingIntentService.
     *
     * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
     */
    private class ResponseReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (!intent.hasExtra(AuthenticateIntentService.EXCEPTION)) {
                if (intent.getAction().equals(AuthenticateIntentService.AUTHENTICATE_TERMINAL_ACTION)) {
                    if (intent.hasExtra(AuthenticateIntentService.EXTRA_DATA) &&
                        intent.hasExtra(AuthenticateIntentService.TERMINAL_SHARED_KEY)) {
                        final byte[] extraData = intent.getByteArrayExtra(AuthenticateIntentService.EXTRA_DATA);
                        terminalSharedKey = intent.getByteArrayExtra(AuthenticateIntentService.TERMINAL_SHARED_KEY);

                        try {
                            LOGGER.debug("extraData = {}", new String(extraData));
                            // TODO: tidy this up with a set of extra data classes
                            final JSONObject obj = new JSONObject(new String(extraData));
                            final String serviceAddress = obj.getString("sa");
                            LOGGER.debug("sa = {}", serviceAddress);
                            final String serviceCommitment = obj.getString("sc");
                            LOGGER.debug("sc = {}", serviceCommitment);
                            loginForm = obj.getString("lf");
                            LOGGER.debug("lf = {}", loginForm);
                            cookieString = obj.getString("cs");
                            LOGGER.debug("cs = {}", cookieString);

                            // Return the Service to authenticate to
                            service = new SafeService(null,
                                Base64.decodeBase64(serviceCommitment),
                                Uri.parse(serviceAddress),
                                null);

                            // Query whether Pico is already paired with this service
                            LOGGER.debug("Querying Pico's pairings with service {}", serviceAddress);

                            final Intent requestIntent;
                            requestIntent = new Intent(AuthenticateActivity.this, LensPairingIntentService.class);
                            requestIntent.putExtra(LensPairingIntentService.SERVICE, service);
                            requestIntent.setAction(LensPairingIntentService.GET_LENS_PAIRINGS_ACTION);
                            startService(requestIntent);
                        } catch (JSONException e) {
                            LOGGER.error("The received extraData does not parse as JSON", e);
                            authenticateFailed();
                        }
                    } else {
                        LOGGER.error("Intent {} doesn't contain required extras", intent);
                        authenticateFailed();
                    }
                } else if (intent.getAction().equals(AuthenticateIntentService.AUTHENTICATE_TERMINAL_UNTRUSTED)) {
                    authenticateTerminalUntrusted();
                } else if (intent.getAction().equals(LensPairingIntentService.GET_LENS_PAIRINGS_ACTION)) {
                    if (intent.hasExtra(LensPairingIntentService.PAIRINGS)) {
                        final ArrayList<SafeLensPairing> pairings =
                            intent.getParcelableArrayListExtra(LensPairingIntentService.PAIRINGS);
                        if (pairings.isEmpty()) {
                            // No pairings with the service
                            noLensPairings();
                        } else {
                            if (pairings.size() == 1) {
                                // Single pairing with the service - authenticate using this pairing
                                SafeLensPairing pairing = pairings.get(0);
                                // TODO: Figure out a better way to manage the pairing and service class variables
                                service = pairing.getSafeService();

                                OrmLiteSqliteOpenHelper helper = OpenHelperManager.getHelper(context, DbHelper.class);

                                try {
                                    // TODO: Figure out a sensible place to store the AuthToken pairings
                                    DbDataAccessor dbDataAccessor = new DbDataAccessor(helper.getConnectionSource());
                                    Map<String, String> credentials = pairing.getLensPairing(dbDataAccessor).getCredentials();
                                    if (credentials.size() == 1 && credentials.containsKey("AuthToken")) {
                                        String tokenString = credentials.get("AuthToken");
                                        final byte[] tokenStringBytes = Base64.decodeBase64(tokenString);
                                        AuthToken token = AuthTokenFactory.fromByteArray(tokenStringBytes);
                                        authenticateToService(token, pairing);
                                    } else {
                                        authenticateToService(pairing);
                                    }
                                } catch (SQLException e) {
                                    LOGGER.warn("Failed to connect to database");
                                } catch (IOException e) {
                                    LOGGER.warn("IOException searching for pairing in database");
                                }
                            } else {
                                // Multiple pairings with this service
                                multipleLensPairings(pairings);
                            }
                        }
                    } else {
                        LOGGER.error("Intent {} doesn't contain required extras", intent);
                        authenticateFailed(service);
                    }
                } else if (intent.getAction().equals(AuthenticateIntentService.AUTHENTICATE_PAIRING_ACTION)) {
                    if (intent.hasExtra(AuthenticateIntentService.SESSION) && intent.hasExtra(AuthenticateIntentService.PAIRING)) {
                        final SafeSession session = intent.getParcelableExtra(AuthenticateIntentService.SESSION);
                        final SafeKeyPairing keyPairing = intent.getParcelableExtra(AuthenticateIntentService.PAIRING);
                        LOGGER.debug("Authentication successful. Received KeyPairing");
                        authenticateSuccess(session, keyPairing);
                    } else if (intent.hasExtra(AuthenticateIntentService.SESSION)) {
                        final SafeSession session = intent.getParcelableExtra(AuthenticateIntentService.SESSION);
                        LOGGER.debug("Authentication successful");
                        authenticateSuccess(session);
                    } else {
                        LOGGER.error("Intent {} doesn't contain required extras", intent);
                        authenticateFailed(pairing);
                    }
                } else if (intent.getAction().equals(AuthenticateIntentService.AUTHENTICATE_PAIRING_DELEGATION_FAILED)) {
                    if (intent.hasExtra(AuthenticateIntentService.SESSION)) {
                        final SafeSession session = intent.getParcelableExtra(AuthenticateIntentService.SESSION);
                        authenticatPairingDelegationFailed(session);
                    } else {
                        LOGGER.error("Intent {} doesn't contain required extras", intent);
                        authenticateFailed(pairing);
                    }
                } else if (intent.getAction().equals(AuthenticateIntentService.AUTHENTICATE_DELEGATED)) {
                    if (intent.hasExtra(AuthenticateIntentService.SERVICE)) {
                        final SafeService service = intent.getParcelableExtra(AuthenticateIntentService.SERVICE);
                        LOGGER.debug("Authentication successful");
                        authenticateSuccess(service);
                    } else {
                        LOGGER.error("Intent {} doesn't contain required extras", intent);
                        authenticateFailed(service);
                    }
                } else {
                    LOGGER.error("Unrecognised action {}", intent.getAction());
                    authenticateFailed(service);
                }
            } else {
                final Bundle extras = intent.getExtras();
                exception = (Throwable) extras.getSerializable(PairingsIntentService.EXCEPTION);
                final int activityId = extras.getInt(AuthenticateIntentService.ACTIVITY_ID, -1);
                LOGGER.info("activityId {}. Current id {}", activityId, currentId);
                LOGGER.error("Exception raise by IntentService.", exception);
                // show the exception text in the window - useful when debugging
                showExceptionMessage(exception);
                // We will show some message to the user only if the activity is running (active)
                // Also, the activityId in the parcel HAS to be the same as the current activity
                // otherwise it means this was some old exception that should be ignored.
                // Note: Every intent from exception should have an ACTIVITY_ID. If, for
                // some bug this happens, activityId will be -1 and we handle anyway.
                if (active && (activityId == currentId || activityId == -1)) {
                    if (exception instanceof IOException) {
                        authenticateFailedShowMessage(getExceptionActionableFeedback(exception));
                    } else if (intent.hasExtra(AuthenticateIntentService.SESSION)) {
                        final SafeSession session =
                            (SafeSession) intent.getParcelableExtra(AuthenticateIntentService.SESSION);
                        delegateFailed(session);
                    } else {
                        if (service != null) {
                            authenticateFailed(service);
                        } else if (pairing != null) {
                            authenticateFailed(pairing);
                        } else {
                            authenticateFailed();
                        }
                    }
                } else {
                    finish();
                }
            }
        }
    }

    /**
     * Called in case a delegation from one Pico to another fails.
     *
     * @param session The session performing the delegation.
     */
    private void authenticatPairingDelegationFailed(final SafeSession session) {
        LOGGER.error("Authenticating with {} failed", service);

        // Hide the progress spinner since no more work is being done
        hideSpinner();

        // Display a dialog sourced from the service
        DelegationFailedHere.getInstance(pairing, new ParcelableAuthToken(session.getAuthToken()))
            .show(getFragmentManager(), "authFailedDialog");
    }

    /**
     * Perform an authentication.
     *
     * @param pairing The pairing to authenticate using.
     */
    private void authenticateToService(final SafePairing pairing) {
        LOGGER.debug("Authenticating with service {}: pairing = {}",
            service, pairing);
        this.pairing = pairing;
        showAutenticatingTo(service);

        final Intent requestIntent =
            new Intent(getIntent());
        requestIntent.setClass(AuthenticateActivity.this, AuthenticateIntentService.class);
        requestIntent.putExtra(AuthenticateIntentService.PAIRING, pairing);
        requestIntent.putExtra(AuthenticateIntentService.SERVICE, service);
        requestIntent.putExtra(AuthenticateIntentService.TERMINAL_ADDRESS,
            getIntent().getParcelableExtra(VisualCodeIntentGenerator.TERMINAL_ADDRESS));
        requestIntent.putExtra(AuthenticateIntentService.TERMINAL_SHARED_KEY, terminalSharedKey);
        requestIntent.putExtra(AuthenticateIntentService.LOGIN_FORM, loginForm);
        requestIntent.putExtra(AuthenticateIntentService.COOKIE_STRING, cookieString);
        requestIntent.setAction(AuthenticateIntentService.AUTHENTICATE_PAIRING_ACTION);
        startService(requestIntent);
    }

    /**
     * Perform an authentication. In this case authentication is performed not using keys or
     * password credentials, but rather a cookie delegated from one Pico to another.
     *
     * @param token   The token to use for authentication.
     * @param pairing The pairing.
     */
    private void authenticateToService(final AuthToken token, final SafePairing pairing) {
        LOGGER.debug("Authenticating with service {}: token = {}",
            service, token.getFull());
        this.token = token;
        showAutenticatingTo(service);

        final Intent requestIntent =
            new Intent(AuthenticateActivity.this, AuthenticateIntentService.class);
        final ParcelableAuthToken authtoken = new ParcelableAuthToken(token);
        requestIntent.putExtra(AuthenticateIntentService.AUTHTOKEN, authtoken);
        requestIntent.putExtra(AuthenticateIntentService.TERMINAL_ADDRESS,
            getIntent().getParcelableExtra(VisualCodeIntentGenerator.TERMINAL_ADDRESS));
        requestIntent.putExtra(AuthenticateIntentService.TERMINAL_SHARED_KEY, terminalSharedKey);
        requestIntent.setAction(AuthenticateIntentService.AUTHENTICATE_DELEGATED);
        requestIntent.putExtra(AuthenticateIntentService.PAIRING, pairing);
        requestIntent.putExtra(AuthenticateIntentService.SERVICE, service);
        startService(requestIntent);
    }

    /**
     * Triggered in case no lens pairing exists for the authentication.
     */
    private void noLensPairings() {
        LOGGER.debug("Pico is not paired with the service {}", service);

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

    /**
     * Triggered in case there are multiple potential pairings that could be used for the
     * authentication. In this case, the UI should allow the user to choose a pairing.
     *
     * @param pairings The possible pairings that apply.
     */
    private void multipleLensPairings(final ArrayList<SafeLensPairing> pairings) {
        LOGGER.debug("Pairings with {}={}", service, pairings);

        // Hid the progress spinner whilst selecting from a list of pairings
        hideSpinner();

        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        final LensPairingListFragment fragment = LensPairingListFragment.newInstance(pairings, service);
        fragmentTransaction.add(R.id.lens_pairings_fragment, fragment, PAIRING_LIST_FRAGMENT);
        fragmentTransaction.commit();
        findViewById(R.id.lens_pairings_fragment).setVisibility(View.VISIBLE);
    }


    /**
     * Called if authentication fails, so that an appropriate message can be shown to the user.
     *
     * @param message The message to show.
     */
    private void authenticateFailedShowMessage(final String message) {
        LOGGER.error("IOException made the connection fail");

        // Hide the progress spinner since no more work is being done
        hideSpinner();

        // Display a dialog sourced from the service
        AuthFailedHere.getInstance(new AuthFailedDialog.AuthFailedSource() {
            @Override
            public Dialog getAuthFailedDialog(Activity context, String userMsg) {
                // Build alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(message);
                builder.setPositiveButton(R.string.ok, null);
                return builder.create();
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
            }
        }, null).show(getFragmentManager(), "authFailedDialog");
    }


    /**
     * Called if an authentication is attempted using an untrusted terminal. In this case the
     * authentication should not be allowed to proceed, and a message explaining the situation
     * shown to the user.
     */
    private void authenticateTerminalUntrusted() {
        LOGGER.error("Authenticating with {} failed", service);

        // Hide the progress spinner since no more work is being done
        hideSpinner();

        // Display a dialog sourced from the service
        AuthFailedHere.getInstance(service, null)
            .show(getFragmentManager(), "authFailedDialog");
    }

    /**
     * Called if authentication fails.
     */
    private void authenticateFailed() {
        LOGGER.error("Authenticating failed");

        // Hide the progress spinner since no more work is being done
        hideSpinner();

        // Display a dialog sourced from the service
        AuthFailedHere.getInstance(
            new SafeService(getString(R.string.service_unknown).toLowerCase(),
                null, null, null),
            getExceptionActionableFeedback(exception))
            .show(getFragmentManager(), "authFailedDialog");
    }

    /**
     * Called if authentication fails.
     *
     * @param source The context of the failure, including a user message.
     */
    private void authenticateFailed(final AuthFailedDialog.AuthFailedSource source) {
        LOGGER.error("Authenticating with {} failed", service);

        // Hide the progress spinner since no more work is being done
        hideSpinner();

        // Display a dialog sourced from the service
        AuthFailedHere.getInstance(source, getExceptionActionableFeedback(exception))
            .show(getFragmentManager(), "authFailedDialog");
    }

    /**
     * Called in case a delegation process fails.
     *
     * @param session The session associated with the delegation that has failed.
     */
    private void delegateFailed(final SafeSession session) {
        LOGGER.error("Authenticating with {} failed", service);

        // Hide the progress spinner since no more work is being done
        hideSpinner();

        // Display a dialog sourced from the service
        DelegationFailedHere.getInstance(pairing, new ParcelableAuthToken(session.getAuthToken()))
            .show(getFragmentManager(), "authFailedDialog");
    }

    /**
     * Called in case the authentication succeeds.
     *
     * @param session The session associated with the authentication.
     * @param pairing The pairing associated with the authentication.
     */
    private void authenticateSuccess(final SafeSession session, final SafeKeyPairing pairing) {
        // In either case, this is considered a successful authentication
        successToast(pairing.getSafeService());

        // Broadcast that the authentication was successful
        broadcastSuccess();

        // Create result intent and add session
        final Intent resultIntent = new Intent();
        resultIntent.putExtra(SafeSession.class.getCanonicalName(), session);
        resultIntent.putExtra(SafeKeyPairing.class.getCanonicalName(), pairing);
        setResult(Activity.RESULT_OK, resultIntent);

        // Finish this activity
        finish();
    }

    /**
     * Called in case the authentication succeeds.
     *
     * @param session The session associated with the authentication.
     */
    private void authenticateSuccess(final SafeSession session) {
        // In either case, this is considered a successful authentication
        successToast(pairing.getSafeService());

        // Broadcast that the authentication was successful
        broadcastSuccess();

        // Create result intent and add session
        final Intent resultIntent = new Intent();
        resultIntent.putExtra(SafeSession.class.getCanonicalName(), session);
        setResult(Activity.RESULT_OK, resultIntent);

        // Finish this activity
        finish();
    }

    /**
     * Called in case the authentication succeeds.
     *
     * @param service The service associated with the authentication.
     */
    private void authenticateSuccess(final SafeService service) {
        // In either case, this is considered a successful authentication
        successToast(service);

        // Broadcast that the authentication was successful
        broadcastSuccess();

        // Create result intent and add service
        // TODO: This used to return a session, now it's a service. Does this matter?
        final Intent resultIntent = new Intent();
        resultIntent.putExtra(SafeService.class.getCanonicalName(), service);
        setResult(Activity.RESULT_OK, resultIntent);

        // Finish this activity
        finish();
    }

    /**
     * Broadcast the success of an authentication to other activities.
     */
    private void broadcastSuccess() {
        // Broadcast that the authentication was successful
        final Intent broadcast = new Intent(AUTHENTICATION_SUCCESS_BROADCAST);
        LOGGER.debug("Broadcasting: " + AUTHENTICATION_SUCCESS_BROADCAST);

        // Ideally this would be a sent as a local broadcast for security reasons, but it's not
        // possible to register receivers for local braodcasts in the manifest (used by
        // PicoPebbleControl). See http://stackoverflow.com/a/23366125
        // Are the security implications a concern?
        sendBroadcast(broadcast);
        //LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    /**
     * Hide the 'waiting' spinner that shows during authentication.
     */
    private void hideSpinner() {
        final ProgressBar spinner = (ProgressBar) findViewById(
            R.id.activity_authenticate__spinner);
        spinner.setVisibility(View.GONE);
    }

    /**
     * Show the 'waiting' spinner that shows during authentication.
     */
    private void showSpinner() {
        final ProgressBar spinner = (ProgressBar) findViewById(
            R.id.activity_authenticate__spinner);
        spinner.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LOGGER.info("onResume");
        // Register mMessageReceiver to receive messages.
        active = true;
    }

    @Override
    protected void onPause() {
        // Unregister since the activity is not visible
        LOGGER.info("onPause");
        active = false;
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        active = true;
        super.onCreate(savedInstanceState);
        currentId++;

        setContentView(R.layout.activity_authenticate);

        authProgressText = (TextView) findViewById(R.id.current_step);
        authProgressBar = (ProgressBar) findViewById(R.id.progress);

        // Register the BroadcastReceiver that receives responses from
        // the TermianlIntentService (unregistered in the onDestroy lifecycle method)
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(responseReceiver, intentFilter);
        // register receiver for progress feedback
        broadcastManager.registerReceiver(progressReceiver,
            new IntentFilter(AuthenticateIntentService.AUTH_PROGRESS_ACTION));

        if (savedInstanceState == null) {
            final Intent intent = getIntent();
            if (intent.hasExtra(SafeKeyPairing.class.getCanonicalName())) {
                // Old style Key authenticate

                pairing = intent.getParcelableExtra(SafeKeyPairing.class.getCanonicalName());
                showAutenticatingTo(pairing.getSafeService());

                final Intent requestIntent =
                    new Intent(AuthenticateActivity.this, AuthenticateIntentService.class);
                // Include all of the extras from the received intent to forward the terminal details
                // if they are present
                requestIntent.putExtras(getIntent());
                requestIntent.putExtra(AuthenticateIntentService.ACTIVITY_ID, currentId);
                requestIntent.setAction(AuthenticateIntentService.AUTHENTICATE_KEY_PAIRING_ACTION);
                startService(requestIntent);
            } else {
                // Show the progress spinner whilst authenticating to the terminal
                showSpinner();

                // Authenticate to the Terminal; once authenticated the channel is used to transmit the
                // commitment and address of the service to authenticate to
                final Intent requestIntent = new Intent(this, AuthenticateIntentService.class);
                requestIntent.putExtra(AuthenticateIntentService.ACTIVITY_ID, currentId);
                requestIntent.putExtra(AuthenticateIntentService.TERMINAL_COMMITMENT,
                    getIntent().getByteArrayExtra(VisualCodeIntentGenerator.TERMINAL_COMMITMENT));
                requestIntent.putExtra(AuthenticateIntentService.TERMINAL_ADDRESS,
                    getIntent().getParcelableExtra(VisualCodeIntentGenerator.TERMINAL_ADDRESS));
                requestIntent.setAction(AuthenticateIntentService.AUTHENTICATE_TERMINAL_ACTION);
                startService(requestIntent);
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        service = savedInstanceState.getParcelable(SERVICE);
        pairing = savedInstanceState.getParcelable(PAIRING);
        terminalSharedKey = savedInstanceState.getByteArray(TERMINAL_SHARED_KEY);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(SERVICE, service);
        savedInstanceState.putParcelable(PAIRING, pairing);
        savedInstanceState.putByteArray(TERMINAL_SHARED_KEY, terminalSharedKey);
    }

    /**
     * Show details to the user of the fact that an authentication is taking place.
     *
     * @param service The service being authenticated to.
     */
    private void showAutenticatingTo(SafeService service) {
        // Show the progress spinner
        showSpinner();

        // Set the "Authenticating to ..." text
        final boolean isPairing = getIntent().getBooleanExtra(IS_PAIRING_EXTRA, false);
        final TextView v = (TextView) findViewById(R.id.activity_authenticate__authenticating_to);
        final String t = String.format(
            getString(isPairing ?
                R.string.pairing_with_fmt :
                R.string.authenticating_to_fmt),
            service);
        v.setText(t);
    }

    /**
     * If an exeption occurs, notify the user.
     *
     * @param e The exception.
     */
    private void showExceptionMessage(Throwable e) {
        // hide the progress spinner
        hideSpinner();

        // for debugging
        /*final String message = e.getMessage() == null ?
				getString(R.string.auth_failure_details_short,
						e.getClass().getSimpleName()) :
				getString(R.string.auth_failure_details,
						e.getClass().getSimpleName(), e.getMessage());*/
        // for release
        final String message = getString(R.string.auth_failed);

        // set the message text
        ((TextView) findViewById(R.id.activity_authenticate__authenticating_to)).setText(message);
    }

    /**
     * Return a string that explains to the user why the authentication failed and what they
     * can do about it.
     *
     * @param e The exception.
     * @return A string explaining what went wrong and how the user could conceivably address it.
     */
    private String getExceptionActionableFeedback(Throwable e) {
        // check AuthenticateIntentService#authenticatePairing to see what can be thrown
        if (e instanceof ProtocolViolationException) {
            return getString(R.string.user_feedback_protocol_violation);
        } else if (e instanceof NewSigmaProver.VerifierAuthFailedException) {
            return getString(R.string.user_feedback_verifier_auth_failed);
        } else if (e instanceof NewSigmaProver.ProverAuthRejectedException) {
            return getString(R.string.user_feedback_prover_auth_failed);
        } else if (e instanceof NoSuchMethodException || e instanceof InvocationTargetException) {
            return getString(R.string.user_feedback_bt_reflection_exceptions);
        } else if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
            return getString(R.string.user_feedback_bad_rendezvous_url);
        } else if (e instanceof SocketTimeoutException) {
            return getString(R.string.timeout_error_message);
        } else if (e instanceof IOException) {
            return getString(R.string.io_error_message);
        }
        return null;
    }

    /**
     * Display an authentication success message using a toast widget.
     *
     * @param service The service to show the success message for.
     */
    private void successToast(SafeService service) {
        final boolean isPairing = getIntent().getBooleanExtra(IS_PAIRING_EXTRA, false);
        final String toastFmt = getString(isPairing ?
            R.string.pairing_successful_fmt :
            R.string.auth_successful_fmt);
        final String toastMessage = String.format(toastFmt, service.getName());
        Toast.makeText(AuthenticateActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void finish() {
        LOGGER.debug("finish");
        super.finish();
    }

    @Override
    public void onDestroy() {
        LOGGER.debug("onDestroy");
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(responseReceiver);
        broadcastManager.unregisterReceiver(progressReceiver);

        Intent i = new Intent(PicoBluetoothService.AUTHENTICATION_FINISHED);
        sendBroadcast(i);
        super.onDestroy();
    }

    public static class AuthFailedHere extends AuthFailedDialog {

        public static AuthFailedHere getInstance(final AuthFailedSource source, String userMsg) {
            final AuthFailedHere dialog = new AuthFailedHere();

            // Create args bundle containing the dialog source
            final Bundle args = new Bundle();
            args.putParcelable(SOURCE, source);
            args.putString(USER_MESSAGE, userMsg);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            Dialog dialog = super.onCreateDialog(savedInstanceState);
            dialog.setCanceledOnTouchOutside(true);
            return dialog;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            LOGGER.debug("auth failed dialog dismissed");

            // Call the activity's OnDismissListener
            final Activity activity = getActivity();
            if (activity != null && activity instanceof DialogInterface.OnDismissListener) {
                ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
            }
        }
    }

    public static class DelegationFailedHere extends DelegationFailedDialog {

        public static DelegationFailedHere getInstance(
            DelegationFailedSource source, ParcelableAuthToken token) {
            DelegationFailedHere dialog = new DelegationFailedHere();

            // Create args bundle containing the dialog source and the token
            final Bundle args = new Bundle();
            args.putParcelable(SOURCE, source);
            args.putParcelable(TOKEN, token);
            dialog.setArguments(args);

            return dialog;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            Dialog dialog = super.onCreateDialog(savedInstanceState);
            dialog.setCanceledOnTouchOutside(true);
            return dialog;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            LOGGER.debug("delegation failed dialog dismissed");

            // Call the activity's OnDismissListener
            final Activity activity = getActivity();
            if (activity != null && activity instanceof DialogInterface.OnDismissListener) {
                ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
            }
        }
    }

    @Override
    public void onPairingClicked(final SafePairing pairing) {
        LOGGER.info("{} selected", pairing);

        // Remove the LensPairingListFragment
        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.remove(fragmentManager.findFragmentByTag(PAIRING_LIST_FRAGMENT));
        fragmentTransaction.commit();

        // Authenticate using this pairing
        authenticateToService(pairing);
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        LOGGER.debug("finishing activity");

        // when authentication fails, reopen the main scanner screen
        startActivity(new Intent(this, AcquireCodeActivity.class));

        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
