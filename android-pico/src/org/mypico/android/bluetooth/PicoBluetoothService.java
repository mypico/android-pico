package org.mypico.android.bluetooth;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import org.mypico.android.R;
import org.mypico.android.core.PicoServiceImpl;
import org.mypico.android.core.SettingsActivity;
import org.mypico.android.data.SafeSession;
import org.mypico.jpico.data.session.Session;

/**
 * Background service that will run all the time, listening for requests for authentication over
 * Bluetooth. This replaces the QR code visual channel.
 * <p>
 * When an authentication request is received, it gets stored as an "available login", represented
 * by a {@link AvailableBluetoothLogin} object. Once the requests are no longer received, the
 * available login becomes unavailable and goes away. You can register a listener for these events
 * by binding to the service and calling {@code registerBluetoothLoginListener} -- see
 * {@link PicoBluetoothServiceBinder}.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class PicoBluetoothService extends Service {
    private static final String TAG = PicoBluetoothService.class.getSimpleName();

    /**
     * Broadcast sent by {@link org.mypico.android.pairing.AuthenticateActivity} and picked up by
     * {@link #mAuthenticationFinishedReceiver} when an authentication finishes.
     */
    public static final String AUTHENTICATION_FINISHED = "AUTHENTICATION_FINISHED";

    /**
     * Start the service with this action to perform any necessary changes after the Bluetooth mode
     * has been changed.
     */
    public static final String ACTION_BLUETOOTH_MODE_CHANGED = "ACTION_BLUETOOTH_MODE_CHANGED";

    /**
     * Notification ID for the Bluetooth authentication notification
     */
    private static final int BLUETOOTH_NOTIFICATION_ID = 0;
    /**
     * Notification ID for the "Bluetooth is running notification"
     */
    public static final int RUNNING_NOTIFICATION_ID = 1;

    /**
     * How long, in milliseconds, an available login remains after a Bluetooth message is received.
     * This should be slightly more than the broadcast interval of the service (which is currently
     * 5000 ms).
     */
    private static final int AVAILABLE_LOGIN_TIMEOUT_MS = 6000;

    /**
     * The interface through which Activities that bind to this service can interact with it.
     */
    public class PicoBluetoothServiceBinder extends Binder {

        /**
         * Register a {@link BluetoothLoginListener} to receive callbacks when Bluetooth logins
         * become availble/unavailable. Be sure to call
         * {@link #unregisterBluetoothLoginListener(BluetoothLoginListener)} when you no longer need
         * to receieve these callbacks, particularly if the callback object is linked to a
         * {@link Context}.
         * <p>
         * After registration, if there are currently Bluetooth logins available the listener's
         * {@link BluetoothLoginListener#onBluetoothLoginAvailable} method will be called.
         *
         * @param listener The listener to register.
         * @see BluetoothLoginListener
         */
        public void registerBluetoothLoginListener(BluetoothLoginListener listener) {
            synchronized (bluetoothLoginListeners) {
                bluetoothLoginListeners.add(listener);
            }
            if (getAvailableBluetoothLogins().length != 0)
                listener.onBluetoothLoginAvailable();
        }

        /**
         * Unregister a {@link BluetoothLoginListener} previously registered with
         * {@link #registerBluetoothLoginListener(BluetoothLoginListener)}.
         *
         * @param listener The listener to unregister.
         * @see BluetoothLoginListener
         */
        public void unregisterBluetoothLoginListener(BluetoothLoginListener listener) {
            synchronized (bluetoothLoginListeners) {
                bluetoothLoginListeners.remove(listener);
            }
        }

        /**
         * Get a list of available Bluetooth logins. In the current implementation, at most one
         * login is returned.
         *
         * @return Array of {@link AvailableBluetoothLogin}s. If no login is available, a
         * zero-length array is returned.
         */
        public AvailableBluetoothLogin[] getAvailableBluetoothLogins() {
            AvailableBluetoothLogin[] result;
            if (mostRecentAvailableLogin == null) {
                result = new AvailableBluetoothLogin[0];
            } else {
                result = new AvailableBluetoothLogin[1];
                result[0] = mostRecentAvailableLogin;
            }
            return result;
        }

    }

    /**
     * Inner class to receive notifications of Bluetooth state change (turned on, turned off).
     * This will (re)start and stop the server thread as appropriate.
     * <p>
     * Reference:
     * http://stackoverflow.com/questions/5388609/notification-if-the-bluetooth-is-turned-off-in-android-app
     */
    private class BluetoothStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // verify that it's the right intent
            if (!intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
                return;

            // get the adapter state
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (BluetoothAdapter.STATE_OFF == state) {
                // Bluetooth has been turned off
                Log.d(TAG, "Bluetooth turned off");
                stopServer();
            } else {
                // Bluetooth has been turned on
                Log.d(TAG, "Bluetooth turned on");
                startServer();
            }
        }
    }

    /**
     * Inner class to receive notifications when the start activity finishes
     * This should enable the bluetooth server to start new authentication activities
     */
    private class AuthenticationFinishedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // verify that it's the right intent
            if (!intent.getAction().equals(AUTHENTICATION_FINISHED))
                return;

            waitingForAuthentication = false;
        }
    }

    /**
     * Inner class to handle Bluetooth connections.
     */
    private class ConnectionHandler implements OnBluetoothConnectionAccepted {
        /**
         * Maximum number of bytes to read over the Bluetooth connection before giving up. This is
         * to help prevent denial-of-service attacks. Note that QR codes themselves can store only
         * up to around 3000 bytes.
         */
        private static final int MAX_STREAM_LENGTH = 4096;

        /**
         * When a connection is made, read the JSON string and pass on to
         * {@link #handleBluetoothMessage(String)} for processing (unless {@link #waitingForAuthentication}).
         *
         * @param socket The socket representing the connection to the client device.
         */
        @Override
        public void onBluetoothConnectionAccepted(BluetoothSocket socket) {
            // receive data from the socket until it gets closed (by the remote device)
            String message;
            try {
                InputStream is = socket.getInputStream();
                message = readInputStream(is);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }

            Log.d(TAG, "QR code text received: " + message);
            Log.d(TAG, "waitingForAuthentication: " + waitingForAuthentication);
            if (!waitingForAuthentication)
                handleBluetoothMessage(message);
        }

        /**
         * Read bytes from an InputStream until either it gets closed, or the number of bytes
         * exceeds {@code MAX_STREAM_LENGTH}, and turn it into a String.
         * <p>
         * Source: http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
         *
         * @param stream InputStream to read from.
         * @return The contents of the stream as a String.
         */
        String readInputStream(InputStream stream) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length, totalLength = 0;
            try {
                while (totalLength < MAX_STREAM_LENGTH &&
                    (length = stream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                    totalLength += length;
                }
            } catch (IOException ignored) {
            }
            return result.toString();
        }

    }

    /**
     * Callback interface triggered when a Bluetooth beacon is received and a login is available.
     */
    private class ShowLoginNotificationListener implements BluetoothLoginListener {
        @Override
        public void onBluetoothLoginAvailable() {
            showAuthNotification(mostRecentAvailableLogin.intent, mostRecentAvailableLogin.name);
        }

        @Override
        public void onBluetoothLoginUnavailable() {
            hideAuthNotification();
        }
    }

    // receiver for detecting when Bluetooth is turned off or on
    private final BluetoothStateChangedReceiver mBluetoothStateChangedReceiver =
        new BluetoothStateChangedReceiver();
    // receiver for telling us when an authentication process has finished
    private final AuthenticationFinishedReceiver mAuthenticationFinishedReceiver =
        new AuthenticationFinishedReceiver();
    // handles Bluetooth connections made to the server
    private final ConnectionHandler mConnectionHandler = new ConnectionHandler();
    // login listener that shows and hides the notification as the login becomes available or not
    private final ShowLoginNotificationListener mShowLoginNotificationListener =
        new ShowLoginNotificationListener();

    // the Binder for this service
    private final PicoBluetoothServiceBinder mBinder = new PicoBluetoothServiceBinder();

    // the Bluetooth server, to which connections are made by devices wishing to authenticate
    private PicoBluetoothServer mServer;
    // true if the device has a Bluetooth adapter
    private boolean mDeviceHasBluetooth;
    // cached preference value so we don't need a Context every time
    private String mBluetoothMode;

    // Handler that deals with the authentication period timeout
    private final Handler timeoutHandler = new Handler();
    // Runner that will hide the Bluetooth authentication notification
    private final Runnable timeoutAvailableLoginRunner = new Runnable() {
        @Override
        public void run() {
            // the login is no longer available, notify listeners
            mostRecentAvailableLogin = null;
            callbackBluetoothLoginUnavailable();
        }
    };

    // listeners for when Bluetooth logins become available, registered through the Binder
    private final LinkedList<BluetoothLoginListener> bluetoothLoginListeners = new LinkedList<>();
    // the login request we most recently received
    private AvailableBluetoothLogin mostRecentAvailableLogin;
    // True if we started some activity and it did not finish yet
    private boolean waitingForAuthentication;


    /**
     * Handle processing of the QR code text received over Bluetooth. This method checks whether the
     * request is valid, and if so passes it (depending on the Bluetooth mode) to either
     * {@link #handleLoginAutomatic} or {@link #handleLoginManual}.
     * <p>
     * This method gets called every time an authentication request is received, which at the time
     * of writing is every 5 seconds when a service is waiting for someone to log in. Note however
     * that this method does not get called if {@link #waitingForAuthentication} is {@code true}.
     * <p>
     * The request will be ignored if it is malformed (not valid JSON, or does not form a valid
     * {@code VisualCode}), or if it is valid but we are not paired with the service. We then have
     * two cases to consider, depending on the Bluetooth mode.
     * <ul>
     * <li>In automatic mode, we have to check whether there is a continuous session running with
     * the service before we handle the request further. Otherwise, if they just locked their
     * computer (which makes it request authentication) it will immediately unlock. Instead, if
     * there is a session running we fall back to manual mode. If there is no session the request
     * is handled by {@link #handleLoginAutomatic}</li>
     * <li>In manual mode, a request is always handled after this point, because we need to keep
     * track of available logins. If the computer is locked, so that there is a session running,
     * the login must still be available so they can use Bluetooth to unlock. This case is handled
     * by {@link #handleLoginManual}.</li>
     * </ul>
     *
     * @param message The QR code text to be handled. Should be a JSON string.
     */
    private void handleBluetoothMessage(String message) {
        // instantiate a login from the JSON message we received
        final AvailableBluetoothLogin login = AvailableBluetoothLogin.fromJson(this, message);
        // an invalid message produces a null login
        if (login == null) {
            Log.d(TAG, "Message is not a valid visual code");
            return;
        }
        // we also don't care about services we are not paired with, shown by a null service name
        if (login.name == null) {
            Log.d(TAG, "Not paired with this service; rejecting request");
            return;
        }
        // need some flag to start an activity from a service
        login.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // this receives the list of continuous sessions
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // unregister this receiver, because it should never fire again
                LocalBroadcastManager.getInstance(PicoBluetoothService.this)
                    .unregisterReceiver(this);
                // double-check that it's the right action
                if (!PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS.equals(intent.getAction())) {
                    Log.d(TAG, "Unrecognized action " + intent.getAction());
                    return;
                }

                // get the list of sessions and search it for an active session with the service
                final ArrayList<SafeSession> list = intent
                    .getParcelableArrayListExtra(ArrayList.class.getCanonicalName());
                boolean found = false;
                for (SafeSession session : list) {
                    if (Arrays.equals(session.getSafePairing().getSafeService().getCommitment(), login.commitment)
                        && session.getStatus() != Session.Status.CLOSED
                        && session.getStatus() != Session.Status.ERROR) {
                        found = true;
                        break;
                    }
                }

                // handle it as appropriate
                if (found) {
                    // if we currently have a valid session with this service, go manual
                    Log.d(TAG, "Running continuous session with this service. Login must be manual.");
                    handleLoginManual(login);
                } else {
                    // otherwise, there's no session, so log in automatically
                    handleLoginAutomatic(login);
                }
            }
        };

        if (mBluetoothMode.equals(BluetoothMode.AUTOMATIC_MODE)) {
            // In this mode, we automatically authenticate to everything UNLESS we have a continuous
            // session running. This solves the problem where Pico instantly logs you back in if you
            // lock your computer. Instead we fall back to manual login for this case.

            // ask the PicoService to tell us about the current continuous sessions
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
                new IntentFilter(PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS));
            final Intent requestIntent = new Intent(this, PicoServiceImpl.class);
            requestIntent.setAction(PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS);
            startService(requestIntent);

        } else {
            // We don't care about whether there's already a session -- the login needs to be shown
            // as available if we're getting messages, and session deduplication is handled by the
            // PicoService
            handleLoginManual(login);
        }

    }

    /**
     * Handles the authentication request when we're in automatic mode. The user will always be
     * logged in immediately.
     *
     * @param login The login for the authentication request.
     */
    private void handleLoginAutomatic(final AvailableBluetoothLogin login) {
        // authenticate to everything automatically
        startActivity(login.intent);
        waitingForAuthentication = true;
    }

    /**
     * Handles the authentication request when we're in manual mode. Here we keep track of available
     * logins, and the user chooses when (and to what) they want to log in.
     *
     * @param login The login for the authentication request.
     */
    public void handleLoginManual(final AvailableBluetoothLogin login) {
        // this flag indicates this is the first request from this service -- either if there
        // was no previous available login, or if it's from a different service
        final boolean isNewLogin = mostRecentAvailableLogin == null ||
            !Arrays.equals(mostRecentAvailableLogin.commitment, login.commitment);

        // store the login as the most recent available login
        mostRecentAvailableLogin = login;
        resetAvailableLoginTimeout();
        if (isNewLogin) {
            // trigger callbacks to listeners saying that a new login is available
            callbackBluetoothLoginAvailable();
        }
    }

    /**
     * Reset the timeout for the most recent available login.
     * <p>
     * After the elapsed time -- unless this method is called again -- the
     * {@link #timeoutAvailableLoginRunner} will be run.
     */
    private void resetAvailableLoginTimeout() {
        // cancel any previous one
        timeoutHandler.removeCallbacks(timeoutAvailableLoginRunner);
        // start a timer to hide it after the timeout
        timeoutHandler.postDelayed(timeoutAvailableLoginRunner, AVAILABLE_LOGIN_TIMEOUT_MS);
    }

    /**
     * Trigger the {@code onBluetoothLoginAvailable} callback on the registered
     * {@link BluetoothLoginListener}s.
     */
    void callbackBluetoothLoginAvailable() {
        // tell our private listeners first
        if (mBluetoothMode.equals(BluetoothMode.NOTIFICATION_MODE))
            mShowLoginNotificationListener.onBluetoothLoginAvailable();

        for (BluetoothLoginListener listener : bluetoothLoginListeners)
            listener.onBluetoothLoginAvailable();
    }

    /**
     * Trigger the {@code onBluetoothLoginUnavailable} callback on the registered
     * {@link BluetoothLoginListener}s.
     */
    void callbackBluetoothLoginUnavailable() {
        // tell our private listeners first
        if (mBluetoothMode.equals(BluetoothMode.NOTIFICATION_MODE))
            mShowLoginNotificationListener.onBluetoothLoginUnavailable();

        for (BluetoothLoginListener listener : bluetoothLoginListeners)
            listener.onBluetoothLoginUnavailable();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mBluetoothMode = BluetoothMode.getCurrentMode(this);

        if (mBluetoothMode.equals(BluetoothMode.AUTOMATIC_MODE) ||
            mBluetoothMode.equals(BluetoothMode.NOTIFICATION_MODE)) {
            startForeground();
        } else {
            stopForeground(true);
        }

        if (intent != null) {
            final String action = intent.getAction();
            Log.d(TAG, "onStartCommand: " + action);
            if (ACTION_BLUETOOTH_MODE_CHANGED.equals(action)) {
                // Currently the only thing we need to do is show/hide the notification if the mode
                // has changed to/from notification mode.
                if (mBluetoothMode.equals(BluetoothMode.NOTIFICATION_MODE)) {
                    // if there's an available login, show the notification
                    if (mostRecentAvailableLogin != null)
                        showAuthNotification(mostRecentAvailableLogin.intent,
                            mostRecentAvailableLogin.name);
                } else {
                    // we're not in notification mode so make sure no notification is showing
                    hideAuthNotification();
                }
            }
        }
        return Service.START_STICKY;
    }

    /**
     * Bind to this service. The resulting IBinder may be cast to a
     * {@link PicoBluetoothServiceBinder}.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void startForeground() {
        if (mServer != null && mServer.isAlive()) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.bluetooth_notification_service_running))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.pico_orange))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
            startForeground(RUNNING_NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service started");

        mBluetoothMode = BluetoothMode.getCurrentMode(this);
        if (mBluetoothMode.equals(BluetoothMode.AUTOMATIC_MODE) ||
            mBluetoothMode.equals(BluetoothMode.NOTIFICATION_MODE)) {
            startForeground();
        } else {
            stopForeground(true);
        }


        // check that the device actually has Bluetooth
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mDeviceHasBluetooth = (adapter != null);
        if (!mDeviceHasBluetooth) {
            // there's no point having the service running if there's no Bluetooth!
            stopSelf();
            return;
        }

        // register to be notified when Bluetooth is turned on or off
        registerReceiver(mBluetoothStateChangedReceiver,
            new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        waitingForAuthentication = false;
        registerReceiver(mAuthenticationFinishedReceiver,
            new IntentFilter(AUTHENTICATION_FINISHED));

        // if Bluetooth is on, start the server
        if (adapter.getState() == BluetoothAdapter.STATE_ON)
            startServer();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service finished");

        if (mDeviceHasBluetooth) {
            // unregister the state change receiver
            unregisterReceiver(mBluetoothStateChangedReceiver);
            unregisterReceiver(mAuthenticationFinishedReceiver);
        }
    }

    /**
     * Start the Bluetooth server
     */
    synchronized void startServer() {
        if (mServer == null) {
            mServer = new PicoBluetoothServer(mConnectionHandler);
            mServer.start();
            startForeground();
        }
    }

    /**
     * Stop the Bluetooth server
     */
    synchronized void stopServer() {
        if (mServer != null) {
            mServer.cancel();
            mServer = null;
            stopForeground(true);
        }
    }

    /**
     * Show a notification asking the user to confirm authentication.
     *
     * @param intent The intent that will be launched when the request is accepted.
     */
    void showAuthNotification(Intent intent, String serviceName) {
        final long[] bzzt = {0, 100, 250, 100};
        // turn on the screen automatically
        wakeDevice();
        // add a flag to start it as a separate action
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // create a notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle(getString(R.string.bluetooth_notification__log_in__title))
            .setContentText(getString(R.string.bluetooth_notification__log_in__text, serviceName))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getResources().getColor(R.color.pico_orange))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(bzzt)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT));
        // show it
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(BLUETOOTH_NOTIFICATION_ID, builder.build());
    }

    /**
     * Hide the notification created by {@link #showAuthNotification(Intent, String)}.
     */
    void hideAuthNotification() {
        // remove the notification
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(BLUETOOTH_NOTIFICATION_ID);
    }

    /**
     * Wake up the phone and turn on the screen.
     */
    void wakeDevice() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (!manager.isScreenOn()) {
            PowerManager.WakeLock wakeLock = manager.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                "PicoBluetoothService");
            wakeLock.acquire(10000);
        }
    }

}
