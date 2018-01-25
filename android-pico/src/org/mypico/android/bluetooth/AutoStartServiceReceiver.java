package org.mypico.android.bluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Registered in the AndroidManifest to receive BOOT_COMPLETED intents. This is used to start the
 * PicoBluetoothService in the background automatically when the device is switched on.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class AutoStartServiceReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent startServiceIntent = new Intent(context, PicoBluetoothService.class);
        context.startService(startServiceIntent);
    }

}
