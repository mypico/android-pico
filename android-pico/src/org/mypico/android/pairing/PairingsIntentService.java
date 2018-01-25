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

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.mypico.android.data.ParcelableCredentials;
import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.data.pairing.KeyPairing;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.Pairing;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.db.DbDataFactory;

/**
 * IntentService associated with the NewLensPairingActivity.
 * This service performs queries and writes data to Pico pairings database.
 * Results are returned to the calling activity through Intents broadcast
 * with the LocalBroadcastManager.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public class PairingsIntentService extends IntentService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(PairingsIntentService.class.getSimpleName());

    static final String IS_PAIRING_PRESENT_ACTION = "IS_PAIRING_PRESENT";
    static final String PERSIST_PAIRING_ACTION = "PERSIST_PAIRING";
    static final String GET_ALL_PAIRINGS_ACTION = "GET_ALL_PAIRINGS";
    static final String DELETE_PAIRINGS_ACTION = "DELETE_PAIRINGS";
    static final String CHANGE_PAIRING_NAME_ACTION = "CHANGE_PAIRING_NAME_ACTION";
    static final String PAIRING = "PAIRING";
    static final String PAIRINGS = "PAIRINGS";
    static final String PAIRINGS_DELETED = "PAIRINGS_DELETED";
    static final String SERVICE = "SERVICE";
    static final String CREDENTIALS = "CREDENTIALS";
    static final String EXCEPTION = "EXCEPTION";

    private DbDataFactory dbDataFactory;
    private DbDataAccessor dbDataAccessor;

    public PairingsIntentService() {
        this(PairingsIntentService.class.getCanonicalName());
    }

    public PairingsIntentService(final String name) {
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
        } else if (intent.getAction().equals(GET_ALL_PAIRINGS_ACTION)) {
            // Return the result as a broadcast
            final Intent localIntent = new Intent(GET_ALL_PAIRINGS_ACTION);

            try {
                // Get all pairings
                final List<LensPairing> lps = dbDataAccessor.getAllLensPairings();
                final List<KeyPairing> kps = dbDataAccessor.getAllKeyPairings();

                // Compose a list of safe pairings
                final ArrayList<SafePairing> result = new ArrayList<SafePairing>();
                for (LensPairing lp : lps) {
                    result.add(new SafeLensPairing(lp));
                }

                for (KeyPairing kp : kps) {
                    result.add(new SafeKeyPairing(kp));
                }

                LOGGER.debug("Sending pairings {}", result);
                localIntent.putParcelableArrayListExtra(PAIRINGS, result);
            } catch (IOException e) {
                final Bundle extras = new Bundle();
                extras.putSerializable(EXCEPTION, (Serializable) e);
                localIntent.putExtras(extras);
            } finally {
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        } else if (intent.getAction().equals(DELETE_PAIRINGS_ACTION)) {
            final ArrayList<SafePairing> pairings =
                intent.getParcelableArrayListExtra(PairingsIntentService.PAIRINGS);
            final int total = pairings.size();
            int deleted = 0;
            LOGGER.debug("deleting {} pairings(s)...", total);

            // Return the result as a broadcast
            final Intent localIntent = new Intent(DELETE_PAIRINGS_ACTION);
            try {
                for (SafePairing sp : pairings) {
                    try {
                        sp.getPairing(dbDataAccessor).delete();
                        ++deleted;
                        LOGGER.info("{} deleted (note: all deleted pairings have an id of 0)", sp);
                    } catch (IOException e) {
                        LOGGER.warn(sp.toString() + " not deleted (IOException)", e);
                    }
                }
            } finally {
                localIntent.putExtra(PAIRINGS, total);
                localIntent.putExtra(PAIRINGS_DELETED, deleted);
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
            }
        } else if (intent.getAction().equals(CHANGE_PAIRING_NAME_ACTION)) {
            if (intent.hasExtra(PAIRING)) {
                final SafePairing pairing =
                    (SafePairing) intent.getParcelableExtra(PAIRING);
                final String newName =
                    (String) intent.getStringExtra(String.class.getCanonicalName());

                // Return the result as a broadcast
                final Intent localIntent = new Intent(CHANGE_PAIRING_NAME_ACTION);
                try {
                    final Pairing newPairing = pairing.getPairing(
                        dbDataAccessor);
                    newPairing.setName(newName);
                    newPairing.save();

                    localIntent.putExtra(CHANGE_PAIRING_NAME_ACTION, true);
                    localIntent.putExtra(PAIRING, new SafePairing(newPairing));
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
