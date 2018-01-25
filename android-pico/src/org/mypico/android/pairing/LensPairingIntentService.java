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

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.security.KeyPair;

import org.mypico.android.data.ParcelableCredentials;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.db.DbDataFactory;
import org.mypico.jpico.data.terminal.Terminal;
import org.mypico.jpico.comms.RendezvousSigmaProxy;
import org.mypico.jpico.crypto.NewSigmaProver;
import org.mypico.jpico.crypto.NewSigmaProver.ProverAuthRejectedException;
import org.mypico.jpico.crypto.NewSigmaProver.VerifierAuthFailedException;
import org.mypico.jpico.crypto.ProtocolViolationException;
import org.mypico.rendezvous.RendezvousChannel;
import org.mypico.jpico.comms.JsonMessageSerializer;

/**
 * IntentService associated with the NewLensPairingActivity.
 * This service performs queries and writes data to Pico pairings database.
 * Results are returned to the calling activity through Intents broadcast
 * with the LocalBroadcastManager.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class LensPairingIntentService extends IntentService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(LensPairingIntentService.class.getSimpleName());

    static final String GET_CREDENTIALS_FROM_TERMINAL = "GET_CREDENTIALS_FROM_TERMINAL";
    static final String IS_PAIRING_PRESENT_ACTION = "IS_PAIRING_PRESENT";
    static final String PERSIST_PAIRING_ACTION = "PERSIST_PAIRING";
    static final String GET_ALL_LENS_PAIRINGS_ACTION = "GET_ALL_LENS_PAIRINGS";
    static final String GET_LENS_PAIRINGS_ACTION = "GET_LENS_PAIRINGS";
    static final String AUTHENTICATE_TERMINAL_UNTRUSTED = "AUTHENTICATE_TERMINAL_UNTRUSTED";
    static final String PAIRING = "PAIRING";
    static final String PAIRINGS = "PAIRINGS";
    static final String SERVICE = "SERVICE";
    static final String SERVICE_NAME = "SERVICE_NAME";
    static final String SERVICE_ADDRESS = "SERVICE_ADDRESS";
    static final String CREDENTIALS = "CREDENTIALS";
    static final String PRIVATE_FIELDS = "PRIVATE_FIELDS";
    static final String EXCEPTION = "EXCEPTION";
    static final String TERMINAL_COMMITMENT = "TERMINAL_COMMITMENT";
    static final String TERMINAL_ADDRESS = "TERMINAL_ADDRESS";
    static final String TERMINAL_SHARED_KEY = "SECRET_KEY";

    private DbDataFactory dbDataFactory;
    private DbDataAccessor dbDataAccessor;

    public LensPairingIntentService() {
        this(LensPairingIntentService.class.getCanonicalName());
    }

    public LensPairingIntentService(final String name) {
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
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (intent.getAction().equals(GET_CREDENTIALS_FROM_TERMINAL)) {
            if (intent.hasExtra(TERMINAL_ADDRESS) && intent.hasExtra(TERMINAL_COMMITMENT)) {
                final Uri terminalAddress = intent.getParcelableExtra(TERMINAL_ADDRESS);
                final byte[] terminalCommitment = intent.getByteArrayExtra(TERMINAL_COMMITMENT);
                // Return the result as a broadcast intent
                final Intent localIntent = new Intent();
                try {
                    // Lookup the terminal based on it's commitment
                    final Terminal terminal = dbDataAccessor.getTerminalByCommitment(terminalCommitment);
                    if (terminal != null) {
                        LOGGER.debug("Terminal {} is trusted", terminal);
                        localIntent.setAction(GET_CREDENTIALS_FROM_TERMINAL);

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
                            null);

                        // Authenticate to the Terminal
                        LOGGER.debug("Authenticating to {} over RendezvousChannel {}",
                            terminal, terminalAddress);
                        prover.prove();
                        String extraData = new String(prover.getReceivedExtraData());
                        LOGGER.debug("Received extraData = {}", extraData);

                        // Return the credentials and the terminal shared key
                        localIntent.putExtra(TERMINAL_SHARED_KEY, prover.getSharedKey().getEncoded());
                        final JSONObject obj = new JSONObject(new String(extraData));
                        final String serviceName = obj.getString("sn");
                        LOGGER.debug("sn = {}", serviceName);
                        final String serviceAddress = obj.getString("sa");
                        LOGGER.debug("sa = {}", serviceAddress);
                        final JSONObject credentialsJson = obj.getJSONObject("c");
                        final JSONArray privateFieldsJson = obj.getJSONArray("p");

                        Iterator<String> iter = credentialsJson.keys();
                        Map<String, String> credentialsMap = new HashMap();
                        while (iter.hasNext()) {
                            String key = iter.next();
                            String value = credentialsJson.getString(key);
                            LOGGER.debug("Found credential {}={}", key, value);
                            credentialsMap.put(key, value);
                        }

                        ArrayList<String> privateFields = new ArrayList<String>();
                        for (int i = 0; i < privateFieldsJson.length(); i++) {
                            LOGGER.debug("Adding private field: {}", privateFieldsJson.get(i).toString());
                            privateFields.add(privateFieldsJson.get(i).toString());
                        }

                        final ParcelableCredentials credentials = new ParcelableCredentials(credentialsMap);
                        localIntent.putExtra(CREDENTIALS, credentials);
                        localIntent.putExtra(SERVICE_NAME, serviceName);
                        localIntent.putExtra(SERVICE_ADDRESS, serviceAddress);
                        localIntent.putExtra(PRIVATE_FIELDS, privateFields);
                    } else {
                        LOGGER.warn("Terminal with commitment {} is not trusted", terminalCommitment);
                        localIntent.setAction(AUTHENTICATE_TERMINAL_UNTRUSTED);
                    }
                } catch (IOException e) {
                    LOGGER.warn("unable to authenticate to terminal", e);
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } catch (ProverAuthRejectedException e) {
                    LOGGER.warn("terminal rejected authentication", e);
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } catch (ProtocolViolationException e) {
                    LOGGER.warn("terminal violated the authentication protocol", e);
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } catch (VerifierAuthFailedException e) {
                    LOGGER.warn("terminal violated the authentication protocol", e);
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } catch (JSONException e) {
                    LOGGER.warn("could not parse json", e);
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } finally {
                    LOGGER.debug("Authenticated, sending broadcast");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
                }
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else if (intent.getAction().equals(IS_PAIRING_PRESENT_ACTION)) {
            if (intent.hasExtra(SERVICE) && intent.hasExtra(CREDENTIALS)) {
                final SafeService service =
                    (SafeService) intent.getParcelableExtra(SERVICE);
                final ParcelableCredentials credentials =
                    (ParcelableCredentials) intent.getParcelableExtra(CREDENTIALS);

                // Return the result as a broadcast
                final Intent localIntent = new Intent(IS_PAIRING_PRESENT_ACTION);
                try {
                    final List<LensPairing> pairings = dbDataAccessor
                        .getLensPairingsByServiceCommitmentAndCredentials(
                            service.getCommitment(), credentials.getCredentials());
                    if (pairings.size() == 0) {
                        localIntent.putExtra(IS_PAIRING_PRESENT_ACTION, false);
                    } else {
                        localIntent.putExtra(IS_PAIRING_PRESENT_ACTION, true);
                    }
                } catch (IOException e) {
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } finally {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
                }
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else if (intent.getAction().equals(PERSIST_PAIRING_ACTION)) {
            if (intent.hasExtra(PAIRING) && intent.hasExtra(CREDENTIALS) && intent.hasExtra(PRIVATE_FIELDS)) {
                final SafeLensPairing pairing =
                    (SafeLensPairing) intent.getParcelableExtra(PAIRING);
                final ParcelableCredentials credentials =
                    (ParcelableCredentials) intent.getParcelableExtra(CREDENTIALS);
                final ArrayList<String> privateFields =
                    (ArrayList<String>) intent.getSerializableExtra(LensPairingIntentService.PRIVATE_FIELDS);
                LOGGER.debug("PRIVATE: {}", privateFields.get(0));

                // Return the result as a broadcast
                final Intent localIntent = new Intent(PERSIST_PAIRING_ACTION);
                try {
                    if (dbDataAccessor == null) LOGGER.debug("{} is null", dbDataAccessor);
                    if (dbDataFactory == null) LOGGER.debug("{} is null", dbDataFactory);
                    final LensPairing newPairing = pairing.createLensPairing(
                        dbDataFactory, dbDataAccessor, credentials.getCredentials(), privateFields);
                    newPairing.save();

                    localIntent.putExtra(PERSIST_PAIRING_ACTION, true);
                    localIntent.putExtra(PAIRING, new SafeLensPairing(newPairing));

                } catch (IOException e) {
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtra(PAIRING, pairing);
                    localIntent.putExtras(extras);
                } finally {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
                }
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else if (intent.getAction().equals(GET_ALL_LENS_PAIRINGS_ACTION)) {
            // Return the result as a broadcast
            final Intent localIntent = new Intent(GET_ALL_LENS_PAIRINGS_ACTION);

            try {
                // Get all pairings
                final List<LensPairing> lps = dbDataAccessor.getAllLensPairings();

                // Compose a list of safe pairings
                final ArrayList<SafePairing> result = new ArrayList<SafePairing>(lps.size());
                for (LensPairing lp : lps) {
                    result.add(new SafeLensPairing(lp));
                }

                localIntent.putParcelableArrayListExtra(PAIRINGS, result);
            } catch (IOException e) {
                final Bundle extras = new Bundle();
                extras.putSerializable(EXCEPTION, (Serializable) e);
                localIntent.putExtras(extras);
            } finally {
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        } else if (intent.getAction().equals(GET_LENS_PAIRINGS_ACTION)) {
            if (intent.hasExtra(SERVICE)) {
                final SafeService service =
                    (SafeService) intent.getParcelableExtra(SERVICE);

                // Return the result as a broadcast
                final Intent localIntent = new Intent(GET_LENS_PAIRINGS_ACTION);
                try {
                    // Get all pairings with the service
                    final List<LensPairing> lps =
                        dbDataAccessor.getLensPairingsByServiceCommitment(service.getCommitment());

                    // Compose a list of safe pairings
                    final ArrayList<SafePairing> result = new ArrayList<SafePairing>(lps.size());
                    for (LensPairing lp : lps) {
                        result.add(new SafeLensPairing(lp));
                    }
                    LOGGER.trace("Lens pairings found = {}", result);
                    localIntent.putExtra(SERVICE, service);
                    localIntent.putParcelableArrayListExtra(PAIRINGS, result);
                } catch (IOException e) {
                    final Bundle extras = new Bundle();
                    extras.putSerializable(EXCEPTION, (Serializable) e);
                    localIntent.putExtras(extras);
                } finally {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
                }
            } else {
                LOGGER.error("Intent {} doesn't contain required extras", intent);
            }
        } else {
            LOGGER.warn("Unrecongised action {} ignored", intent.getAction());
        }
    }
}
