package org.mypico.android.delegate;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.data.ParcelableCredentials;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.db.DbDataFactory;

/**
 * IntentService associated with the DelegatePairingActivity.
 * This service performs queries and writes data to Pico delegation database.
 * Results are returned to the calling activity through Intents broadcast
 * with the LocalBroadcastManager.
 *
 * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class DelegatePairingIntentService extends IntentService {
    /**
     * Use to log output messages to the LogCat console
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DelegatePairingIntentService.class.getSimpleName());

    static final String IS_PAIRING_PRESENT_ACTION = "IS_PAIRING_PRESENT";
    static final String PERSIST_PAIRING_ACTION = "PERSIST_PAIRING";
    static final String GET_ALL_LENS_PAIRINGS_ACTION = "GET_ALL_LENS_PAIRINGS";
    static final String GET_LENS_PAIRINGS_ACTION = "GET_LENS_PAIRINGS";
    static final String PAIRING = "PAIRING";
    static final String PAIRINGS = "PAIRINGS";
    static final String SERVICE = "SERVICE";
    static final String CREDENTIALS = "CREDENTIALS";
    static final String EXCEPTION = "EXCEPTION";

    // Used to access the database
    private DbDataFactory dbDataFactory;
    private DbDataAccessor dbDataAccessor;

    public DelegatePairingIntentService() {
        this(DelegatePairingIntentService.class.getCanonicalName());
    }

    /**
     * Constructor for creating a new <code>DelegatePairingIntentService</code>.
     *
     * @param name The name of the {@link IntentService}.
     */
    public DelegatePairingIntentService(final String name) {
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
        if (intent.getAction().equals(IS_PAIRING_PRESENT_ACTION)) {
            // Fnd out whether the pairing exists in the database
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
            // Store the pairing in the database
            if (intent.hasExtra(PAIRING) && intent.hasExtra(CREDENTIALS)) {
                final SafeLensPairing pairing =
                    (SafeLensPairing) intent.getParcelableExtra(PAIRING);
                final ParcelableCredentials credentials =
                    (ParcelableCredentials) intent.getParcelableExtra(CREDENTIALS);

                // Return the result as a broadcast
                final Intent localIntent = new Intent(PERSIST_PAIRING_ACTION);
                try {
                    final LensPairing newPairing = pairing.createLensPairing(
                        dbDataFactory, dbDataAccessor, credentials.getCredentials(), new ArrayList<String>());
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
            // Output a list of all pairings in the database
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
            // Get a specific lens pairing from the database selected
            // using the pairing commitment
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
