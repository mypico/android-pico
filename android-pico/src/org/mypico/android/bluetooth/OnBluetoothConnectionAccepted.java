package org.mypico.android.bluetooth;

import android.bluetooth.BluetoothSocket;

/**
 * Callback interface from BluetoothServerThread for when a device connects to the Bluetooth server.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public interface OnBluetoothConnectionAccepted {

    /**
     * Called from a BluetoothServerThread when a device connects to the Bluetooth server.
     *
     * @param socket The socket representing the connection to the client device.
     */
    void onBluetoothConnectionAccepted(BluetoothSocket socket);

}
