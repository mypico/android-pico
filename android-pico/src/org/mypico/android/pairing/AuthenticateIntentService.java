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

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.sql.SQLException;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.json.JSONException;
import org.mypico.android.R;
import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.db.DbHelper;
import org.mypico.jpico.ProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.mypico.android.bluetooth.BluetoothInfo;
import org.mypico.android.comms.SigmaProxy;
import org.mypico.android.core.AcquireCodeActivity;
import org.mypico.android.core.PicoApplication;
import org.mypico.android.core.PicoServiceImpl;
import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.data.SafeSession;
import org.mypico.jpico.comms.JsonMessageSerializer;
import org.mypico.jpico.comms.RendezvousSigmaProxy;
import org.mypico.jpico.comms.SocketCombinedProxy;
import org.mypico.jpico.crypto.CryptoFactory;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.crypto.LensProver;
import org.mypico.jpico.crypto.NewSigmaProver;
import org.mypico.jpico.crypto.NewSigmaProver.ProverAuthRejectedException;
import org.mypico.jpico.crypto.NewSigmaProver.VerifierAuthFailedException;
import org.mypico.jpico.crypto.ProtocolViolationException;
import org.mypico.jpico.crypto.Prover;
import org.mypico.jpico.crypto.messages.PicoReauthMessage;
import org.mypico.jpico.crypto.messages.ReauthState;
import org.mypico.jpico.crypto.messages.SequenceNumber;
import org.mypico.jpico.data.pairing.KeyPairing;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.PairingNotFoundException;
import org.mypico.jpico.data.session.Session;
import org.mypico.jpico.data.terminal.Terminal;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.db.DbDataFactory;
import org.mypico.rendezvous.RendezvousChannel;

