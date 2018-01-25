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


package org.mypico.android.terminal;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafeService;
import org.mypico.jpico.crypto.messages.PicoReauthMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.crypto.AuthToken;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import com.google.common.base.Optional;

/**
 * Attempts to write an authentication token (cookie) to a terminal. This is an abstract class, and
 * an implementation would be expected to define a channel to usee for the send. For example see
 * {@link HttpWriteAuthToken}, {@link RendezvousWriteAuthToken} and {@see SocketWriteAuthToken}.
 * <p>
 * In practice this class isn't used and the HTTP channel is likely to be insecure. The current
 * implementation sends the {@see AuthToken} to the terminal in a {@link PicoReauthMessage} message,
 * as can be seen in {@link org.mypico.android.pairing.AuthenticateIntentService#authenticatePairing(SafeLensPairing, SafeService, String, String, Uri, byte[])}.
 * <p>
 * Given this it's not clear what this class is really intended to be used for.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
abstract class WriteAuthToken extends AsyncTask<AuthToken, Void, Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        SocketWriteAuthToken.class.getSimpleName());

    protected Context context;
    private Optional<Intent> fallback;
    protected AuthToken token;

    /**
     * Constructor.
     *
     * @param context  The UI context.
     * @param fallback Intent to use in case writing the token over this channel fails.
     */
    public WriteAuthToken(Context context, Optional<Intent> fallback) {
        this.context = context;
        this.fallback = fallback;
    }

    protected abstract boolean write(AuthToken token);

    @Override
    protected Boolean doInBackground(AuthToken... params) {
        token = params[0];
        return write(token);
    }

    @Override
    public void onPostExecute(final Boolean wasSuccessful) {
        if (wasSuccessful) {
            LOGGER.debug("Auth token written successfully");
        } else {
            LOGGER.debug("Auth token was not written");
            if (fallback.isPresent()) {
                LOGGER.debug("Starting fallback...");
                context.startActivity(fallback.get());
            } else {
                LOGGER.debug("No fallback specified");
            }
        }
    }
}
