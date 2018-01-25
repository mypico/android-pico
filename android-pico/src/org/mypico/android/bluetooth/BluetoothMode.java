package org.mypico.android.bluetooth;

import android.content.Context;
import android.preference.PreferenceManager;

import org.mypico.android.R;

/**
 * An enum-like class that stores options for the Bluetooth mode preference. This is nicer than
 * typing the same string constant repeatedly and reduces chance of error.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class BluetoothMode {

    /* KEEP THIS CONSTANT IN SYNC WITH THE KEY IN xml/pref_general.xml */
    /// The Bluetooth mode preference key
    public static final String PREFERENCE_KEY = "bluetooth_mode_list";

    /* KEEP THESE CONSTANTS IN SYNC WITH THOSE IN values/activity_settings_strings.xml */
    /// One shot mode: a one-off login that the user manually chooses
    public static final String MANUAL_MODE = "one_shot";
    /// Notification mode: display a notification whenever a login is available
    public static final String NOTIFICATION_MODE = "notification";
    /// Always on mode: always log in to whatever requests it
    public static final String AUTOMATIC_MODE = "always_on";

    /**
     * Get the current Bluetooth mode from the app's preferences.
     *
     * @param context The context to get preferences from.
     * @return The current Bluetooth mode preference, defaulting to {@link #DEFAULT_MODE} if no
     * preference is currently set.
     */
    public static String getCurrentMode(Context context) {
        /// the default mode if the preference hasn't been set
        final String DEFAULT_MODE = context.getResources().getString(R.string.pref_bluetooth_mode_default_value);

        String preferenceMode = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREFERENCE_KEY, DEFAULT_MODE);

        if (preferenceMode == null) {
            preferenceMode = DEFAULT_MODE;
        }

        return preferenceMode;
    }

}
