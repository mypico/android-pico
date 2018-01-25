package org.mypico.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

/**
 * Thread that maintains a Bluetooth server. Create and {@link #start()} the thread to begin
 * accepting connections. The callback listener will be notified of each connection. Use
 * {@link #cancel()} to stop the server.
 * <p>
 * It is not checked whether Bluetooth is enabled or not. If Bluetooth is off nothing will happen.
 * <p>
 * Based on the example given in the Android developer docs:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html#ConnectingAsAServer
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class BluetoothServerThread extends Thread {
    private static final String TAG = "BluetoothServerThread";

    String serviceName;
    UUID serviceUUID;
    OnBluetoothConnectionAccepted listener;

    BluetoothServerSocket serverSocket;
    boolean oneTime = false;

    /**
     * Create a Bluetooth server with the given parameters. The server is not started until the
     * thread is started -- see {@link #start()}.
     *
     * @param serviceName Human-friendly name of the service the server is providing.
     * @param serviceUUID UUID of the service, by which clients will identify the service during
     *                    an SDP inquiry.
     * @param listener    Callback that will handle connections to the server.
     *                    See {@link OnBluetoothConnectionAccepted}.
     */
    public BluetoothServerThread(String serviceName, UUID serviceUUID,
                                 OnBluetoothConnectionAccepted listener) {
        this.serviceName = serviceName;
        this.serviceUUID = serviceUUID;
        this.listener = listener;

        // get the Bluetooth adapter and make sure it exists before using it!
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            serverSocket = null;
            return;
        }
        // we have Bluetooth: try to create the server socket
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                serviceName, serviceUUID);
        } catch (IOException e) {
            // something went wrong
            Log.e(TAG, "Couldn't create BluetoothServerSocket");
            e.printStackTrace();
            serverSocket = null;
        }

    }

    /**
     * Set whether this is a one-time server. For most practical applications you do not want this
     * (by default it is off).
     *
     * @param oneTime If true, the server will shut down after its first connection. If false, it
     *                will continue accepting connections until {@link #cancel()} is invoked.
     * @return This object, so you can chain methods.
     */
    public BluetoothServerThread setOneTime(boolean oneTime) {
        this.oneTime = oneTime;
        return this;
    }

    /**
     * Run the server. This is invoked by the thread when it is started. Don't call it directly!
     */
    @Override
    public void run() {
        BluetoothSocket socket;

        if (serverSocket == null)
            return;

        // server socket created successfully
        Log.d(TAG, "Bluetooth server started");
        while (true) {
            // wait for a connection
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                break;
            }
            // send to the callback listener if it's not null
            if (socket != null) {
                listener.onBluetoothConnectionAccepted(socket);
                // check if one-time is enabled, and stop if yes
                if (oneTime)
                    break;
            }
        }

        // finished for whatever reason, close the socket to be safe
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        serverSocket = null;
        Log.d(TAG, "Bluetooth server finished");
    }

    /**
     * Stop the server.
     */
    public void cancel() {
        if (serverSocket == null)
            return;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
