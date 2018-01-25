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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.UUID;

import org.mypico.android.setup.SetupActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;

/**
 * Splash screen, displayed on first running the Pico application.
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class SplashScreenActivity extends Activity {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        SplashScreenActivity.class.getSimpleName());

    // Splash screen timer in ms
    private static final int SPLASH_TIME_OUT = 2000;
    // Key indicating first run of the Pico app
    private static final String PICO_UUID_KEY = "PICO_UUID";
    private static final String PICO_SHOWN_SPLASH_KEY = "PICO_SHOWN_SPLASH";

    private Handler handler;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String picoUuid = preferences.getString(PICO_UUID_KEY, "");
        handler = new Handler();

        if (isNullOrEmpty(picoUuid)) {
            // Pico is not configured
            LOGGER.info("Configuring Pico...");
            final boolean shownAlready = preferences.getBoolean(PICO_SHOWN_SPLASH_KEY, false);
            if (!shownAlready) {
                // the splash screen hasn't been displayed; show it for a bit, then move on
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // record that it has been shown now
                        preferences.edit()
                            .putBoolean(PICO_SHOWN_SPLASH_KEY, true)
                            .apply();
                        whenUnconfigured();
                    }
                }, SPLASH_TIME_OUT);
            } else {
                // if the splash screen has already been shown, move straight on
                whenUnconfigured();
            }

        } else {
            // Pico is already configured
            LOGGER.debug("Pico is already configured");
            whenConfigured();
        }
    }

    @Override
    protected void onActivityResult(
        final int requestCode, final int resultCode, final Intent result) {
        super.onActivityResult(requestCode, resultCode, result);

        if (requestCode == SetupActivity.SETUP_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                // The backup has been configured, therefore, start using Pico

                // Generate a UUID for the application at first run
                final UUID newPicoUuid = UUID.randomUUID();

                // Store the UUID indicating the Pico is configured in the SharedPreferences  
                final SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(this);
                final SharedPreferences.Editor editor = preferences.edit();
                editor.putString(PICO_UUID_KEY, newPicoUuid.toString());
                editor.commit();

                LOGGER.debug("Configuration complete");
                whenConfigured();
            } else if (resultCode == RESULT_CANCELED) {
                // Close this activity
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Called when Pico has not been configured and after the splash screen has been shown.
     * Launches the setup wizard.
     */
    private void whenUnconfigured() {
        // Launch the setup Activity
        LOGGER.info("Launching setup activity...");
        final Intent intent = new Intent(SplashScreenActivity.this, SetupActivity.class);
        startActivityForResult(intent, SetupActivity.SETUP_RESULT_CODE);
    }

    /**
     * Called when Pico starts up and has already been configured (i.e. startup situations
     * where the set up wizard will be skipped).
     */
    private void whenConfigured() {
        LOGGER.debug("Pico is configured, starting AcquireCodeActivity...");
        final Intent intent = new Intent(getIntent());
        intent.setClass(this, AcquireCodeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}