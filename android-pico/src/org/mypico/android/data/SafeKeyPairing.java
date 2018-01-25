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


package org.mypico.android.data;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyPair;
import java.util.Date;

import org.mypico.android.R;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.data.DataAccessor;
import org.mypico.jpico.data.DataFactory;
import org.mypico.jpico.data.pairing.KeyPairing;
import org.mypico.jpico.data.pairing.KeyPairingAccessor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * {@link KeyPairing}s are used on the Pico to store pairings with terminals, devices and services
 * that can work using only public/private key pairs (i.e. not a
 * {@link org.mypico.jpico.data.pairing.LensPairing}). These {@link KeyPairing}s store data about
 * the pairing, some of which must be kept private, such as the private keys used to identify the
 * Pico to the service.
 * <p>
 * The <code>SafeKeyPairing</code> is a version of the {@link KeyPairing} that stores only public
 * data, and is therefore safe to use on the UI thread.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class SafeKeyPairing extends SafePairing {

    /*
     * Need to override CREATOR otherwise a ClassCastException is thrown when we try to get back a
     * parcelled SafeKeyPairing.
     */
    public static final Parcelable.Creator<SafeKeyPairing> CREATOR =
        new Parcelable.Creator<SafeKeyPairing>() {

            @Override
            public SafeKeyPairing createFromParcel(Parcel source) {
                // Just reuse the superclass CREATOR:
                SafePairing p =
                    SafePairing.CREATOR.createFromParcel(source);
                return new SafeKeyPairing(
                    p.pairingId,
                    p.idIsKnown(),
                    p.getName(),
                    p.getSafeService(),
                    p.getDateCreated().orNull());
            }

            @Override
            public SafeKeyPairing[] newArray(int size) {
                return new SafeKeyPairing[size];
            }
        };

    /**
     * Create a new <code>SafeKeyPairing</code> from the given data.
     *
     * @param id          The id of the key pairing. This is needed to associated the
     *                    <code>SafeKeyPairing</code> with the full-fat {@see KeyPairing} stored in the Pico
     *                    database.
     * @param idIsKnown   true if the id is known, false o/w. Note that if this is set to false, it
     *                    the won't be possible to extract the associated {@see KeyPairing} from the
     *                    Pico database.
     * @param name        The name of the key pairing.
     * @param service     The service to associate the key pairing with.
     * @param dateCreated The date the key pairing was created.
     */
    private SafeKeyPairing(
        final int id,
        final boolean idIsKnown,
        final String name,
        final SafeService service,
        final Date dateCreated) {
        super(id, idIsKnown, name, service, dateCreated);
    }

    /**
     * Create a new <code>SafeKeyPairing</code> from the given data.
     *
     * @param name    The name of the key pairing.
     * @param service The service to associate the key pairing with.
     */
    public SafeKeyPairing(final String name, final SafeService service) {
        super(name, service);
    }

    /**
     * Create a safe key pairing from a full-fat key pairing.
     *
     * @param keyPairing The full fat key pairing to make a safe pairing from.
     */
    public SafeKeyPairing(KeyPairing keyPairing) {
        super(keyPairing);
    }

    public KeyPairing createKeyPairing(DataFactory factory, DataAccessor accessor, KeyPair keyPair, String extraData)
        throws IOException {
        return new KeyPairing(
            factory,
            getName(),
            getSafeService().getOrCreateService(factory, accessor),
            keyPair,
            extraData);
    }

    /**
     * Get the {@link KeyPairing} associated with this <code>SafeKeyPairing</code>. The key pairing
     * will be retrieved from the database.
     *
     * @param accessor The database accessor to use.
     * @return the key pairing associated with the safe key pairing, or null if not assocaited.
     * @throws IOException if an error occurs accessing the database.
     */
    public KeyPairing getKeyPairing(KeyPairingAccessor accessor) throws IOException {
        if (idIsKnown()) {
            return accessor.getKeyPairingById(pairingId);
        } else {
            return null;
        }
    }

    /**
     * Get the {@link KeyPairing} associated with this <code>SafeKeyPairing</code>. The key pairing
     * will be retrieved from the database. If it does not already exist a new one will be created.
     *
     * @param accessor  The database accessor to use.
     * @param factory   The database factory to use.
     * @param accessor  The database accessor to use.
     * @param keyPair   The key pair to get.
     * @param extraData The extra data to associated with the key pairing if it doesn't exist.
     * @return the key pairing associated with the safe key pairing, or a new key pairing if none
     * was associated with it.
     * @throws IOException if an error occurs accessing the database.
     */
    public KeyPairing getOrCreateKeyPairing(DataFactory factory, DataAccessor accessor, KeyPair keyPair, String extraData)
        throws IOException {
        KeyPairing existing = getKeyPairing(accessor);
        if (existing != null) {
            return existing;
        } else {
            return createKeyPairing(factory, accessor, keyPair, extraData);
        }
    }

    @Override
    public AlertDialog getDelegationFailedDialog(Activity context, AuthToken token) {
        // TODO remove use of exceptions for control flow
        String message;
        try {
            URL fallbackUrl = new URL(token.getFallback());

            // if MalformedURLException not thrown then fallback is a valid URL
            // Prepare a message which includes the fallback of the auth token
            message = context.getString(R.string.delegation_failed_transcribe) + ": " + fallbackUrl.toString();
        } catch (MalformedURLException e) {
            message = context.getString(R.string.delegation_failed);
        }

        // Build dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }
}
