package org.mypico.android.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;

/**
 * Class used to get Bluetooth-related information.
 * Created on 09/09/16.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class BluetoothInfo {

    /**
     * Get the device's local Bluetooth address.
     * On Marshmallow and above, the usual BluetoothAdapter#getAddress() method returns a dummy
     * address. See https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id.
     * There is a workaround used below that is confirmed working on Marshmallow (API level 23)
     * and Nougat (API level 24).
     *
     * @param context App context
     * @return The device's local Bluetooth address in hexadecimal format (xx:xx:xx:xx:xx:xx), or
     * null if Bluetooth is unavailable
     */
    public static String getLocalAddress(Context context) {
        // see if we have to use the workaround
        if (Build.VERSION.SDK_INT < 23) {
            // get the device's Bluetooth adapter -- note this may be null if the device does not
            // support Bluetooth
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            return bluetoothAdapter == null ? null : bluetoothAdapter.getAddress();

        } else {
            // workaround for M and above
            return android.provider.Settings.Secure
                .getString(context.getContentResolver(), "bluetooth_address");
        }
    }

}
