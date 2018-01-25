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


// Copyright University of Cambridge, 2013

package org.mypico.android.data;

import java.io.IOException;
import java.util.Date;

import org.mypico.android.R;
import org.mypico.android.pairing.AuthFailedDialog.AuthFailedSource;
import org.mypico.android.pairing.AuthenticateActivity;
import org.mypico.android.pairing.DelegationFailedDialog.DelegationFailedSource;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.data.DataAccessor;
import org.mypico.jpico.data.DataFactory;
import org.mypico.jpico.data.pairing.Pairing;
import org.mypico.jpico.data.pairing.PairingAccessor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Optional;

/**
 * Representation of a Pairing instance for display on the UI.
 * <p>
 * The SafePairing class is a representation of a Pairing, that contains only the information
 * required by the UI. Sensitive data such as the Pairing's private key is not passed to the UI
 * thread.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Chris Warrington <cw471@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see Pairing
 */
public class SafePairing implements Parcelable, AuthFailedSource, DelegationFailedSource {

    private static final int UNKNOWN_ID = -1;
    private static final long INVALID_DATE = -1;

    public static enum PairingType {
        KEY,
        CREDENTIAL
    }

    /**
     * Generates a SafePairing instance from a Parcelable class, whose data had previously been
     * written by Parcelable.writeToParcel().
     *
     * @see android.os.Parcelable.Creator
     */
    public static final Parcelable.Creator<SafePairing> CREATOR =
        new Parcelable.Creator<SafePairing>() {

            /**
             * Create a new instance of the Parcelable class. A SafePairing is instantiated
             * given a Parcel whose data had previously been written by
             * Parcelable.writeToParcel().
             *
             * @param Parcel a Parcel whose data had previously been written by
             *        Parcelable.writeToParcel()
             * @return a unmarshaled SafePairing instance
             */
            @Override
            public SafePairing createFromParcel(Parcel source) {
                // Read boolean array containing idIsKnown flag
                boolean[] b = new boolean[1];
                source.readBooleanArray(b);

                // Check whether the Pairing dateCreated file is set
                final long dateCreatedField = source.readLong();
                final Date dateCreated;
                if (dateCreatedField == INVALID_DATE) {
                    dateCreated = null;
                } else {
                    dateCreated = new Date(dateCreatedField);
                }

                return new SafePairing(
                    source.readInt(), // id
                    b[0], // idIsKnown
                    source.readString(), // name
                    (SafeService) source.readParcelable(
                        SafeService.class.getClassLoader()), // service
                    dateCreated);
            }

            /**
             * Create a new array of the Parcelable class.
             *
             * @param size the size of the array to create.
             * @return the array
             */
            @Override
            public SafePairing[] newArray(int size) {
                return new SafePairing[size];
            }
        };

    protected final int pairingId;
    private final boolean idIsKnown;
    private final String name;
    private final SafeService serviceInfo;
    private final Date dateCreated;

    /**
     * Constructor This Constructor is used to marshal and unmarshal a SafePairing instance.
     *
     * @param id        the Pairing instance's id.
     * @param idIsKnown true if the Pairing is empty, false otherwise
     * @param name      the Pairing instance's name.
     * @param service   the Pairing instances service.
     */
    protected SafePairing(
        final int id,
        final boolean idIsKnown,
        final String name,
        final SafeService service,
        final Date dateCreated) {
        if (service == null)
            throw new NullPointerException();

        this.pairingId = id;
        this.name = name;
        this.serviceInfo = service;
        this.dateCreated = dateCreated;
        this.idIsKnown = idIsKnown;
    }


    /**
     * Constructor to create a <code>SafePairing</code> from a {@see SafeService}.
     *
     * @param name    The name of the pairing.
     * @param service The service to create the <code>SafePairing</code> from.
     */
    protected SafePairing(final String name, final SafeService service) {
        this(UNKNOWN_ID, false, name, service, null);
    }

