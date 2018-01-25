package org.mypico.android.core;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import org.mypico.android.R;
import org.mypico.android.backup.ManageBackupActivity;
import org.mypico.android.bluetooth.PicoBluetoothService;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class SettingsActivity extends PreferenceActivity {
    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                    index >= 0
                        ? listPreference.getEntries()[index]
                        : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }

            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
            PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getString(preference.getKey(), ""));
    }

    /**
     * Add a side effect to the given preference when its value changes, preserving any previous
     * listeners.
     */
    private static void injectOnPreferenceChangedSideEffect(Preference preference,
                                                            final Runnable runnable) {
        final Preference.OnPreferenceChangeListener oldListener =
            preference.getOnPreferenceChangeListener();
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                runnable.run();
                return (oldListener == null) ||
                    oldListener.onPreferenceChange(preference, value);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, new GeneralPreferenceFragment())
            .commit();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
            || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.

            Preference bluetoothModePreference = findPreference("bluetooth_mode_list");
            bindPreferenceSummaryToValue(bluetoothModePreference);
            // when the Bluetooth mode is changed, also notify the Bluetooth service
            injectOnPreferenceChangedSideEffect(bluetoothModePreference, new Runnable() {
                @Override
                public void run() {
                    final Context context = getActivity();
                    final Intent intent = new Intent(context, PicoBluetoothService.class);
                    intent.setAction(PicoBluetoothService.ACTION_BLUETOOTH_MODE_CHANGED);
                    context.startService(intent);
                }
            });

            // Make certain preferences trigger actions when clicked
            makePreferenceStartIntent(findPreference("backup_config"),
                new Intent(getActivity(), ManageBackupActivity.class));

            makePreferenceStartIntent(findPreference("pico_website"),
                new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.pico_project_url_full))));

            // set the version number
            setVersionNumber(findPreference("pico_version"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                getActivity().onBackPressed();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        /**
         * Attach a click listener to the given {@link Preference} item that will start the given
         * {@link Intent}.
         *
         * @param preference The {@link Preference} to make clickable.
         * @param intent     The {@link Intent} that will be started when the preference is clicked.
         */
        void makePreferenceStartIntent(Preference preference, final Intent intent) {
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(intent);
                    return true;
                }
            });
        }

        /**
         * Get the app's version number from its manifest (android:versionName) and set it as the
         * summary text for the given preference.
         *
         * @param preference The version preference whose summary text will be set.
         */
        void setVersionNumber(Preference preference) {
            final Context context = getActivity();
            try {
                // get the app's package information
                final PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
                // obtain the version and display it as the preference's summary text
                final String version = info.versionName;
                preference.setSummary(version);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
    }
}
