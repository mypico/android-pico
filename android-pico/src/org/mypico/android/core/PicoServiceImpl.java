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


package org.mypico.android.core;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.UUID;

import org.mypico.android.comms.SigmaProxy;
import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.data.SafeSession;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.mypico.android.R;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.jpico.comms.CombinedVerifierProxy;
import org.mypico.jpico.comms.JsonMessageSerializer;
import org.mypico.jpico.comms.RendezvousSigmaProxy;
import org.mypico.jpico.comms.SocketCombinedProxy;
import org.mypico.jpico.crypto.ContinuousProver;
import org.mypico.jpico.crypto.LensProver;
import org.mypico.jpico.crypto.Prover;
import org.mypico.jpico.crypto.ServiceSigmaProver;
import org.mypico.jpico.crypto.messages.SequenceNumber;
import org.mypico.jpico.data.pairing.KeyPairing;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.Pairing;
import org.mypico.jpico.data.pairing.PairingNotFoundException;
import org.mypico.jpico.data.session.Session;
import org.mypico.jpico.data.terminal.Terminal;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.db.DbDataFactory;
import org.mypico.rendezvous.RendezvousChannel;

/**
 * An implementation of the {@link PicoService} interface. The service runs in the background and
 * manaages running sessions that the Pico is maintaining.
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class PicoServiceImpl extends Service implements PicoService, ContinuousProver.ProverStateChangeNotificationInterface {

    public static final String SESSION_INFO_UPDATE = "org.mypico.android.service.SESSION_INFO_UPDATE";
    public static final int NOTIFICATION_ID = 5001;
    public static final String PROXY_CHANNEL = "PROXY_CHANNEL";
    public static final String GET_SINGLE_SESSION = "GET_SINGLE_SESSION";
    public static final String ACTION_BROADCAST_ALL_SESSIONS = "ACTION_BROADCAST_ALL_SESSIONS";
    public static final String PROXY_BT_ADDRESS = "PROXY_BT_ADDRESS";
    public static final String PROXY_BT_CHANNEL = "PROXY_BT_CHANNEL";
    static final UUID CONTINUOUS_SERVICE_UUID = UUID.fromString("ed995e5a-c7e7-4442-a6ee-C02712005000");

    public static enum StartCommandType {
        START, PAUSE, RESUME, STOP
    }

    static final Logger LOGGER = LoggerFactory.getLogger(PicoServiceImpl.class.getSimpleName());

    private final IBinder binder = new PicoServiceBinder();
    private LocalBroadcastManager broadcastManager;
    private BroadcastReceiver broadcastReceiver;
    private Map<SafeSession, ContinuousProver> provers;

    // TODO make these into one thing
    private DbDataFactory dbDataFactory;
    private DbDataAccessor dbDataAccessor;

    final Executor pollServiceExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            new Thread(command).start();
        }
    };


    public class PicoServiceBinder extends Binder {
        public PicoService getService() {
            return PicoServiceImpl.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        LOGGER.debug("Starting PicoServiceImpl");

        // Ormlite helper thin
        OrmLiteSqliteOpenHelper helper = OpenHelperManager.getHelper(this, DbHelper.class);

        try {
            dbDataFactory = new DbDataFactory(helper.getConnectionSource());
            dbDataAccessor = new DbDataAccessor(helper.getConnectionSource());
        } catch (SQLException e) {
            LOGGER.warn("Failed to connect to database");
        }

        broadcastManager = LocalBroadcastManager.getInstance(this);

        provers = new HashMap<SafeSession, ContinuousProver>();
    }

    /**
     * Broadcast details of a session to other activities.
     *
     * @param session The session to broadcast.
     */
    private void broadcastInfo(final SafeSession session) {
        PicoServiceImpl.LOGGER.debug("Broadcasting session info");
        final Intent intent = new Intent(PicoServiceImpl.SESSION_INFO_UPDATE);
        intent.putExtra(SafeSession.class.getCanonicalName(), session);
        broadcastManager.sendBroadcast(intent);
    }

    /**
     * Broadcast details of all sessions to other activities.
     */
    public void broadcastAllSessions() {
        LOGGER.debug("Broadcasting all sessions. provers.keySet().size() = ", provers.keySet().size());
        ArrayList<SafeSession> list = new ArrayList<SafeSession>(provers.keySet());
        final Intent intent = new Intent(PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS);
        intent.putParcelableArrayListExtra(ArrayList.class.getCanonicalName(), list);
        broadcastManager.sendBroadcast(intent);
    }

    /**
     * Update the status of the sessions shown in the user interface.
     */
    public void updateForegroundStatus() {
        int numberOfActiveSessions = 0;
        for (SafeSession session : provers.keySet()) {
            if (session.getStatus() == Session.Status.ACTIVE) {
                numberOfActiveSessions++;
            }
        }

        if (numberOfActiveSessions > 0) {
            Intent intent = new Intent(this, PicoStatusActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(TabsActivity.ACTIVE_TAB, PicoStatusActivity.INDEX_SESSIONS_TAB);

            Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getResources().getQuantityString(R.plurals.bluetooth_notification__current_sessions, numberOfActiveSessions, numberOfActiveSessions))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.pico_orange))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
            startForeground(NOTIFICATION_ID, notification);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start in the foreground
        LOGGER.debug(StartCommandType.class.getCanonicalName());

        if (intent != null) {
            if (ACTION_BROADCAST_ALL_SESSIONS.equals(intent.getAction())) {
                broadcastAllSessions();
            } else {
                final SafeSession sessionInfo;
                if (intent.hasExtra(GET_SINGLE_SESSION)) {
                    LOGGER.debug("Need to get single safe session instance from list");
                    // get single safesession instance in provers map
                    Iterator<SafeSession> it = provers.keySet().iterator();
                    if (provers.size() == 1) { //expecting only one continuous auth session for the saw demo
                        if (it.hasNext()) {
                            sessionInfo = it.next();
                            LOGGER.debug("Got single safe session instance");
                        } else { // no active provers, uh oh
                            sessionInfo = null;
                            LOGGER.error("No provers found");
                        }
                    } else {
                        sessionInfo = null;
                        LOGGER.error("provers map was the wrong size. Expected 1 got {}", provers.size());
                    }
                } else {
                    // Unpack SafeSession instance from intent
                    LOGGER.debug("Unpacking safe session from intent");
                    sessionInfo = (SafeSession) intent.getParcelableExtra(SafeSession.class.getCanonicalName());
                }
                if (sessionInfo == null) {
                    LOGGER.error("Can't start ContinuousProver SafeSession null");
                } else {
                    if (sessionInfo.getStatus() == Session.Status.CLOSED
                        || sessionInfo.getStatus() == Session.Status.ERROR) {
                        LOGGER.warn("Can't start ContinuousProver SafeSession " + sessionInfo.getStatus());
                    } else {
                        // Here we do different things depending on the "type"
                        // of start command
                        // i.e. Sometimes the service is being asked to start a
                        // new continuous
                        // auth session, other times to pause/resume/stop an
                        // existing one.
                        final int ord = intent.getIntExtra(StartCommandType.class.getCanonicalName(),
                            StartCommandType.START.ordinal());
                        final StartCommandType type = StartCommandType.values()[ord];

                        if (type == StartCommandType.START) {
                            if (intent.hasExtra(PROXY_CHANNEL)) {
                                // Create a rendezvous channel
                                final Uri url = intent.getParcelableExtra(PROXY_CHANNEL);

                                new Thread(new Runnable() {
                                    public void run() {
                                        try {
                                            final URL rvpurl = SafeService.UriToURI(url).toURL();
                                            final RendezvousChannel channel = new RendezvousChannel(rvpurl);

                                            final CombinedVerifierProxy proxy = new RendezvousSigmaProxy(channel, new JsonMessageSerializer());

                                            Session session = sessionInfo.getSession(dbDataAccessor);
                                            SequenceNumber sequenceNumber = SequenceNumber.getRandomInstance();
                                            HandlerScheduler handlerScheduler = HandlerScheduler.getInstance();

                                            // Create the ContinousProver
                                            final ContinuousProver contProver = new ContinuousProver(
                                                session, proxy, PicoServiceImpl.this,
                                                handlerScheduler, sequenceNumber, pollServiceExecutor);

                                            // Add to provers map for further
                                            // actions (pause etc)
                                            provers.put(sessionInfo, contProver);

                                            LOGGER.debug("Start continuous authentication");

                                            // Start the continuous auth cycle
                                            new Thread(new Runnable() {
                                                public void run() {
                                                    contProver.updateVerifier();
                                                }
                                            }).start();


                                        } catch (MalformedURLException e) {
                                            LOGGER.error("Failed setting rendezvous address for continuous prover");
                                        } catch (IOException e) {
                                            LOGGER.error("Failed creating continuous prover");
                                        }
                                    }
                                }).start();
                            } else if (intent.hasExtra(PROXY_BT_ADDRESS) && intent.hasExtra(PROXY_BT_CHANNEL)) {
                                // Create a Bluetooth channel
                                final String hwAddress = intent.getStringExtra(PROXY_BT_ADDRESS);
                                final int channel = intent.getIntExtra(PROXY_BT_CHANNEL, 0);

                                new Thread(new Runnable() {
                                    public void run() {
                                        try {
                                            BluetoothManager bMgr = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                                            BluetoothAdapter bAdapter = bMgr.getAdapter();
                                            BluetoothDevice bDevice = bAdapter.getRemoteDevice(hwAddress);
                                            LOGGER.info("Continuous BT device address: " + hwAddress);
                                            LOGGER.info("Continuous BT channel: " + channel);

                                            //BluetoothSocket bSocket = bDevice.createRfcommSocketToServiceRecord(uuid);
                                            final BluetoothSocket bSocket;
                                            if (channel == 0) {
                                                bSocket = bDevice.createRfcommSocketToServiceRecord(CONTINUOUS_SERVICE_UUID);
                                            } else {
                                                Method m = bDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                                                bSocket = (BluetoothSocket) m.invoke(bDevice, channel);
                                            }

                                            for (int i = 0; i < 10; i++) {
                                                // Wait 1 second to ensure the server is listening
                                                synchronized (this) {
                                                    wait(1000);
                                                }

                                                try {
                                                    bSocket.connect();
                                                    break;
                                                } catch (IOException e) {
                                                    LOGGER.info("COULD NOT CONNECT, TRYING AGAIN");
                                                }
                                            }
                                            LOGGER.info("Bluetooth socket is connected");

                                            final CombinedVerifierProxy proxy = new SigmaProxy(
                                                bSocket, new JsonMessageSerializer());

                                            Session session = sessionInfo.getSession(dbDataAccessor);
                                            SequenceNumber sequenceNumber = SequenceNumber.getRandomInstance();
                                            HandlerScheduler handlerScheduler = HandlerScheduler.getInstance();

                                            // Create the ContinousProver
                                            final ContinuousProver contProver = new ContinuousProver(
                                                session, proxy, PicoServiceImpl.this,
                                                handlerScheduler, sequenceNumber, pollServiceExecutor);

                                            // Add to provers map for further
                                            // actions (pause etc)
                                            provers.put(sessionInfo, contProver);

                                            LOGGER.debug("Start continuous authentication");

                                            // Start the continuous auth cycle
                                            contProver.updateVerifier();
                                        } catch (IOException e) {
                                            LOGGER.error("Failed creating continuous prover");
                                            e.printStackTrace();
                                        } catch (NoSuchMethodException e) {
                                            LOGGER.error("No such method");
                                            e.printStackTrace();
                                        } catch (IllegalAccessException e) {
                                            e.printStackTrace();
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            } else {
                                LOGGER.error("Failed creating continuous prover");
                            }
                        } else if (type != StartCommandType.START) {
                            final ContinuousProver prover = provers.get(sessionInfo);
                            if (prover != null) {
                                if (type == StartCommandType.PAUSE) {
                                    LOGGER.debug("Pause continuous authentication");
                                    new Thread(new Runnable() {
                                        public void run() {
                                            prover.pause();
                                        }
                                    }).start();
                                } else if (type == StartCommandType.RESUME) {
                                    LOGGER.debug("Resume continuous authentication");
                                    new Thread(new Runnable() {
                                        public void run() {
                                            prover.resume();
                                        }
                                    }).start();
                                } else if (type == StartCommandType.STOP) {
                                    LOGGER.debug("Stop continuous authentication");
                                    provers.remove(sessionInfo);
                                    new Thread(new Runnable() {
                                        public void run() {
                                            prover.stop();
                                        }
                                    }).start();
                                    // Stop the service (if this was the only
                                    // continuous auth
                                    // session)
                                    if (stopSelfResult(startId)) {
                                        // Unregister broadcast receiver if the
                                        // service is going to
                                        // stop
                                        broadcastManager.unregisterReceiver(broadcastReceiver);
                                        LOGGER.info("Unregistered lock broadcast receiver");
                                    }
                                } else {
                                    LOGGER.error("Invalid StatTypeCommand");
                                }
                            } else {
                                LOGGER.warn("No prover found for session");
                                Toast toast = Toast.makeText(this, "No Prover found for Session", Toast.LENGTH_SHORT);
                                toast.show();
                            }
                        }

                    }
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LOGGER.debug("PicoServiceImpl is being destroyed...");
        super.onDestroy();
        // TODO see:
        // http://ormlite.com/javadoc/ormlite-android/com/j256/ormlite/android/apptools/OpenHelperManager.html#releaseHelper%28%29
        // OpenHelperManager.releaseHelper();
    }

    @Override
    public List<SafeKeyPairing> getKeyPairings(final SafeService service) throws IOException {

        final byte[] c = service.getCommitment();
        LOGGER.debug("Getting KeyPairings for service commitment {}", c);
        final List<KeyPairing> pairings = dbDataAccessor.getKeyPairingsByServiceCommitment(c);

        // Convert the Pairing instances to PairingInfo before returning the
        // result to the UI
        final List<SafeKeyPairing> pairingInfos = new ArrayList<SafeKeyPairing>();
        for (KeyPairing p : pairings) {
            pairingInfos.add(new SafeKeyPairing(p));
        }
        return pairingInfos;
    }

    @Override
    @Deprecated
    public List<SafeLensPairing> getLensPairings(final SafeService service) throws IOException {

        final byte[] c = service.getCommitment();
        LOGGER.debug("Getting LensPairings for service commitment {}", c);

        final List<LensPairing> pairings = dbDataAccessor.getLensPairingsByServiceCommitment(c);

        // Convert the Pairing instances to PairingInfo before returning the
        // result to the UI
        final List<SafeLensPairing> pairingInfos = new ArrayList<SafeLensPairing>();
        for (final LensPairing p : pairings) {
            pairingInfos.add(new SafeLensPairing(p));
        }
        return pairingInfos;
    }

    /*
     * Return a ProxyService for a service identified by a ServiceInfo instance.
     *
     * @param serviceInfo the service to retur the {@link CombinedVerifierProxy} for.
     */
    private CombinedVerifierProxy getServiceProxy(SafeService serviceInfo) {
        // Get whole address
        final Uri address = serviceInfo.getAddress();

        // Instantiate and return a concrete proxy subclass. Depends
        if (address.getScheme().equals("tcp")) {
            return new SocketCombinedProxy(address.getHost(), address.getPort(), new JsonMessageSerializer());
        } else {
            // TODO change to log this and return null or something..
            throw new IllegalArgumentException("unsupported service protocol: " + address.getScheme());
        }
    }

    @Deprecated
    @Override
    public SafeSession keyAuthenticate(final SafeKeyPairing pairing) throws IOException, PairingNotFoundException {

        // Verify the method's preconditions
        checkNotNull(pairing);

        LOGGER.debug("Authenticating key pairing {}", pairing);

        // Promote to a full KeyPairing
        final KeyPairing keyPairing = pairing.getKeyPairing(dbDataAccessor);
        if (keyPairing != null) {
            // Get a verifier proxy using the service info (of the pairing
            // info):
            CombinedVerifierProxy proxy = getServiceProxy(pairing.getSafeService());

            // Construct the prover:
            ServiceSigmaProver prover = new ServiceSigmaProver(keyPairing, proxy, dbDataFactory);

            // Carry out the authentication and get the Session instance result:
            final Session session = prover.startSession();

            // TO be removed - no need to persist sessions
            if (session.getStatus() != Session.Status.ERROR) {
                // If the session is ok, then save it.
                LOGGER.debug("Persisting session");
                session.save();
            }

            final SafeSession safeSession = new SafeSession(session);
            if (session.getStatus() == Session.Status.ACTIVE) {
                // Create the ContinousProver
                ContinuousProver contProver = prover.getContinuousProver(proxy, session, this,
                    HandlerScheduler.getInstance(), pollServiceExecutor);

                // Add to provers map for further actions (pause etc)
                provers.put(safeSession, contProver);

                // Start continuous authentication
                final Intent intent = new Intent(getApplicationContext(), PicoServiceImpl.class);
                intent.putExtra(StartCommandType.class.getCanonicalName(), StartCommandType.START.ordinal());
                intent.putExtra(SafeSession.class.getCanonicalName(), safeSession);
                startService(intent);
            }

            return safeSession;
        } else {
            LOGGER.error("KeyPairing is invalid");
            throw new PairingNotFoundException("KeyPairing is invalid");
        }
    }

    @Override
    @Deprecated
    public SafeSession lensAuthenticate(final SafeLensPairing pairing, final Uri newServiceAddress,
                                        final String loginForm, final String cookieString) throws IOException, PairingNotFoundException {

        // Verify the method's preconditions
        checkNotNull(pairing);

        // Promote to a full LensPairing
        final LensPairing credentialPairing = pairing.getLensPairing(dbDataAccessor);
        if (credentialPairing != null) {

            // Get the address to authenticate to
            final URI serviceAddress;
            if (newServiceAddress != null) {
                serviceAddress = SafeService.UriToURI(newServiceAddress);
            } else {
                serviceAddress = credentialPairing.getService().getAddress();
            }

            LOGGER.debug("Authenticating credential pairing: {}, {}", pairing, serviceAddress);

            // Construct the prover TODO fix this
            final Prover prover = new LensProver(credentialPairing, serviceAddress, loginForm, cookieString,
                dbDataFactory);

            // Carry out the authentication and get the Session instance result:
            final Session session = prover.startSession();
            if (session.getStatus() != Session.Status.ERROR) {
                // If the session is ok, then save it...
                session.save();

                // and save the new service address
                credentialPairing.getService().setAddress(serviceAddress);
                credentialPairing.save();
            }

            return new SafeSession(session);
        } else {
            LOGGER.error("LensPairing is invalid");
            throw new PairingNotFoundException("CredentialPairing is invalid");
        }
    }

    @Override
    public SafePairing renamePairing(final SafePairing safePairing, final String newName)
        throws IOException, PairingNotFoundException {
        // Verify the method's preconditions
        checkNotNull(safePairing, "Cannot rename pairing with a null safe pairing");
        checkNotNull(newName, "Cannot rename pairing to Null");
        if (!safePairing.idIsKnown()) {
            throw new IllegalArgumentException("Cannot rename pairing with safe pairing with unknown id");
        }

        final Pairing pairing = safePairing.getPairing(dbDataAccessor);
        if (pairing != null) {
            pairing.setName(newName);

            // Persist update to underlying storage.
            pairing.save();

            // Return the updated PairingInfo
            return new SafePairing(pairing);
        } else {
            throw new PairingNotFoundException("Pairing is invalid");
        }
    }

    @Override
    public void pauseSession(final SafeSession sessionInfo) {
        // Verify the method's preconditions
        checkNotNull(sessionInfo);

        final Intent intent = new Intent(getApplicationContext(), PicoServiceImpl.class);
        intent.putExtra(StartCommandType.class.getCanonicalName(), StartCommandType.PAUSE.ordinal());
        intent.putExtra(SafeSession.class.getCanonicalName(), sessionInfo);
        startService(intent);
    }

    @Override
    public void resumeSession(final SafeSession sessionInfo) {
        // Verify the method's preconditions
        checkNotNull(sessionInfo);

        final Intent intent = new Intent(getApplicationContext(), PicoServiceImpl.class);
        intent.putExtra(StartCommandType.class.getCanonicalName(), StartCommandType.RESUME.ordinal());
        intent.putExtra(SafeSession.class.getCanonicalName(), sessionInfo);
        startService(intent);
    }

    @Override
    public void closeSession(final SafeSession sessionInfo) {
        // Verify the method's preconditions
        checkNotNull(sessionInfo);

        final Intent intent = new Intent(getApplicationContext(), PicoServiceImpl.class);
        intent.putExtra(StartCommandType.class.getCanonicalName(), StartCommandType.STOP.ordinal());
        intent.putExtra(SafeSession.class.getCanonicalName(), sessionInfo);
        startService(intent);
    }

    @Override
    @Deprecated
    public void getTerminal(final byte[] terminalCommitment, final GetTerminalCallback callback) {
        new AsyncTask<byte[], Void, Optional<Terminal>>() {

            @Override
            protected Optional<Terminal> doInBackground(byte[]... params) {
                try {
                    final Terminal t = dbDataAccessor.getTerminalByCommitment(terminalCommitment);
                    return Optional.fromNullable(t);
                } catch (IOException e) {
                    return Optional.absent();
                }
            }

            @Override
            public void onPostExecute(Optional<Terminal> result) {
                callback.onGetTerminalResult(result);
            }
        }.execute(terminalCommitment);
    }

    @Override
    @Deprecated
    public List<Terminal> getTerminals() throws IOException {
        return dbDataAccessor.getAllTerminals();
    }

    @Override
    @Deprecated
    public void getTerminals(final GetTerminalsCallback callback) {
        new AsyncTask<Void, Void, Optional<List<Terminal>>>() {

            private IOException e;

            @Override
            protected Optional<List<Terminal>> doInBackground(Void... arg0) {
                try {
                    return Optional.of(getTerminals());
                } catch (IOException e) {
                    this.e = e;
                    return Optional.absent();
                }
            }

            @Override
            protected void onPostExecute(Optional<List<Terminal>> result) {
                if (result.isPresent()) {
                    callback.onGetTerminalsResult(result.get());
                } else {
                    callback.onGetTerminalsError(e);
                }
            }
        }.execute();
    }

    /**
     * Updates the system broadcasting the SafeSession equivalent to session.
     *
     * @param session The session to update the info for.
     */
    private void updateSafeSessionInfo(final Session session) {
        // Verify the method's preconditions
        checkNotNull(session, "Session cannot be null");
        LOGGER.debug("Session state: " + session.getStatus());
        SafeSession safeSession = null;
        for (Map.Entry<SafeSession, ContinuousProver> e : provers.entrySet()) {
            if (e.getKey().getId() == session.getId()) {
                safeSession = e.getKey();
            }
        }

        if (safeSession != null) {
            // Replace the key with the update session
            ContinuousProver theProver = provers.remove(safeSession);
            safeSession = new SafeSession(session);
            provers.put(safeSession, theProver);
        } else {
            /*
			 * If there is no SafeSession it means we are in a stopped stated. And the session was
			 * deleted from the list.
			 * TODO: Consider the following behaviour. Instead of removing on stop, we could always
			 * keep it. And delete only if the user slides to the left or something
			 */
            safeSession = new SafeSession(session);
        }

        broadcastInfo(safeSession);
        updateForegroundStatus();
    }

    @Override
    public void sessionPaused(final Session session) {
        session.setStatus(Session.Status.PAUSED);
        updateSafeSessionInfo(session);
    }

    @Override
    public void sessionContinued(final Session session) {
        session.setStatus(Session.Status.ACTIVE);
        updateSafeSessionInfo(session);
    }

    @Override
    public void sessionStopped(final Session session) {
        session.setStatus(Session.Status.CLOSED);
        updateSafeSessionInfo(session);
    }

    @Override
    public void sessionError(final Session session) {
        session.setStatus(Session.Status.ERROR);
        updateSafeSessionInfo(session);
    }

    @Override
    public void tick(final Session session) {
        session.setLastAuthDate(new Date());
        updateSafeSessionInfo(session);
    }
}
