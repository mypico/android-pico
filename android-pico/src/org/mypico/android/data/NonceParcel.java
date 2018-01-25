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

import android.os.Parcel;
import android.os.Parcelable;

import org.mypico.jpico.crypto.Nonce;

/**
 * A decorator class that makes the {@link Nonce} class parcelable for storage in an intent.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @see Nonce
 * @see Parcelable
 */
public class NonceParcel implements Parcelable {

    public static final Parcelable.Creator<NonceParcel> CREATOR =
        new Parcelable.Creator<NonceParcel>() {

            @Override
            public NonceParcel createFromParcel(Parcel source) {
                final byte[] bytes = new byte[source.readInt()];
                source.readByteArray(bytes);
                return new NonceParcel(Nonce.getInstance(bytes));
            }

            @Override
            public NonceParcel[] newArray(int size) {
                return new NonceParcel[size];
            }
        };

    private final Nonce nonce;

    /**
     * Make a new parcelable version of the provided nonce.
     *
     * @param nonce The nonce to make parcelable.
     */
    public NonceParcel(Nonce nonce) {
        this.nonce = nonce;
    }

    /**
     * Get the nonce value.
     *
     * @return the nonce value.
     */
    public Nonce getNonce() {
        return this.nonce;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final byte[] bytes = nonce.getValue();
        dest.writeInt(bytes.length);
        dest.writeByteArray(bytes);
    }
}
