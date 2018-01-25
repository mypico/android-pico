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
import java.net.URI;
import java.net.URISyntaxException;

import static com.google.common.base.Preconditions.checkNotNull;

import org.mypico.android.R;
import org.mypico.android.pairing.AuthFailedDialog.AuthFailedSource;
import org.mypico.android.pairing.AuthenticateActivity;
import org.mypico.jpico.data.service.Service;
import org.mypico.jpico.data.service.ServiceAccessor;
import org.mypico.jpico.data.service.ServiceImpFactory;
import org.mypico.jpico.visualcode.LensPairingVisualCode;
import org.mypico.jpico.visualcode.KeyAuthenticationVisualCode;
import org.mypico.jpico.visualcode.KeyPairingVisualCode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Representation of a Service instance for display on the UI.
 * <p>
 * The Service class is a representation of a Service, that contains only the information required
 * by the UI.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see Service
 */
final public class SafeService implements Parcelable, AuthFailedSource {

    private static final int UNKNOWN_ID = -1;

    /**
     * Generates a SafeSession instance from a Parcelable class, whose data had previously been
     * written by Parcelable.writeToParcel().
     *
     * @see android.os.Parcelable.Creator
     */
    public static final Parcelable.Creator<SafeService> CREATOR =
        new Parcelable.Creator<SafeService>() {

            /**
             * Create a new instance of the Parcelable class. A SafeSession is instantiated
             * given a Parcel whose data had previously been written by
             * Parcelable.writeToParcel().
             *
             * @param Parcel a Parcel whose data had previously been written by
             *        Parcelable.writeToParcel()
             * @return a unmarshaled SafeSession instance
             */
            @Override
            public SafeService createFromParcel(Parcel source) {
                // Read commitment, which is prepended with its length in bytes
                byte[] commitment = new byte[source.readInt()];
                source.readByteArray(commitment);

                // Read boolean array containing idIsKnown flag
                boolean[] b = new boolean[1];
                source.readBooleanArray(b);

                return new SafeService(
                    source.readInt(), // serviceId
                    b[0], // idIsKnown
                    source.readString(), // name
                    (Uri) source.readParcelable(null), // address
                    commitment, // commitment
                    (Uri) source.readParcelable(null) // logoUri
                );
            }

            /**
             * Create a new array of the Parcelable class.
             *
             * @param size the size of the array to create.
             * @return the array
             */
            @Override
            public SafeService[] newArray(int size) {
                return new SafeService[size];
            }
        };

    private final int serviceId;
    private final boolean idIsKnown;
    private final String name;
    private final Uri address;
    private final byte[] commitment;
    private final Uri logoUri;

    private SafeService(
        final int serviceId,
        final boolean idIsKnown,
        final String name,
        final Uri address,
        final byte[] commitment,
        final Uri logoUri) {
        this.serviceId = serviceId;
        this.idIsKnown = idIsKnown;
        this.name = name;
        this.address = address;
        this.commitment = commitment;
        this.logoUri = logoUri;
    }

    /**
     * Constructor for creating a new <code>SafeService</code>.
     *
     * @param name       The name of the service.
     * @param commitment The service commitment.
     * @param address    The address of the service.
     * @param logoUri    The URI for logging in to the service.
     */
    public SafeService(
        final String name, final byte[] commitment, final Uri address, final Uri logoUri) {
        this(UNKNOWN_ID, false, name, address, commitment, logoUri);
    }

