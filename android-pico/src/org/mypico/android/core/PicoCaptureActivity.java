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

import org.mypico.android.R;
import org.mypico.android.bluetooth.AvailableBluetoothLogin;
import org.mypico.android.bluetooth.BluetoothLoginListener;
import org.mypico.android.bluetooth.PicoBluetoothService;
import org.mypico.android.qrscanner.BaseScannerActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

/**
 * Extends the {@link BaseScannerActivity} class to provide a QR code scanner with the main menu
 * (menu/pico_capture) and connectivity overlay (see layout/activity_pico_capture and
 * layout/activity_capture_overlay resources).
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public final class PicoCaptureActivity extends BaseScannerActivity {

    // Allow us to keep track of whether the device is connected
    // and the widget to display if we're not
    private boolean connectionRequired = true;
    private View noConnection;
    private ConnectivityReceiver broadcastReceiver;
    // This replicates the private value in
    // com.google.zxing.client.android.CaptureActivity
    private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

    private ServiceConnection bluetoothServiceConn;
    private PicoBluetoothService.PicoBluetoothServiceBinder bluetoothServiceBinder;

    private AvailableBluetoothLogin quickBluetoothLogin;
    private View quickBluetoothLoginUI;
    private TextView quickBluetoothLoginName;

    /**
     * Receives notifications from Android about connectivity changes These are
     * registered dynamically in the code against the two intents
     * android.net.conn.CONNECTIVITY_CHANGE and
     * android.net.conn.WIFI_STATE_CHANGED which require the permissions
     * android.permission.ACCESS_NETWORK_STATE to be added to the manifest.
     * <p>
     * See
     * http://www.vogella.com/tutorials/AndroidBroadcastReceiver/article.html
     * and
     * http://www.androidhive.info/2012/07/android-detect-internet-connection
     * -status/ and
     * http://viralpatel.net/blogs/android-internet-connection-status
     * -network-change/
     *
     * @author David Llewellyn-Jones <dl551@cam.ac.uk>
     */
    private class ConnectivityReceiver extends BroadcastReceiver {
        private View connectionSymbol = null;
        private boolean currentlyConnected;

        /**
         * Set the UI widget that will be hidden if there's a data connection
         * available or shown if there's no data connection
         *
         * @param connectionSymbol The widget to show/hide depending on the connection
         */
        public void setConectionView(View connectionSymbol) {
            this.connectionSymbol = connectionSymbol;
            updateWidgetVisibility();
        }

        /**
         * Returns whether or not there's a currently available data connection
         *
         * @return true if there's a connection available, false otherwise
         */
        public boolean isConnected() {
            return currentlyConnected;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * android.content.BroadcastReceiver#onReceive(android.content.Context,
         * android.content.Intent)
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get session info from intent and check not null
            currentlyConnected = getConnectivityStatus(context);

            Log.d(ConnectivityReceiver.class.getSimpleName(),
                "Connectivity state changed to " + currentlyConnected);
            // Alter the visibility of the currently set widget based on the
            // state of the network
            updateWidgetVisibility();
        }

        /**
         * Updates the visibility of the registered widget depending on the
         * connectivity state
         */
        private void updateWidgetVisibility() {
            if (connectionSymbol != null) {
                if (currentlyConnected == true) {
                    connectionSymbol.setVisibility(View.INVISIBLE);
                } else {
                    connectionSymbol.setVisibility(View.VISIBLE);
                }
            }
        }

        /**
         * Determines whether there's an active cata connection (WiFi or mobile)
         *
         * @param context The Context in which the receiver is running.
         * @return true if there's a WiFi or mobile data connection; false o/w
         */
        private boolean getConnectivityStatus(Context context) {
            boolean connected = false;
            ConnectivityManager conman = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

            // Check whether there's any kind of network
            NetworkInfo activeNetwork = conman.getActiveNetworkInfo();
            if (null != activeNetwork) {
                // Check the type of connection we have
                switch (activeNetwork.getType()) {
                    case ConnectivityManager.TYPE_WIFI:
                        // Intentional fallthrough
                    case ConnectivityManager.TYPE_MOBILE:
                        connected = true;
                        break;
                    default:
                        // Not really necessary, but we do it for clarity
                        connected = false;
                        break;
                }
            }
            return connected;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_pico_capture);

        // Capture network change messages
        broadcastReceiver = new ConnectivityReceiver();

        // Insert the "need network connectivity" warning into the existing UI
        noConnection = findViewById(R.id.noconnection);

        // Get Views associated with the Quick Bluetooth Login UI
        quickBluetoothLoginUI = findViewById(R.id.quick_bluetooth_login);
        quickBluetoothLoginName = (TextView) findViewById(R.id.quick_bluetooth_login_name);

        // bind to PicoBluetoothService and get the current Bluetooth status
        Intent intent = new Intent(this, PicoBluetoothService.class);
        bluetoothServiceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                bluetoothServiceBinder = (PicoBluetoothService.PicoBluetoothServiceBinder) iBinder;
                bluetoothServiceBinder.registerBluetoothLoginListener(mBluetoothLoginListener);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        bindService(intent, bluetoothServiceConn, BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set up the appropriate widget to show/hide
        // It will be made visible if the network becomes unavailable
        noConnection.setVisibility(android.widget.ImageView.INVISIBLE);

        if (connectionRequired) {
            // Register to receive info about when connectivity changes
            // This will be unregistered in onPause()
            // The manifest has android.permission.ACCESS_NETWORK_STATE
            // permission requested for this
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            filter.addAction("android.net.conn.WIFI_STATE_CHANGED");
            this.registerReceiver(broadcastReceiver, filter);

            // This will also set the appropriate visibility
            broadcastReceiver.setConectionView(noConnection);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (connectionRequired) {
            // Unregister the receiver listening for when the connection changes
            // This was registered in onResume()
            this.unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the PicoBluetoothService
        bluetoothServiceBinder.unregisterBluetoothLoginListener(mBluetoothLoginListener);
        bluetoothServiceBinder = null;
        unbindService(bluetoothServiceConn);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (getIntent().getBooleanExtra(AcquireCodeActivity.NO_MENU, false)) {
            return false;
        } else {
            // Call the superclass's implementation to call the base
            // implementation.
            super.onCreateOptionsMenu(menu);
            // Add our own items
            getMenuInflater().inflate(R.menu.pico_capture, menu);

            // Return true to ensure menu is displayed
            return true;
        }
    }

    /**
     * Don't handle the QR code if a connection is required and there isn't one.
     */
    @Override
    public boolean onQRCodeFound(Result result) {
        if ((!connectionRequired) || broadcastReceiver.isConnected()) {
            // Decode the QR-code as expected
            return super.onQRCodeFound(result);
        } else { // Not connected
            // If we're not connected, warn the user and restart the code
            // checking, but don't process what we just saw
            Toast.makeText(
                getApplicationContext(),
                "Connect to a Wi-Fi or mobile network, then scan again",
                Toast.LENGTH_SHORT).show();
            restartPreviewAfterDelay(5000);
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_control_panel) {
            // Start control panel activity
            final Intent intent = new Intent(this, PicoStatusActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;

        } else if (itemId == R.id.action_settings) {
            final Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private final BluetoothLoginListener mBluetoothLoginListener = new BluetoothLoginListener() {
        @Override
        public void onBluetoothLoginAvailable() {
            showQuickBluetoothLogin();
        }

        @Override
        public void onBluetoothLoginUnavailable() {
            hideQuickBluetoothLogin();
        }
    };

    /**
     * Show the quick Bluetooth login button at the bottom of the screen. Called by the
     * {@link #mBluetoothLoginListener} when a Pico device comes in range.
     */
    private void showQuickBluetoothLogin() {
        final AvailableBluetoothLogin[] logins =
            bluetoothServiceBinder.getAvailableBluetoothLogins();
        if (logins.length == 0 || logins[0] == null || logins[0].name == null) {
            hideQuickBluetoothLogin();
            return;
        }

        quickBluetoothLogin = logins[0];
        final String loginName = quickBluetoothLogin.name;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                quickBluetoothLoginName.setText(Html.fromHtml(
                    getString(R.string.quick_bluetooth_login, loginName)));
                quickBluetoothLoginUI.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Hide the quick Bluetooth login button. Called by the {@link #mBluetoothLoginListener} when
     * the Bluetooth device goes out of range.
     */
    private void hideQuickBluetoothLogin() {
        quickBluetoothLogin = null;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                quickBluetoothLoginUI.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Called when the user clicks the Bluetooth login option.
     *
     * @param view The view that was clicked.
     */
    public void onClickedQuickBluetoothLogin(View view) {
        if (quickBluetoothLogin != null) {
            startActivity(quickBluetoothLogin.intent);
            // hide the button now
            hideQuickBluetoothLogin();
        }
    }

}
