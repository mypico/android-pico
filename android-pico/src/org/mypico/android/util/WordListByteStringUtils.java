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


package org.mypico.android.util;


/**
 * Convert a word list into the byte string key used for encryption.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see PgpWordListByteString for an implementation of this abstract class.
 */
public abstract class WordListByteStringUtils {

    /**
     * Convert the bytestring to a sequence of words.
     *
     * @param bytes The key.
     * @return The sequence of words the key corresponds to.
     */
    public abstract String toWords(byte[] bytes);

    /**
     * Convert a sequence of words to a key byte array.
     *
     * @param s The sequence of words.
     * @return the key as a byte array.
     * @throws InvalidWordException thrown if any of the words are not valid words from the PGP
     *                              word list, or a word has the wrong parity.
     */
    public abstract byte[] fromWords(String s) throws InvalidWordException;

    /**
     * Get the word list being used for the conversion.
     *
     * @return the word list being used.
     */
    public abstract String[] getWordList();
}