    /**
     * Create a <code>SafeService</code> from a {@link Service}.
     *
     * @param service The service to create the <code>SafeService</code> from.
     */
    public SafeService(final Service service) {
        // Verify the method's preconditions
        checkNotNull(service);

        serviceId = service.getId();
        idIsKnown = true;
        name = service.getName();
        address = URIToUri(service.getAddress());
        commitment = service.getCommitment();
        logoUri = null; // logoUri currently unused.
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean idIsKnown() {
        return idIsKnown;
    }

    /**
     * Get the SafeSession instance's name.
     *
     * @return the SafeSession instance's name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the address of this <code>SafeService</code>.
     */
    public Uri getAddress() {
        return address;
    }

    /**
     * @return the commitment of this <code>SafeService</code>.
     */
    public byte[] getCommitment() {
        return commitment;
    }

    /**
     * Get the SafeService instance's logUri. The logoUri identifies a URI where a logo (or set of
     * logos) for the service can be acquired.
     *
     * @return the SafeService instance's logo URI.
     */
    public Uri getLogoUri() {
        return logoUri;
    }

    public Service createService(ServiceImpFactory factory) {
        return new Service(factory, name, UriToURI(address), commitment);
    }

    /**
     * Get the {@link Service} associated with this <code>SafeService</code>. The service
     * will be retrieved from the database.
     *
     * @param accessor The database accessor to use.
     * @return the service associated with the <code>SafeService</code>, or null if not assocaited.
     * @throws IOException if an error occurs accessing the database.
     */
    public Service getService(ServiceAccessor accessor) throws IOException {
        if (idIsKnown) {
            return accessor.getServiceById(serviceId);
        } else {
            return accessor.getServiceByCommitment(commitment);
        }
    }

    /**
     * Get the {@link Service} associated with this <code>SafeService</code>. The service
     * will be retrieved from the database. If it does not already exist a new one will be created.
     *
     * @param factory  The database factory to use.
     * @param accessor The database accessor to use.
     * @return the service associated with the <code>SafeService</code>, or a new service if none
     * was associated with it.
     * @throws IOException if an error occurs accessing the database.
     */
    public Service getOrCreateService(
        ServiceImpFactory factory, ServiceAccessor accessor)
        throws IOException {
        final Service existing = getService(accessor);
        if (existing != null) {
            return existing;
        } else {
            return createService(factory);
        }
    }

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
     * @param out   The resulting a parcel
     */
    @Override
    public void writeToParcel(final Parcel out, final int flags) {
        // Write out the commitment byte array (prepended with its length)
        out.writeInt(commitment.length);
        out.writeByteArray(commitment);

        // Write the other fields
        out.writeBooleanArray(new boolean[]{idIsKnown});
        out.writeInt(serviceId);
        out.writeString(name);
        out.writeParcelable(address, flags);
        out.writeParcelable(logoUri, flags);
    }

    /**
     * Convert a standard library Java URI instance to an equivalent android Uri instance.
     *
     * @param uri A <code>java.net.URI</code> instance.
     * @return Equivalent <code>android.net.Uri</code> instance.
     */
    public static Uri URIToUri(URI uri) {
        if (uri == null) {
            return null;
        } else {
            return Uri.parse(uri.toString());
        }
        // TODO is this too naieve? Something more complicated like this?
        /*
         * return new Uri.Builder() .scheme(uri.getScheme())
         * .encodedAuthority(uri.getRawAuthority()) .encodedPath(uri.getRawPath())
         * .query(uri.getRawQuery()) .fragment(uri.getRawFragment()) .build();
         */
    }

    /**
     * Convert an android Uri instance to an equivalent standard library Java URI instance.
     *
     * @param uri A <code>android.net.Uri</code> instance.
     * @return Equivalent <code>java.net.URI</code> instance.
     * @throws URISyntaxException
     */
    public static URI UriToURI(Uri uri) {
        if (uri == null) {
            return null;
        } else {
            try {
                return new URI(uri.toString());
                // TODO is this too naieve?
            } catch (URISyntaxException e) {
                // TODO sort this out
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Generate a <code>SafeService</code> from a {@link KeyPairingVisualCode}.
     *
     * @param code The code to create the <code>SafeService</code> from.
     * @return the generated <code>SafeService</code>.
     */
    public static SafeService fromVisualCode(
        final KeyPairingVisualCode code) {
        return new SafeService(
            code.getServiceName(),
            code.getServiceCommitment(),
            URIToUri(code.getServiceAddress()),
            null); // logoUri not yet included in this visual code type
    }

    /**
     * Generate a <code>SafeService</code> from a {@link KeyAuthenticationVisualCode}.
     *
     * @param code The code to create the <code>SafeService</code> from.
     * @return the generated <code>SafeService</code>.
     */
    public static SafeService fromVisualCode(
        final KeyAuthenticationVisualCode code) {
        return new SafeService(
            null, code.getServiceCommitment(), URIToUri(code.getServiceAddress()), null);
    }

    /**
     * Generate a <code>SafeService</code> from a {@link LensPairingVisualCode}.
     *
     * @param code The code to create the <code>SafeService</code> from.
     * @return the generated <code>SafeService</code>.
     */
    public static SafeService fromVisualCode(
        final LensPairingVisualCode code) {
        return new SafeService(
            null,
            code.getServiceCommitment(),
            null,
            null); // logoUri not yet included in this visual code type
    }

    @Override
    public Dialog getAuthFailedDialog(Activity context, String userMsg) {
        // Form string for dialog
        final boolean isPairing = context.getIntent()
            .getBooleanExtra(AuthenticateActivity.IS_PAIRING_EXTRA, false);
        final String safeName = (name != null) ? name : address.toString();
        final String message;

        if (isPairing) {
            message = (userMsg == null) ?
                context.getString(R.string.pairing_failed_fmt, safeName) :
                context.getString(R.string.pairing_failed_fmt2, safeName, userMsg);
        } else {
            message = (userMsg == null) ?
                context.getString(R.string.auth_failed_fmt, safeName) :
                context.getString(R.string.auth_failed_fmt2, safeName, userMsg);
        }

        // Build alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }
}
