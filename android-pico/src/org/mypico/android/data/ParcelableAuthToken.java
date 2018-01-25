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

import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.crypto.AuthTokenFactory;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A decorator class that makes an AuthToken instance Parcelable.
 *
 * @author Max Spencer <ms955@cl.cam.ac.uk>
 */
final public class ParcelableAuthToken implements AuthToken, Parcelable {

    public static final Parcelable.Creator<ParcelableAuthToken> CREATOR =
        new Parcelable.Creator<ParcelableAuthToken>() {

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
            public ParcelableAuthToken createFromParcel(Parcel source) {
                final byte[] tokenBytes = new byte[source.readInt()];
                source.readByteArray(tokenBytes);
                try {
                    return new ParcelableAuthToken(
                        AuthTokenFactory.fromByteArray(tokenBytes));
                } catch (IOException e) {
                    throw new RuntimeException(
                        "Exception occured whilst creating " +
                            "ParcelableSessionAuthToken from Parcel", e);
                }
            }

            @Override
            public ParcelableAuthToken[] newArray(int size) {
                return new ParcelableAuthToken[size];
            }
        };

    private AuthToken authToken;

    public ParcelableAuthToken(AuthToken authToken) {
        this.authToken = authToken;
    }

    @Override
    public String getFull() {
        return authToken.getFull();
    }

    @Override
    public String getFallback() {
        return authToken.getFallback();
    }

    @Override
    public byte[] toByteArray() throws IOException {
        return authToken.toByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        try {
            final byte[] tokenBytes = authToken.toByteArray();
            out.writeInt(tokenBytes.length);
            out.writeByteArray(tokenBytes);
        } catch (IOException e) {
            throw new RuntimeException(
                "Exception occured writing ParcelableSessionAuthToken " +
                    "to Parcel", e);
        }
    }
}