/**
 * IntentService associated with the AuthenticateActivity.
 * Results are returned to the calling activity through Intents broadcast
 * with the LocalBroadcastManager.
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class AuthenticateIntentService extends IntentService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(AuthenticateIntentService.class.getSimpleName());

    static final String AUTHENTICATE_PAIRING_ACTION = "AUTHENTICATE_PAIRING";
    static final String AUTHENTICATE_KEY_PAIRING_ACTION = "AUTHENTICATE_KEY_PAIRING";
    static final String AUTHENTICATE_PAIRING_DELEGATION_FAILED = "AUTHENTICATE_PAIRING_DELEGATION_FAILED";
    static final String AUTHENTICATE_TERMINAL_ACTION = "AUTHENTICATE_TERMINAL";
    static final String AUTHENTICATE_TERMINAL_UNTRUSTED = "AUTHENTICATE_TERMINAL_UNTRUSTED";
    static final String AUTHENTICATE_DELEGATED = "AUTHENTICATE_DELEGATED";
    static final String TERMINAL_COMMITMENT = "TERMINAL_COMMITMENT";
    static final String TERMINAL_ADDRESS = "TERMINAL_ADDRESS";
    static final String TERMINAL_SHARED_KEY = "SECRET_KEY";
    static final String LOGIN_FORM = "LOGIN_FORM";
    static final String COOKIE_STRING = "COOKIE_STRING";
    static final String SERVICE = "SERVICE";
    static final String PAIRING = "PAIRING";
    static final String SESSION = "SESSION";
    static final String AUTHTOKEN = "AUTHTOKEN";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    static final String EXCEPTION = "EXCEPTION";
    static final String ACTIVITY_ID = "ACTIVITY_ID";
    static final UUID AUTHENTICATION_SERVICE_UUID = UUID.fromString("ed995e5a-c7e7-4442-a6ee-407400000000");

    public static final String AUTH_PROGRESS_ACTION = "AuthenticateIntentService.PROGRESS";
    public static final String PROGRESS_EXTRA = "PROGRESS";
    public static final String MAX_PROGRESS_EXTRA = "MAX_PROGRESS";
    public static final String DESCRIPTION_EXTRA = "DESCRIPTION";

    private DbDataFactory dbDataFactory;
    private DbDataAccessor dbDataAccessor;

    private Intent receivedIntent;

    private int receivedId;

    private String[] authProgressStageDescriptions;

    /**
     * Progress callback from the authentication process. The current stage is broadcast in a local
     * intent, to be picked up by the {@link AuthenticateActivity}.
     * <p>
     * The broadcast intent has action {@link #AUTH_PROGRESS_ACTION}, and contains the fields of a
     * {@link org.mypico.jpico.ProgressCallback.Stage} object in the following extras:
     * <ul>
     * <li>{@link #PROGRESS_EXTRA} &ndash; {@code int}, see {@link org.mypico.jpico.ProgressCallback.Stage#stage}</li>
     * <li>{@link #MAX_PROGRESS_EXTRA} &ndash; {@code int}, see {@link org.mypico.jpico.ProgressCallback.Stage#stages}</li>
     * <li>{@link #DESCRIPTION_EXTRA} &ndash; {@code String}, see {@link org.mypico.jpico.ProgressCallback.Stage#description}</li>
     * </ul>
     *
     * @see AuthenticateActivity#progressReceiver
     */
    private final ProgressCallback progressCallback = new ProgressCallback() {
        @Override
        public void onAuthProgress(Object caller, Stage currentStage) {
            final Intent intent = new Intent(AUTH_PROGRESS_ACTION);
            final int progress = currentStage.getProgress();
            final String description = authProgressStageDescriptions[progress];
            intent.putExtra(PROGRESS_EXTRA, progress);
            intent.putExtra(MAX_PROGRESS_EXTRA, currentStage.getMaxProgress());
            intent.putExtra(DESCRIPTION_EXTRA, description);
            LocalBroadcastManager.getInstance(AuthenticateIntentService.this).sendBroadcast(intent);
        }
    };

    public AuthenticateIntentService() {
        this(AuthenticateIntentService.class.getCanonicalName());
    }

    public AuthenticateIntentService(final String name) {
        super(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Ormlite helper
        final OrmLiteSqliteOpenHelper helper =
            OpenHelperManager.getHelper(this, DbHelper.class);
        try {
            dbDataFactory = new DbDataFactory(helper.getConnectionSource());
            dbDataAccessor = new DbDataAccessor(helper.getConnectionSource());
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to database");
            throw new RuntimeException(e);
        }

        final Resources res = getResources();
        authProgressStageDescriptions = res.getStringArray(R.array.auth_progress__stages);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        receivedIntent = intent;
        receivedId = intent.getIntExtra(ACTIVITY_ID, 0);
        LOGGER.info("Handling Intent for activity id: {}", receivedId);
        if (intent.getAction().equals(AUTHENTICATE_PAIRING_ACTION)) {
            if (intent.hasExtra(PAIRING) &&
                intent.hasExtra(SERVICE) &&
                intent.hasExtra(TERMINAL_ADDRESS) &&
                intent.hasExtra(TERMINAL_SHARED_KEY) &&
                intent.hasExtra(LOGIN_FORM) &&
                intent.hasExtra(COOKIE_STRING)) {
                // Extract the pairing, service, terminal address and shared key from the Intent
                final SafeLensPairing pairing = intent.getParcelableExtra(PAIRING);
                final SafeService service = intent.getParcelableExtra(SERVICE);
                final Uri terminalAddress = intent.getParcelableExtra(TERMINAL_ADDRESS);
                final byte[] terminalSharedKey = intent.getByteArrayExtra(TERMINAL_SHARED_KEY);
                final String loginForm = intent.getStringExtra(LOGIN_FORM);
                final String cookieString = intent.getStringExtra(COOKIE_STRING);

                // Authenticate to the service using the specified pairing
                authenticatePairing(pairing, service, loginForm, cookieString, terminalAddress, terminalSharedKey);
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else if (intent.getAction().equals(AUTHENTICATE_KEY_PAIRING_ACTION)) {
            if (intent.hasExtra(AcquireCodeActivity.SERVICE) &&
                intent.hasExtra(SafeKeyPairing.class.getCanonicalName())) {
                // Extract the pairing, service, terminal address and shared key from the Intent
                final SafeKeyPairing pairing = intent.getParcelableExtra(SafeKeyPairing.class.getCanonicalName());
                final SafeService service = intent.getParcelableExtra(VisualCodeIntentGenerator.SERVICE);
                final Uri terminalAddress = intent.getParcelableExtra(VisualCodeIntentGenerator.TERMINAL_ADDRESS);
                final String terminalCommitment = intent.getStringExtra(VisualCodeIntentGenerator.TERMINAL_COMMITMENT);

                // Authenticate to the service using the specified pairing
                authenticatePairing(pairing, service, terminalAddress, terminalCommitment);
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else if (intent.getAction().equals(AUTHENTICATE_TERMINAL_ACTION)) {
            if (intent.hasExtra(TERMINAL_ADDRESS) &&
                intent.hasExtra(TERMINAL_COMMITMENT)) {
                // Extract the terminal address and commitment from the Intent
                final Uri terminalAddress = intent.getParcelableExtra(TERMINAL_ADDRESS);
                final byte[] terminalCommitment = intent.getByteArrayExtra(TERMINAL_COMMITMENT);

                // Authenticate to the terminal specified by the terminalCommitment
                authenticateTerminal(terminalAddress, terminalCommitment);
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else if (intent.getAction().equals(AUTHENTICATE_DELEGATED)) {
            if (intent.hasExtra(AUTHTOKEN) && intent.hasExtra(TERMINAL_ADDRESS)
                && intent.hasExtra(TERMINAL_SHARED_KEY)) {
                // Extract the authtoken, terminal address and shared key from the Intent
                final AuthToken token = intent.getParcelableExtra(AUTHTOKEN);
                final Uri terminalAddress = intent.getParcelableExtra(TERMINAL_ADDRESS);
                final byte[] terminalSharedKey = intent.getByteArrayExtra(TERMINAL_SHARED_KEY);
                final SafeService service = intent.getParcelableExtra(SERVICE);

                authenticateDelegated(token, terminalAddress, terminalSharedKey, service);
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else {
            LOGGER.warn("Unrecongised action {} ignored", intent.getAction());
        }
    }

    /**
     * Perform an authentication to the service using the pairing provided.
     *
     * @param pairing            The pairing to authenticate using.
     * @param service            The service to authenticate to.
     * @param terminalAddress    The address of the terminal to pass the authentication cookie to.
     * @param terminalCommitment The terminal commitment.
     */
    private void authenticatePairing(SafeKeyPairing pairing, final SafeService service,
                                     final Uri terminalAddress, final String terminalCommitment) {
        // Return the result as a broadcast
        final Intent localIntent = new Intent(AUTHENTICATE_PAIRING_ACTION);
        // Get the device's Bluetooth address to put in the extra data field
        final String bluetoothAddress = BluetoothInfo.getLocalAddress(this);
        final byte[] bluetoothExtra = bluetoothAddress == null ? null : bluetoothAddress.getBytes();

        KeyPair keyPair = null;
        KeyPairing keyPairing;
        try {
            keyPairing = pairing.getKeyPairing(dbDataAccessor);
            if (keyPairing == null)
                keyPair = CryptoFactory.INSTANCE.ecKpg().generateKeyPair();
            else
                keyPair = new KeyPair(keyPairing.getPublicKey(), keyPairing.getPrivateKey());
            if (true) {
                // Instantiate and return a concrete proxy subclass based on the service address
                //final Uri address = pairing.getSafeService().getAddress();
                final Uri address = service.getAddress();
                LOGGER.info("pairing.getSafeService().getAddress(): " + address.toString());
                LOGGER.info("service.getAddress():" + service.getAddress());
                LOGGER.info("address.getHost():" + address.getHost());
                LOGGER.info("address.getScheme():" + address.getScheme());

                if (address.getScheme().equals("tcp")) {
                    final SocketCombinedProxy proxy = new SocketCombinedProxy(
                        address.getHost(),
                        address.getPort(),
                        new JsonMessageSerializer());

                    // Construct the prover:
                    byte[] extraData = bluetoothExtra;
                    if (keyPairing != null) {
                        LOGGER.info("Sending extra data: {}", keyPairing.getExtraData());
                        extraData = keyPairing.getExtraData().getBytes();
                    }
                    final NewSigmaProver prover = new NewSigmaProver(
                        NewSigmaProver.VERSION_1_1,
                        keyPair,
                        extraData, //extra data here
                        proxy,
                        service.getCommitment(),
                        progressCallback);

                    final Session session;
                    boolean proveResult = prover.prove();
                    extraData = prover.getReceivedExtraData();
                    String dataToSave = null;
                    String pairingName = null;
                    if (extraData != null && extraData.length > 0) {
                        String extraDataStr = new String(extraData);
                        LOGGER.debug("Received extraData = {}", extraDataStr);
                        try {
                            // New format, decode the JSON string
                            JSONObject obj = new JSONObject(new String(extraData));
                            dataToSave = obj.getString("data");
                            pairingName = obj.getString("name");
                        } catch (JSONException e) {
                            // Old format, the service is expecting us to just store the data
                            dataToSave = extraDataStr;
                        }
                    } else {
                        LOGGER.debug("No extraData received");
                    }

                    keyPairing = pairing.getOrCreateKeyPairing(dbDataFactory, dbDataAccessor, keyPair, dataToSave);
                    if (pairingName != null) {
                        keyPairing.setName(pairingName);
                    }
                    keyPairing.save();

                    if (proveResult) {
                        session = Session.newInstanceActive(
                            dbDataFactory,
                            Integer.toString(prover.getVerifierSessionId()),
                            prover.getSharedKey(),
                            keyPairing,
                            null);
                    } else {
                        session = Session.newInstanceClosed(
                            dbDataFactory,
                            Integer.toString(prover.getVerifierSessionId()),
                            keyPairing,
                            null);
                    }


                    // TO be removed - no need to persist sessions
                    if (session.getStatus() != Session.Status.ERROR) {
                        // If the session is ok, then save it.
                        LOGGER.debug("Persisting session");
                        session.save();
                    }

                    // Carry out the authentication and get the Session instance result:
                    final SafeSession safeSession = new SafeSession(session);
                    localIntent.putExtra(SESSION, safeSession);
                    localIntent.putExtra(PAIRING, new SafeKeyPairing(keyPairing));
                } else if (address.getScheme().equals("http")) {
                    // Create a rendezvous channel
                    final RendezvousChannel channel =
                        new RendezvousChannel(SafeService.UriToURI(service.getAddress()).toURL());

                    final RendezvousSigmaProxy proxy = new RendezvousSigmaProxy(
                        channel, new JsonMessageSerializer());


                    byte[] extraData = bluetoothExtra;
                    if (keyPairing != null) {
                        LOGGER.info("Sending extra data: {}", keyPairing.getExtraData());
                        extraData = keyPairing.getExtraData().getBytes();
                    }
                    final NewSigmaProver prover = new NewSigmaProver(
                        NewSigmaProver.VERSION_1_1,
                        keyPair,
                        extraData, //extra data here
                        proxy,
                        service.getCommitment(),
                        progressCallback);

                    // Authenticate to the Terminal
                    LOGGER.debug("Authenticating to {} over RendezvousChannel {}",
                        service, channel.getUrl());

                    final Session session;
                    boolean proveResult = prover.prove();
                    extraData = prover.getReceivedExtraData();
                    String dataToSave = null;
                    String pairingName = null;
                    if (extraData != null && extraData.length > 0) {
                        String extraDataStr = new String(extraData);
                        LOGGER.debug("Received extraData = {}", extraDataStr);
                        try {
                            // New format, decode the JSON string
                            JSONObject obj = new JSONObject(new String(extraData));
                            dataToSave = obj.getString("data");
                            pairingName = obj.getString("name");
                        } catch (JSONException e) {
                            // Old format, the service is expecting us to just store the data
                            dataToSave = extraDataStr;
                        }
                    } else {
                        LOGGER.debug("No extraData received");
                    }

                    keyPairing = pairing.getOrCreateKeyPairing(dbDataFactory, dbDataAccessor, keyPair, dataToSave);
                    if (pairingName != null) {
                        keyPairing.setName(pairingName);
                    }
                    keyPairing.save();

                    if (proveResult) {
                        session = Session.newInstanceActive(
                            dbDataFactory,
                            Integer.toString(prover.getVerifierSessionId()),
                            prover.getSharedKey(),
                            keyPairing,
                            null);

                    } else {
                        session = Session.newInstanceClosed(
                            dbDataFactory,
                            Integer.toString(prover.getVerifierSessionId()),
                            keyPairing,
                            null);
                    }

                    // TO be removed - no need to persist sessions
                    if (session.getStatus() != Session.Status.ERROR) {
                        // If the session is ok, then save it.
                        LOGGER.debug("Persisting session");
                        session.save();
                    }

                    // Carry out the authentication and get the Session instance result:
                    final SafeSession safeSession = new SafeSession(session);
                    LOGGER.debug("Current name {}", session.getPairing().getName());
                    if (session.getStatus() == Session.Status.ACTIVE) {
                        // Start continuous authentication
                        final Intent intent = new Intent(this, PicoServiceImpl.class);
                        final Uri url = SafeService.URIToUri(channel.getUrl().toURI());
                        intent.putExtra(PicoServiceImpl.PROXY_CHANNEL, url);

                        intent.putExtra(
                            PicoServiceImpl.StartCommandType.class.getCanonicalName(),
                            PicoServiceImpl.StartCommandType.START.ordinal());
                        intent.putExtra(SafeSession.class.getCanonicalName(), safeSession);
                        startService(intent);
                    }

                    localIntent.putExtra(SESSION, safeSession);
                    SafeKeyPairing newSafeKeyPairing = new SafeKeyPairing(keyPairing);
                    localIntent.putExtra(PAIRING, newSafeKeyPairing);


                } else if (address.getScheme().equals("btspp")) {
                    String serviceAddr = service.getAddress().toString();
                    // Format is btspp://DDDDDDDDDDDD:PP
                    // where DDDDDDDDDDDD is a 6-byte hex device id
                    // and PP is a 1-byte hex channel number
                    String hwAddressStr = serviceAddr.substring(8, 8 + 12).toUpperCase();
                    //insert colon between each pair of characters
                    String hwAddress = hwAddressStr.replaceAll("..(?!$)", "$0:");

                    //create bluetooth socket connecting to specific BT hardware address
                    BluetoothManager bMgr = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                    BluetoothAdapter bAdapter = bMgr.getAdapter();
                    BluetoothDevice bDevice = bAdapter.getRemoteDevice(hwAddress);
                    final int channel;
                    final BluetoothSocket bSocket;

                    if (serviceAddr.length() == 23) {
                        // Contains port
                        channel = Integer.parseInt(serviceAddr.substring(serviceAddr.length() - 2), 16);
                        Method m = bDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                        bSocket = (BluetoothSocket) m.invoke(bDevice, channel);
                    } else {
                        channel = 0;
                        bSocket = bDevice.createRfcommSocketToServiceRecord(AUTHENTICATION_SERVICE_UUID);
                    }

                    LOGGER.info("BT device address: " + hwAddress);
                    LOGGER.info("BT channel: " + channel);

                    bSocket.connect();
                    LOGGER.info("Bluetooth socket is connected");

                    final SigmaProxy proxy = new SigmaProxy(
                        bSocket, new JsonMessageSerializer());

                    //set the extra data to be null unless some was received
                    // Bluetooth address does not need to be sent here
                    byte[] extraData = null;
                    if (receivedIntent.hasExtra(AuthenticateIntentService.EXTRA_DATA)) {
                        extraData = receivedIntent.getByteArrayExtra(AuthenticateIntentService.EXTRA_DATA);
                    }
                    if (keyPairing != null) {
                        LOGGER.info("Sending extra data: {}", keyPairing.getExtraData());
                        extraData = keyPairing.getExtraData().getBytes();
                    }
                    final NewSigmaProver prover = new NewSigmaProver(
                        NewSigmaProver.VERSION_1_1,
                        keyPair,
                        extraData, //extra data here
                        proxy,
                        service.getCommitment(),
                        progressCallback);

                    // Authenticate to the Terminal
                    LOGGER.debug("Authenticating to {} over Bluetooth Channel",
                        service);

                    final Session session;
                    boolean proveResult = prover.prove();
                    extraData = prover.getReceivedExtraData();
                    String dataToSave = null;
                    String pairingName = null;
                    if (extraData != null && extraData.length > 0) {
                        String extraDataStr = new String(extraData);
                        LOGGER.debug("Received extraData = {}", extraDataStr);
                        try {
                            // New format, decode the JSON string
                            JSONObject obj = new JSONObject(new String(extraData));
                            dataToSave = obj.getString("data");
                            pairingName = obj.getString("name");
                        } catch (JSONException e) {
                            // Old format, the service is expecting us to just store the data
                            dataToSave = extraDataStr;
                        }
                    } else {
                        LOGGER.debug("No extraData received");
                    }

                    keyPairing = pairing.getOrCreateKeyPairing(dbDataFactory, dbDataAccessor, keyPair, dataToSave);
                    if (pairingName != null) {
                        keyPairing.setName(pairingName);
                    }
                    keyPairing.save();

                    if (proveResult) {
                        session = Session.newInstanceActive(
                            dbDataFactory,
                            Integer.toString(prover.getVerifierSessionId()),
                            prover.getSharedKey(),
                            keyPairing,
                            null);

                    } else {
                        session = Session.newInstanceClosed(
                            dbDataFactory,
                            Integer.toString(prover.getVerifierSessionId()),
                            keyPairing,
                            null);
                    }

                    if (session.getStatus() != Session.Status.ERROR) {
                        // If the session is ok, then save it.
                        LOGGER.debug("Persisting session");
                        session.save();
                    }

                    LOGGER.info("Closing bluetooth socket");
                    bSocket.close();

                    // Carry out the authentication and get the Session instance result:
                    final SafeSession safeSession = new SafeSession(session);
                    if (session.getStatus() == Session.Status.ACTIVE) {
                        // Start continuous authentication
                        final Intent intent = new Intent(this, PicoServiceImpl.class);
                        intent.putExtra(PicoServiceImpl.PROXY_BT_ADDRESS, hwAddress);
                        intent.putExtra(PicoServiceImpl.PROXY_BT_CHANNEL, channel);

                        intent.putExtra(
                            PicoServiceImpl.StartCommandType.class.getCanonicalName(),
                            PicoServiceImpl.StartCommandType.START.ordinal());
                        intent.putExtra(SafeSession.class.getCanonicalName(), safeSession);
                        startService(intent);
                    }

                    localIntent.putExtra(SESSION, safeSession);
                    localIntent.putExtra(PAIRING, new SafeKeyPairing(keyPairing));
                } else {
                    throw new IllegalArgumentException(
                        "unsupported service protocol: " + address.getScheme());
                }
            } else {
                LOGGER.error("KeyPairing is invalid");
                throw new PairingNotFoundException("KeyPairing is invalid");
            }
        } catch (Exception e) {
            e.printStackTrace();
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } finally {
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
    }

    private void authenticatePairing(final SafeLensPairing pairing, final SafeService service,
                                     final String loginForm, final String cookieString, final Uri terminalAddress, final byte[] terminalSharedKey) {
        // Return the result as a broadcast
        final Intent localIntent = new Intent(AUTHENTICATE_PAIRING_ACTION);
        try {
            try {
                // Promote to a full LensPairing
                final LensPairing credentialPairing = pairing.getLensPairing(dbDataAccessor);
                if (credentialPairing != null) {
                    // Get the address to authenticate to
                    final URI serviceAddress = new URI(service.getAddress().toString());

                    LOGGER.debug("Authenticating credential pairing: {}, {}", pairing, serviceAddress);

                    // Construct the prover
                    final Prover prover = new LensProver(credentialPairing, serviceAddress, loginForm, cookieString, dbDataFactory);

                    // Carry out the authentication and get the Session instance result:
                    final Session session = prover.startSession();
                    localIntent.putExtra(SESSION, new SafeSession(session));
                    if (session.getStatus() != Session.Status.ERROR) {
                        // If the session is ok, then save it...
                        session.save();

                        // and save the new service address
                        credentialPairing.getService().setAddress(serviceAddress);
                        credentialPairing.save();
                    }

                    // Delegate the AuthToken to the Terminal
                    final PicoReauthMessage message = new PicoReauthMessage(
                        session.getId(), ReauthState.CONTINUE, SequenceNumber.getRandomInstance(),
                        session.getAuthToken().toByteArray());

                    // Create a rendezvous channel
                    LOGGER.debug("Creating RendezvousChannel at {}", terminalAddress);
                    final URL url = SafeService.UriToURI(terminalAddress).toURL();
                    final RendezvousChannel channel = new RendezvousChannel(url);

                    // Make a proxy for the terminal verifier
                    final RendezvousSigmaProxy proxy = new RendezvousSigmaProxy(
                        channel, new JsonMessageSerializer());

                    proxy.reauth(message.encrypt(new SecretKeySpec(terminalSharedKey, "AES/GCM/NoPadding")));
                } else {
                    LOGGER.error("Pairing not found");
                }
            } catch (IOException e) {
                LOGGER.error("IOException...");
                throw e;
            } catch (URISyntaxException e) {
                LOGGER.error("URISyntax...");
                throw e;
            } catch (InvalidKeyException e) {
                LOGGER.error("InvalidKey...");
                throw e;
            }
        } catch (Exception e) {
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } finally {
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
    }

    /**
     * Perform an authentication to the service using the delegated token provided.
     *
     * @param token             The token to authenticate using.
     * @param terminalAddress   The address of the terminal to pass the authentication cookie to.
     * @param terminalSharedKey The shared key to use.
     * @param service           The service to authenticate to.
     */
    private void authenticateDelegated(final AuthToken token, final Uri terminalAddress, final byte[] terminalSharedKey, SafeService service) {
        // Return the result as a broadcast
        final Intent localIntent = new Intent(AUTHENTICATE_DELEGATED);

        try {
            try {
                // Delegate the AuthToken to the Terminal
                // TODO: Figure out what the session.getId() value should be
                PicoReauthMessage message = new PicoReauthMessage(
                        /*session.getId()*/ 0, ReauthState.CONTINUE, SequenceNumber.getRandomInstance(),
                    token.toByteArray());

                // Create a rendezvous channel
                LOGGER.debug("Creating RendezvousChannel at {}", terminalAddress);
                final URL url = SafeService.UriToURI(terminalAddress).toURL();
                final RendezvousChannel channel = new RendezvousChannel(url);

                // Make a proxy for the terminal verifier
                final RendezvousSigmaProxy proxy = new RendezvousSigmaProxy(
                    channel, new JsonMessageSerializer());

                proxy.reauth(message.encrypt(new SecretKeySpec(terminalSharedKey, "AES/GCM/NoPadding")));

                localIntent.putExtra(SERVICE, service);

            } catch (IOException e) {
                LOGGER.error("IOException...");
                throw e;
            } catch (InvalidKeyException e) {
                LOGGER.error("InvalidKey...");
                throw e;
            }
        } catch (Exception e) {
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } finally {
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
    }

    /**
     * Perform an authentication to a terminal.
     *
     * @param terminalAddress    The address of the terminal to pass the authentication cookie to.
     * @param terminalCommitment The terminal commitment.
     */
    private void authenticateTerminal(final Uri terminalAddress, final byte[] terminalCommitment) {
        // Return the result as a broadcast intent
        final Intent localIntent = new Intent();
        try {
            // Lookup the terminal based on it's commitment
            final Terminal terminal = dbDataAccessor.getTerminalByCommitment(terminalCommitment);
            if (terminal != null) {
                LOGGER.debug("Terminal {} is trusted", terminal);
                localIntent.setAction(AUTHENTICATE_TERMINAL_ACTION);

                // Create a rendezvous channel
                final URL url = SafeService.UriToURI(terminalAddress).toURL();
                final RendezvousChannel channel = new RendezvousChannel(url);

                // Make a proxy for the terminal verifier
                final RendezvousSigmaProxy proxy = new RendezvousSigmaProxy(
                    channel, new JsonMessageSerializer());

                final NewSigmaProver prover = new NewSigmaProver(
                    NewSigmaProver.VERSION_1_1,
                    new KeyPair(terminal.getPicoPublicKey(), terminal.getPicoPrivateKey()),
                    null,
                    proxy,
                    terminal.getCommitment(),
                    progressCallback);

                // Authenticate to the Terminal
                LOGGER.debug("Authenticating to {} over RendezvousChannel {}",
                    terminal, terminalAddress);
                prover.prove();

                // Return the extra data and the terminal shared key
                localIntent.putExtra(EXTRA_DATA, prover.getReceivedExtraData());
                localIntent.putExtra(TERMINAL_SHARED_KEY, prover.getSharedKey().getEncoded());
            } else {
                LOGGER.warn("Terminal with commitment {} is not trusted", terminalCommitment);
                localIntent.setAction(AUTHENTICATE_TERMINAL_UNTRUSTED);
            }
        } catch (IOException e) {
            LOGGER.warn("unable to authenticate to terminal", e);
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } catch (ProverAuthRejectedException e) {
            LOGGER.warn("terminal rejected authentication", e);
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } catch (ProtocolViolationException e) {
            LOGGER.warn("terminal violated the authentication protocol", e);
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } catch (VerifierAuthFailedException e) {
            LOGGER.warn("terminal violated the authentication protocol", e);
            final Bundle extras = new Bundle();
            extras.putSerializable(EXCEPTION, (Serializable) e);
            extras.putInt(ACTIVITY_ID, receivedId);
            localIntent.putExtras(extras);
        } finally {
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
    }
}
