package org.mypico.android.bluetooth;

/**
 * Provides callbacks for when services with Bluetooth login become available or unavailable.
 * <p>
 * Register a listener by binding to the {@link PicoBluetoothService} and using
 * {@link org.mypico.android.bluetooth.PicoBluetoothService.PicoBluetoothServiceBinder#registerBluetoothLoginListener(BluetoothLoginListener)}.
 * Don't forget to unregister it later, otherwise you're likely to leak
 * {@link android.content.Context} references.
 * <p>
 * Note that the callbacks in this interface will be called on a thread other than the UI thread, so
 * any UI operations need to be wrapped in {@code runOnUiThread}.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public interface BluetoothLoginListener {

    /**
     * Called when a Pico-enabled Bluetooth device is first in range.
     */
    void onBluetoothLoginAvailable();

    /**
     * Called when no more Pico-enabled Bluetooth devices are in range.
     */
    void onBluetoothLoginUnavailable();

}
