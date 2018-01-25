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

import java.io.IOException;
import java.util.List;

import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.data.SafeSession;
import org.mypico.jpico.data.pairing.PairingNotFoundException;
import org.mypico.jpico.data.terminal.Terminal;

import android.net.Uri;

import com.google.common.base.Optional;

/**
 * <ode>PicoService</code> provides the interface for interacting with the service that manages
 * running sessions. When the user authenticates using their Pico, a continuous session may be
 * created that persists until the authenticated connection breaks, or the user chooses to stop it.
 * An implementation of this interface provides the appropriate methods for such a service.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @see PicoServiceImpl
 */
public interface PicoService {

    /**
     * Get all key pairings assocaited with a service.
     *
     * @param service The service to get the key pairings for.
     * @return The key pairings, returned as a list of {@link SafeKeyPairing} objects.
     * @throws IOException in case there is an error retrieving the data from the database.
     */
    public List<SafeKeyPairing> getKeyPairings(SafeService service)
        throws IOException;

    /**
     * Get all lens pairings assocaited with a service.
     *
     * @param service The service to get the lens pairings for.
     * @return The lens pairings, returned as a list of {@link SafeLensPairing} objects.
     * @throws IOException in case there is an error retrieving the data from the database.
     */
    public List<SafeLensPairing> getLensPairings(
        SafeService service) throws IOException;

    /**
     * Get all sessions assocaited with a key pairing.
     *
     * @param pairing The key pairing to get the sessions for.
     * @return The session associated with the key pairing, if one exists.
     * @throws IOException              in case there is an error retrieving the data from the database.
     * @throws PairingNotFoundException if no session associated with the key pairing exists.
     */
    public SafeSession keyAuthenticate(SafeKeyPairing pairing)
        throws IOException, PairingNotFoundException;

    /**
     * Get all sessions assocaited with a lens pairing.
     *
     * @param pairing The lens pairing to get the sessions for.
     * @return The session associated with the lens pairing, if one exists.
     * @throws IOException              in case there is an error retrieving the data from the database.
     * @throws PairingNotFoundException if no session associated with the lens pairing exists.
     */
    public SafeSession lensAuthenticate(SafeLensPairing pairing,
                                        Uri loginUri, String loginForm, String cookieString) throws IOException, PairingNotFoundException;

    /**
     * Rename a pairing.
     *
     * @param pairing The pairing to rename.
     * @param name    The new name to give to the pairing.
     * @return The pairing with the new name.
     * @throws IOException              in case there is an error retrieving the data from the database.
     * @throws PairingNotFoundException if no such pairing exists.
     */
    public SafePairing renamePairing(SafePairing pairing, String name)
        throws IOException, PairingNotFoundException;

    /**
     * Place the active session into a pause state.
     *
     * @param sessionInfo The active session to pause.
     */
    public void pauseSession(SafeSession sessionInfo);

    /**
     * Place the paused session into an active state.
     *
     * @param sessionInfo The paused session to resume.
     */
    public void resumeSession(SafeSession sessionInfo);

    /**
     * Place an active or paused session into a closed state.
     *
     * @param sessionInfo The session to close.
     */
    public void closeSession(SafeSession sessionInfo);

    /**
     * Retrieve a list of terminals.
     *
     * @return The list of terminals.
     * @throws IOException in case there is an error retrieving the data from the database.
     */
    public List<Terminal> getTerminals() throws IOException;

    /**
     * An interface used to pass back terminals found in the database. Once
     * {@link #getTerminals(GetTerminalsCallback)} is called, the results are passed back through
     * this interface.
     */
    public interface GetTerminalsCallback {
        /**
         * Callback called with a list of <code>Terminal</code> objects taken from the database.
         *
         * @param result The list of terminals found in the database.
         */
        public void onGetTerminalsResult(List<Terminal> result);

        /**
         * In case there is an error retrieving the data from the database, this callback will be
         * triggered with details of the exception triggered.
         *
         * @param e The exception.
         */
        public void onGetTerminalsError(IOException e);
    }

    /**
     * Asynchronously retrieve a list of all <code>Terminal</code> objects from the database. The
     * results are returned through the {@link GetTerminalsCallback} interface.
     *
     * @param callback The callback to use to return the data.
     */
    public void getTerminals(GetTerminalsCallback callback);

    /**
     * An interface used to pass back a terminal found in the database. Once
     * {@link #getTerminal(byte[], GetTerminalCallback)} is called, the result is passed back
     * through this interface.
     */
    public interface GetTerminalCallback {
        /**
         * Callback called with a <code>Terminal</code> object taken from the database, or null
         * if none was found.
         *
         * @param result The terminal found in the database, or null if none was found.
         */
        public void onGetTerminalResult(Optional<Terminal> result);
    }

    /**
     * Asynchronously retrieve a <code>Terminal</code> object from the database. The
     * results are returned through the {@link GetTerminalCallback} interface.
     *
     * @param terminalCommitment The commitment to search for.
     * @param callback           The callback to use to return the data.
     */
    void getTerminal(byte[] terminalCommitment, GetTerminalCallback callback);
}
