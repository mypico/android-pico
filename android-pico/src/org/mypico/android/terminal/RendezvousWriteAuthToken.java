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

import java.io.IOException;
import java.net.URL;
import java.security.KeyPair;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafeService;
import org.mypico.jpico.crypto.messages.PicoReauthMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.comms.JsonMessageSerializer;
import org.mypico.jpico.comms.RendezvousSigmaProxy;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.crypto.NewSigmaProver;
import org.mypico.jpico.crypto.NewSigmaProver.ProverAuthRejectedException;
import org.mypico.jpico.crypto.NewSigmaProver.VerifierAuthFailedException;
import org.mypico.jpico.crypto.ProtocolViolationException;
import org.mypico.jpico.data.terminal.Terminal;
import org.mypico.rendezvous.RendezvousChannel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.common.base.Optional;

/**
 * Attempts to write an authentication token (cookie) to a terminal using a Rendezvous channel.
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
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class RendezvousWriteAuthToken extends WriteAuthToken {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(RendezvousWriteAuthToken.class.getSimpleName());
    private static final Optional<Intent> NO_FALLBACK = Optional.absent();

    private final RendezvousChannel channel;
    private final Terminal terminal;

    /**
     * Constructor.
     *
     * @param context  The UI context.
     * @param fallback Intent to use in case writing the token over this channel fails.
     * @param url      The URL of the Rendezvous Point to write the token to.
     * @param terminal The terminal the final write is directed at.
     */
    public RendezvousWriteAuthToken(
        Context context, Optional<Intent> fallback, URL url, Terminal terminal) {
        super(context, fallback);
        channel = new RendezvousChannel(url);
        this.terminal = terminal;
    }

    /**
     * Constructor.
     *
     * @param context  The UI context.
     * @param url      The URL of the Rendezvous Point to write the token to.
     * @param terminal The terminal the final write is directed at.
     */
    public RendezvousWriteAuthToken(Context context, URL url, Terminal terminal) {
        this(context, NO_FALLBACK, url, terminal);
    }

    @Override
    protected boolean write(AuthToken token) {
        LOGGER.debug("Sending auth token to {}", channel.getUrl().toString());

        // Make a proxy for the terminal verifier
        final RendezvousSigmaProxy proxy = new RendezvousSigmaProxy(
            channel, new JsonMessageSerializer());

        final NewSigmaProver prover;
        try {
            prover = new NewSigmaProver(
                NewSigmaProver.VERSION_1_1,
                new KeyPair(terminal.getPicoPublicKey(), terminal.getPicoPrivateKey()),
                token.toByteArray(),
                proxy,
                terminal.getCommitment(),
                null);
        } catch (IOException e) {
            LOGGER.warn("could not serialize auth token", e);
            return false;
        }

        try {
            prover.prove();
        } catch (IOException e) {
            LOGGER.warn("unable to authenticate to terminal", e);
            return false;
        } catch (ProverAuthRejectedException e) {
            LOGGER.warn("terminal rejected authentication", e);
            return false;
        } catch (ProtocolViolationException e) {
            LOGGER.warn("terminal violated the authentication protocol", e);
            return false;
        } catch (VerifierAuthFailedException e) {
            LOGGER.warn("terminal failed to authenticate", e);
            return false;
        }

        // Otherwise, if no exceptions were thrown, the prover authenticated and transferred the
        // token as its extra data successfully!
        return true;
    }
}