    /**
     * Construct a SafePairing instance from a full Pairing.
     *
     * @param pairing a full Pairing instance.
     */
    public SafePairing(final Pairing pairing) {
        if (pairing == null) {
            throw new NullPointerException(
                "SafePairing cannot be created from a null Pairing");
        } else {
            pairingId = pairing.getId();
            name = pairing.getName();
            serviceInfo = new SafeService(pairing.getService());
            dateCreated = pairing.getDateCreated();
            // TODO: Check this with Max
            idIsKnown = true;
        }
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Retrieve whether the pairing id has been set and is known.
     *
     * @return true if the SafePairing is empty, false otherwise
     */
    public boolean idIsKnown() {
        return idIsKnown;
    }

    /**
     * Get the SafePairing instance's name.
     *
     * @return the SafePairing instance's name.
     */
    public String getName() {

        return name;
    }

    /**
     * Get a string to represent this <code>SafePairing</code> in the UI.
     *
     * @return The string to display the pairing to the user as.
     */
    public String getDisplayName() {
        return new StringBuilder()
            .append(this.serviceInfo.getName())
            .append(": ")
            .append(name)
            .toString();
    }

    /**
     * Get the service assocated with this <code>SafePairing</code>.
     *
     * @return The service.
     */
    public SafeService getSafeService() {
        return serviceInfo;
    }

    /**
     * Get the date the <code>SafePairing</code> was created.
     *
     * @return The creation date.
     */
    public Optional<Date> getDateCreated() {
        return Optional.fromNullable(dateCreated);
    }

    /**
     * Factoring for creating a new {@see Pairing}.
     *
     * @param factory  The database factory to use.
     * @param accessor The database accessor to use.
     * @return the {@see Pairing} created.
     * @throws IOException in case an error occurs accessing the database.
     */
    public Pairing createPairing(DataFactory factory, DataAccessor accessor)
        throws IOException {
        return new Pairing(
            factory,
            name,
            serviceInfo.getOrCreateService(factory, accessor));
    }

    /**
     * Get the full pairing assocaited with this <code>SafePairing</code> from the Pico database.
     *
     * @param accessor The database accessor.
     * @return The {@see Pairing} associated with this <code>SafePairing</code>, if the id is known,
     * or null o/w.
     * @throws IOException in case an error occurs accessing the database.
     */
    public Pairing getPairing(PairingAccessor accessor)
        throws IOException {
        if (idIsKnown) {
            return accessor.getPairingById(pairingId);
        } else {
            return null;
        }
    }

    /**
     * Get the {@link Pairing} associated with this <code>SafePairing</code>. The pairing
     * will be retrieved from the database.
     *
     * @param accessor The database accessor to use.
     * @param factory  The database factory to use.
     * @param accessor The database accessor to use.
     * @return the pairing associated with the safe pairing, or a new pairing if none
     * was associated with it.
     * @throws IOException if an error occurs accessing the database.
     */
    public Pairing getOrCreatePairing(
        DataFactory factory, DataAccessor accessor) throws IOException {
        Pairing existing = getPairing(accessor);
        if (existing != null) {
            return existing;
        } else {
            return createPairing(factory, accessor);
        }
    }

    /*
     * public Pairing getFullPairing(PairingDao dao) throws SQLException { Pairing pairing = null;
     * 
     * if (isEmpty) { // Case where this is a newly created, "empty", SafePairing with // no
     * existing Pairing stored in the database.
     * 
     * // Get a full Service instance for the pairing // TODO add a similar getFull... method to
     * ServiceInfo org.mypico.jpico.service.Service service = new
     * org.mypico.jpico.service.Service( getServiceInfo().getPublicKey(),
     * getServiceInfo().getName(), getServiceInfo().getUri().toString() );
     * 
     * // Load config values Config config = Config.getInstance(); String provider; String
     * kpgAlgorithm; if ((provider = (String) config.get("crypto.provider")) == null) { throw new
     * IllegalArgumentException("crypto.provider config value is null"); } if ((kpgAlgorithm =
     * (String) config.get("crypto.kpg_algorithm")) == null) { throw new
     * IllegalArgumentException("crypto.kpg_algorithm config value is null"); }
     * 
     * 
     * // Create a key pair for the new pairing KeyPair keyPair = null; try { KeyPairGenerator kpg =
     * KeyPairGenerator.getInstance(kpgAlgorithm, provider); kpg.initialize(256); keyPair =
     * kpg.generateKeyPair(); } catch (NoSuchAlgorithmException e) { throw new
     * CryptoRuntimeException(e); } catch (NoSuchProviderException e) { throw new
     * CryptoRuntimeException(e); }
     * 
     * pairing = new Pairing(service, name, keyPair); LOGGER.debug("Created new Pairing instance");
     * } else { // Case where there is a Pairing corresponding to the supplied // SafePairing
     * already stored in the database.
     * 
     * // Retrieve that Pairing instance: pairing = dao.getPairingById(id);
     * LOGGER.debug("Retrieved existing Pairing instance from database", pairing); }
     * 
     * return pairing; }
     */

    /**
     * Describe the kinds of special objects contained in this Parcelable's marshalled
     * representation.
     *
     * @return a bitmask indicating the set of special object types marshalled by the Parcelable (0
     * in this case).
     */
    @Override
    public int describeContents() {

        return 0;
    }

    /**
     * Marshal this object into a Parcel.
     *
     * @param flags Additional flags about how the object should be written. May be 0 or
     *              PARCELABLE_WRITE_RETURN_VALUE.
     * @param out   The resulting parcel
     */
    @Override
    public void writeToParcel(Parcel out, int flags) {

        // Verify the method's preconditions
        if (out == null)
            throw new NullPointerException();

        out.writeBooleanArray(new boolean[]{idIsKnown});
        // Check whether the dateCreated attribute is set yet.
        // This will be null when a new SafePairing is created and a
        // corresponding Pairing has not yet been written to the database
        if (dateCreated == null) {
            out.writeLong(INVALID_DATE);
        } else {
            out.writeLong(dateCreated.getTime());
        }
        out.writeInt(pairingId);
        out.writeString(name);
        out.writeParcelable(serviceInfo, flags);
    }

    /**
     * Optionally return an intent to start a detail activity for this pairing.
     * <p>
     * <p>The default implementation of this method returns an absent optional. Subclasses should
     * override it if appropriate.
     *
     * @param context to use when creating intent
     * @return optionally an intent to start a detail activity
     */
    public Optional<Intent> detailIntent(final Context context) {
        return Optional.absent();
    }

    /**
     * Start a detail activity for this pairing. Convenience method which starts the intent
     * returned by {@link #detailIntent(Context)} if present, or does nothing otherwise.
     *
     * @param context context to use when starting the activity
     */
    public void startDetail(Context context) {
        Optional<Intent> i = detailIntent(context);
        if (i.isPresent()) {
            context.startActivity(i.get());
        }
    }

    @Override
    public AlertDialog getAuthFailedDialog(Activity context, String userMsg) {
        // Form string for dialog
        final boolean isPairing = context.getIntent()
            .getBooleanExtra(AuthenticateActivity.IS_PAIRING_EXTRA, false);
        final String name = serviceInfo.getName();
        final String message;

        if (isPairing) {
            message = (userMsg == null) ?
                context.getString(R.string.pairing_failed_fmt, name) :
                context.getString(R.string.pairing_failed_fmt2, name, userMsg);
        } else {
            message = (userMsg == null) ?
                context.getString(R.string.auth_failed_fmt, name) :
                context.getString(R.string.auth_failed_fmt2, name, userMsg);
        }

        // Build alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    @Override
    public AlertDialog getDelegationFailedDialog(Activity context, AuthToken token) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.delegation_failed);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }
}
