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


// Copyright University of Cambridge, 2014

package org.mypico.android.data;

import java.io.IOException;
import java.util.Date;

import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.data.session.Session;
import org.mypico.jpico.data.session.SessionAccessor;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Representation of a Session instance for display on the UI.
 * <p>
 * The SafeSession class is a representation of a Session, that contains only the information
 * required by the UI. Sensitive data such as the Session's long term shared secret are not passed
 * to the UI thread.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @see Session
 */
final public class SafeSession implements Parcelable {

    private int id;
    private SafePairing pairing;
    private Session.Status status;
    private Session.Error error;
    private Date lastAuthDate;
    private ParcelableAuthToken authToken;

    /**
     * Generates a SafeSession instance from a Parcelable class, whose data had previously been
     * written by Parcelable.writeToParcel().
     *
     * @see android.os.Parcelable.Creator
     */
    public static final Parcelable.Creator<SafeSession> CREATOR =
        new Parcelable.Creator<SafeSession>() {

            /**
             * Create a new instance of the Parcelable class. A ServiceInfo is instantiated
             * given a Parcel whose data had previously been written by
             * Parcelable.writeToParcel().
             *
             * @param Parcel a Parcel whose data had previously been written by
             *        Parcelable.writeToParcel()
             * @return a unmarshaled ServiceInfo instance
             */
            @Override
            public SafeSession createFromParcel(Parcel source) {

                long t;

                return new SafeSession(
                    source.readInt(),
                    (SafePairing) source.readParcelable(SafePairing.class.getClassLoader()),
                    Session.Status.values()[source.readInt()],
                    Session.Error.values()[source.readInt()],
                    ((t = source.readLong()) > 0) ? new Date(t) : null,
                    (ParcelableAuthToken) source.readParcelable(
                        ParcelableAuthToken.class.getClassLoader())
                );
            }

            /**
             * Create a new array of the Parcelable class.
             *
             * @param size the size of the array to create.
             * @return the array
             */
            @Override
            public SafeSession[] newArray(int size) {

                return new SafeSession[size];
            }
        };

    /**
     * Constructor for creating a <code>SafeSession</code> populated with the data provided.
     *
     * @param sessionId    The session id for the session.
     * @param pairing      The pairing for the session.
     * @param status       The status of the session.
     * @param error        Any error state associated with the service.
     * @param lastAuthDate The last time and date an authentication occurred.
     * @param authToken    An auth token to associate with the session.
     */
    private SafeSession(
        final int sessionId,
        final SafePairing pairing,
        final Session.Status status,
        final Session.Error error,
        final Date lastAuthDate,
        final ParcelableAuthToken authToken) {
        // Validate arguments
        assert (pairing != null);

        this.id = sessionId;
        this.pairing = pairing;
        this.status = status;
        this.error = error;
        this.lastAuthDate = lastAuthDate;
        this.authToken = authToken;
    }

    /**
     * Constructor for creating a new <code>SafeSession</code>.
     *
     * @param session Session instance
     */
    public SafeSession(final Session session) {
        if (session == null) {
            throw new NullPointerException(
                "SafeSession cannot be created from a null Session");
        } else {
            id = session.getId();
            pairing = new SafePairing(session.getPairing());
            status = session.getStatus();
            error = session.getError();
            lastAuthDate = session.getLastAuthDate();
            if (session.hasAuthToken()) {
                authToken = new ParcelableAuthToken(session.getAuthToken());
            } else {
                authToken = null;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SafeSession) {
            SafeSession other = (SafeSession) obj;
            return (id == other.id);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return id;
    }

    /**
     * Get the SafeSession instance's id.
     *
     * @return the SafeSession instance's id.
     */
    public int getId() {
        return id;
    }

    /**
     * Get the SafeSession instance's pairing.
     *
     * @return the PairingInfo representing the Pairing between the Pico and a Service that this
     * session represents.
     */
    public SafePairing getSafePairing() {
        return pairing;
    }

    /**
     * Get the SafeSession instance's status.
     *
     * @return the SafeSession instance's current status.
     */
    public Session.Status getStatus() {
        return status;
    }

    /**
     * Get the SafeSession instance's errorCode.
     *
     * @return if the SafeSession instance's current status is ERROR, this attribute yields further
     * information as to the cause of the error.
     */
    public Session.Error getError() {
        return error;
    }

    /**
     * Get the last authentication date of the <code>SafeSession</code>.
     *
     * @return the last authentication date.
     */
    public Date getLastAuthDate() {
        return lastAuthDate;
    }

    /**
     * Get whether or not there's an auth token associated with the session.
     *
     * @return true if there is an auth token, false o/w.
     */
    public boolean hasAuthToken() {
        return (authToken != null);
    }

    /**
     * Get the auth token associated with the session.
     *
     * @return the auth token, or null if there is none.
     * @see #hasAuthToken()
     */
    public AuthToken getAuthToken() {
        if (authToken == null) {
            throw new IllegalStateException(
                "AuthToken has already been read.");
        } else {
            try {
                return authToken;
            } finally {
                authToken = null;
            }
        }
    }

    /**
     * Get the {@link Session} associated with this <code>SafeSession</code>. The {@link Session}
     * will be extracted from the database.
     *
     * @param accessor The database accessor to use.
     * @return The {@link Session} associated with this <code>SafeSession</code>.
     * @throws IOException if an error occurs accessing the database.
     */
    public Session getSession(
        final SessionAccessor accessor) throws IOException {
        return accessor.getSessionById(id);
    }

    /**
     * Tells whether or not Session instance is in an error state.
     *
     * @return <code>false</code> if the Session is in an error state or <code>true</code>
     * otherwise.
     */
    public boolean isOk() {
        return (status != Session.Status.ERROR);
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
     * @param out   The resulting parcel
     */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(id);
        out.writeParcelable(pairing, flags);
        out.writeInt(status.ordinal());
        out.writeInt(error.ordinal());
        out.writeLong((lastAuthDate != null) ? lastAuthDate.getTime() : 0);
        out.writeParcelable(authToken, flags);
    }
}

// End of file
