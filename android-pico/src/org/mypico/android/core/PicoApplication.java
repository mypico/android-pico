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

import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import org.mypico.android.data.SafeSession;
import org.mypico.android.crypto.PrngFixes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import org.mypico.android.bluetooth.PicoBluetoothService;
import org.mypico.jpico.comms.CombinedVerifierProxy;

/**
 * Initialisation of services, crypto, ... on starting the application.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class PicoApplication extends Application {
    private final static Logger LOGGER =
        LoggerFactory.getLogger(PicoApplication.class.getSimpleName());
    private static Context mContext;

    static {
        // Install Spongycastle as the first security provider
        LOGGER.debug("Installing Spongycastle security provider");
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

        // Apply Android PRNG fix (if applicable depending on API version).
        // Note: Greater than 4.2 is not thought to be affected as SecureRandom
        // was re-implemented.
        LOGGER.debug("Applying fixes to SecureRandom (if necessary)");
        PrngFixes.apply();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        LOGGER.debug("Application started (onCreate called)");
    }

    /**
     * Get the application context.
     *
     * @return The application context.
     */
    public static Context getContext() {
        return mContext;
    }
}
