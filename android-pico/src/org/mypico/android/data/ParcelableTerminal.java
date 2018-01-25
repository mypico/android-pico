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

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Parcel;
import android.os.Parcelable;

import org.mypico.jpico.crypto.CryptoFactory;
import org.mypico.jpico.data.terminal.Terminal;

/**
 * A decorator class that makes the {@link Terminal} class parcelable for storage in an intent.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see Terminal
 * @see Parcelable
 */
public class ParcelableTerminal implements Parcelable {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(ParcelableTerminal.class.getSimpleName());

    final private int id;
    final private String name;
    final private byte[] commitment;
    final private PublicKey picoPublicKey;
    final private PrivateKey picoPrivateKey;

    public static final Parcelable.Creator<ParcelableTerminal> CREATOR =
        new Parcelable.Creator<ParcelableTerminal>() {

            @Override
            public ParcelableTerminal createFromParcel(final Parcel source) {
                final int id = source.readInt();
                final String name = source.readString();
                final byte[] commitment = new byte[source.readInt()];
                source.readByteArray(commitment);
                final byte[] pubKeyBytes = new byte[source.readInt()];
                source.readByteArray(pubKeyBytes);
                final byte[] privKeyBytes = new byte[source.readInt()];
                source.readByteArray(privKeyBytes);

                try {
                    final KeyFactory kf = CryptoFactory.INSTANCE.ecKeyFactory();
                    PublicKey pubKey;
                    pubKey = kf.generatePublic(new X509EncodedKeySpec(pubKeyBytes));
                    final PrivateKey privKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privKeyBytes));
                    return new ParcelableTerminal(id, name, commitment, pubKey, privKey);
                } catch (InvalidKeySpecException e) {
                    LOGGER.error("InvalidKeySpecError generating ParcelableTerminal");
                    return null;
                }
            }

            @Override
            public ParcelableTerminal[] newArray(int size) {
                return new ParcelableTerminal[size];
            }
        };

    /**
     * Constructor that takes an existing terminal and makes it parcelable.
     *
     * @param terminal The terminal to parcelableise.
     */
    public ParcelableTerminal(final Terminal terminal) {
        this.id = terminal.getId();
        this.name = terminal.getName();
        this.commitment = terminal.getCommitment();
        this.picoPublicKey = terminal.getPicoPublicKey();
        this.picoPrivateKey = terminal.getPicoPrivateKey();
    }

    /**
     * Creates a new parcellable terminal, building it from the data provided.
     *
     * @param id             The id to use for the terminal.
     * @param name           The name of the terminal.
     * @param commitment     The terminal commitment.
     * @param picoPublicKey  The terminal's public key.
     * @param picoPrivateKey The terminal's private key.
     */
    public ParcelableTerminal(final int id, final String name,
                              final byte[] commitment, final PublicKey picoPublicKey,
                              final PrivateKey picoPrivateKey) {
        this.id = id;
        this.name = name;
        this.commitment = commitment;
        this.picoPublicKey = picoPublicKey;
        this.picoPrivateKey = picoPrivateKey;
    }

    /**
     * Get the terminal id.
     *
     * @return The terminal id.
     */
    public int getId() {
        return id;
    }

    /**
     * Get the terminal name.
     *
     * @return The terminal's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the terminal commitment.
     *
     * @return The terminal commitment.
     */
    public byte[] getCommitment() {
        return commitment;
    }

    /**
     * Get the terminal's public key.
     *
     * @return The terminal's public key.
     */
    public PublicKey getPicoPublicKey() {
        return picoPublicKey;
    }

    /**
     * Get the terminal's private key.
     *
     * @return The terminal's private key.
     */
    public PrivateKey getPicoPrivateKey() {
        return picoPrivateKey;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeInt(commitment.length);
        dest.writeByteArray(commitment);
        dest.writeInt(picoPublicKey.getEncoded().length);
        dest.writeByteArray(picoPublicKey.getEncoded());
        dest.writeInt(picoPrivateKey.getEncoded().length);
        dest.writeByteArray(picoPrivateKey.getEncoded());
    }
}
