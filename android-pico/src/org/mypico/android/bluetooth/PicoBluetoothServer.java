package org.mypico.android.bluetooth;

import java.util.UUID;

/**
 * Subclass of BluetoothServerThread that uses the Pico service name and UUID.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class PicoBluetoothServer extends BluetoothServerThread {

    public static final String SERVICE_NAME = "Pico";
    public static final UUID SERVICE_UUID = UUID.fromString("ed995e5a-c7e7-4442-a6ee-7bb76df43b0d");

    public PicoBluetoothServer(OnBluetoothConnectionAccepted listener) {
        super(SERVICE_NAME, SERVICE_UUID, listener);
    }

}
