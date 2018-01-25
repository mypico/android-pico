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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafeService;
import org.mypico.jpico.crypto.messages.PicoReauthMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.crypto.AuthToken;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.common.base.Optional;

/**
 * Attempts to write an authentication token (cookie) to a terminal using a socket.
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
final public class SocketWriteAuthToken extends WriteAuthToken {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        SocketWriteAuthToken.class.getSimpleName());

    private final SocketAddress address;
    private final int timeout;

    /**
     * Constructor.
     *
     * @param context  The UI context.
     * @param fallback Intent to use in case writing the token over this channel fails.
     * @param address  The socket address to write to.
     * @param timeout  Timeout after which to stop the write.
     */
    public SocketWriteAuthToken(Context context, Optional<Intent> fallback, SocketAddress address, int timeout) {
        super(context, fallback);

        this.address = address;
        this.timeout = timeout;
    }

    @Override
    protected boolean write(AuthToken token) {
        LOGGER.debug("Writing auth token to socket: {}", address);

        // Try to connect to the terminal, signalling failure (return false)
        // after some timeout.
        final Socket socket = new Socket();
        try {
            socket.connect(address, timeout);
            try {
                // Once the socket is connected, try to write the full form of the auth
                // token to it as one line. Signalling failure if an IOException
                // occurs.
                final BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
                try {
                    writer.write(token.getFull());
                    writer.newLine();
                    writer.flush();
                } finally {
                    writer.close();
                }
            } finally {
                socket.close();
            }
        } catch (SocketTimeoutException e) {
            LOGGER.warn("Failed to write auth token {}", e);
            return false;
        } catch (IOException e) {
            LOGGER.warn("Failed to write auth token {}", e);
            return false;
        }

        // If all that succeeded without one of the return false's getting hit
        return true;
    }
}