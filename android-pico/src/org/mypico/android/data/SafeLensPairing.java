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
import java.util.Date;
import java.util.Map;
import java.util.List;

import org.mypico.android.pairing.LensPairingDetailActivity;
import org.mypico.android.R;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.data.DataAccessor;
import org.mypico.jpico.data.DataFactory;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.LensPairingAccessor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Optional;

/**
 * {@link LensPairing}s are used on the Pico to store pairings with websites that require a pasword
 * and return a cookie that can then be transfered to a terminal (i.e. not a
 * {@link org.mypico.jpico.data.pairing.KeyPairing}). These {@link LensPairing}s store data about
 * the pairing, some of which must be kept private, such as the private keys used to identify the
 * Pico to the terminal.
 * <p>
 * The <code>SafeLensPairing</code> is a version of the {@link LensPairing} that stores only public
 * data, and is therefore safe to use on the UI thread.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class SafeLensPairing extends SafePairing {

    /*
     * Need to override CREATOR otherwise a ClassCastException is thrown when we try to get back a
     * parcelled SafeLensPairing.
     */
    public static final Parcelable.Creator<SafeLensPairing> CREATOR =
        new Parcelable.Creator<SafeLensPairing>() {

            @Override
            public SafeLensPairing createFromParcel(Parcel source) {
                // Just reuse the superclass CREATOR:
                SafePairing p =
                    SafePairing.CREATOR.createFromParcel(source);
                return new SafeLensPairing(
                    p.pairingId,
                    p.idIsKnown(),
                    p.getName(),
                    p.getSafeService(),
                    p.getDateCreated().orNull());
            }

            @Override
            public SafeLensPairing[] newArray(int size) {
                return new SafeLensPairing[size];
            }
        };

    /**
     * Constructor to create a <code>SafeLensPairing</code> using the provided data.
     *
     * @param id          The id of the lens pairing. This is needed to associated the
     *                    <code>SafeLensPairing</code> with the full-fat {@see LensPairing} stored in the
     *                    Pico database.
     * @param idIsKnown   true if the id is known, false o/w. Note that if this is set to false, it
     *                    the won't be possible to extract the associated {@see KeyPairing} from the
     *                    Pico database.
     * @param name        The name of the lens pairing.
     * @param service     The name of the service.
     * @param dateCreated The dat the pairing was created.
     */
    private SafeLensPairing(
        final int id,
        final boolean idIsKnown,
        final String name,
        final SafeService service,
        final Date dateCreated) {
        super(id, idIsKnown, name, service, dateCreated);
    }

    /**
     * Constructor to create a <code>SafeLensPairing</code> from a {@see LensPairing}.
     *
     * @param credentialPairing The {@see LensPairing} to create the <code>SafeLensPairing</code>
     *                          from.
     */
    public SafeLensPairing(final LensPairing credentialPairing) {
        super(credentialPairing);
    }

    /**
     * Constructor to create a <code>SafeLensPairing</code> from a {@see SafeService}.
     *
     * @param name    The name of the lens pairing.
     * @param service The service to create the <code>SafeLensPairing</code> from.
     */
    public SafeLensPairing(
        final String name,
        final SafeService service) {
        super(name, service);
    }

    /**
     * Factoring for creating a new {@see LensPairing}.
     *
     * @param factory       The database factory to use.
     * @param accessor      The database accessor to use.
     * @param credentials   The credentials to store with the {@see LensPairing}.
     * @param privateFields The private fields to store with the {@see LensPairing}.
     * @return the {@see LensPairing} created.
     * @throws IOException in case an error occurs accessing the database.
     */
    public LensPairing createLensPairing(
        final DataFactory factory,
        final DataAccessor accessor,
        final Map<String, String> credentials,
        final List<String> privateFields) throws IOException {

        return new LensPairing(
            factory,
            getName(),
            getSafeService().getOrCreateService(factory, accessor),
            credentials,
            privateFields);
    }

    /**
     * Retrieve the {@see LensPairing} associated with thsi <code>SafeLensPairing</code> from the
     * Pico database.
     *
     * @param accessor The database accessor.
     * @return The associated {@see LensPairing} or null if there is none.
     * @throws IOException in case an error occurs accessing the database.
     */
    public LensPairing getLensPairing(
        final LensPairingAccessor accessor) throws IOException {
        if (idIsKnown()) {
            return accessor.getLensPairingById(pairingId);
        } else {
            return null;
        }
    }

    @Override
    public Optional<Intent> detailIntent(final Context context) {
        final Intent intent = new Intent(context, LensPairingDetailActivity.class);
        intent.putExtra(LensPairingDetailActivity.PAIRING, this);
        return Optional.of(intent);
    }

    // Helper for the public getXFailedDialog methods
    private AlertDialog fallbackDialog(final Activity context, CharSequence message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setPositiveButton(
            R.string.show_saved_credentials, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = detailIntent(context).get();
                    i.putExtra(LensPairingDetailActivity.SHOW_ON_RESUME, true);
                    context.startActivity(i);
                }
            });
        builder.setNegativeButton(R.string.cancel, null);
        return builder.create();
    }

    @Override
    public AlertDialog getAuthFailedDialog(final Activity context, String userMsg) {
        // Format message for dialog, then use helper method.
        final String name = getSafeService().getName();
        final String message = (userMsg == null) ?
            context.getString(R.string.auth_failed_fmt, name) :
            context.getString(R.string.auth_failed_fmt2, name, userMsg);
        return fallbackDialog(context, message);
    }

    @Override
    public AlertDialog getDelegationFailedDialog(Activity context, AuthToken token) {
        // Use helper method
        return fallbackDialog(context, context.getString(R.string.delegation_failed));
    }
}
