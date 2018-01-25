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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.mypico.android.R;

import android.content.Context;

/**
 * Convert a PGP word list into the byte string key used for encryption.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see WordListByteStringUtils
 */
public final class PgpWordListByteString extends WordListByteStringUtils {

    private final String[] evenWordList;
    private final String[] oddWordList;
    private final Map<String, Integer> invertedEvenWordList;
    private final Map<String, Integer> invertedOddWordList;

    /**
     * Constructor.
     *
     * @param c The context.
     */
    public PgpWordListByteString(final Context c) {
        evenWordList = c.getApplicationContext().getResources().getStringArray(R.array.pgp_word_list_even);
        oddWordList = c.getApplicationContext().getResources().getStringArray(R.array.pgp_word_list_odd);
        invertedEvenWordList = invertWordList(evenWordList);
        invertedOddWordList = invertWordList(oddWordList);
    }

    /**
     * Invert the ordering of the list of words.
     *
     * @param wordList The original list.
     * @return the inverted list.
     */
    private Map<String, Integer> invertWordList(final String[] wordList) {
        final Map<String, Integer> invertedWordList = new HashMap<String, Integer>(wordList.length);
        for (int i = 0; i < wordList.length; i++) {
            invertedWordList.put(wordList[i].toLowerCase(Locale.UK), Integer.valueOf((byte) i));
        }
        return invertedWordList;
    }

    /**
     * Convert the bytestring to a sequence of words.
     *
     * @param bytes The key.
     * @return The sequence of words the key corresponds to.
     */
    public String toWords(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        boolean even = true;

        for (byte b : bytes) {
            if (even) {
                sb.append(evenWordList[b + 128]);
            } else {  // odd
                sb.append(oddWordList[b + 128]);
            }
            sb.append(' ');
            even = !even;
        }
        return sb.toString();
    }

    /**
     * Convert a sequence of words to a key byte array.
     *
     * @param wordsString The sequence of words.
     * @return the key as a byte array.
     * @throws InvalidWordException thrown if any of the words are not valid words from the PGP
     *                              word list, or a word has the wrong parity.
     */
    public byte[] fromWords(String wordsString) throws InvalidWordException {
        final String[] words = wordsString.split(" ");
        return fromWords(words);
    }

    /**
     * Convert an aray of words to a key byte array.
     *
     * @param s The array of words.
     * @return the key as a byte array.
     * @throws InvalidWordException thrown if any of the words are not valid words from the PGP
     *                              word list, or a word has the wrong parity.
     */
    public byte[] fromWords(String[] s) throws InvalidPGPWordException {
        final byte[] bytes = new byte[s.length];
        boolean even = true;
        for (int index = 0; index < s.length; index++) {
            final Integer b;
            if (even) {
                b = invertedEvenWordList.get(s[index].toLowerCase(Locale.US));
            } else { // odd
                b = invertedOddWordList.get(s[index].toLowerCase(Locale.US));
            }
            if (b == null) throw new InvalidPGPWordException(index, s[index]);
            bytes[index] = (byte) (b - 128);
            even = !even;
        }
        return bytes;
    }

    /**
     * Convert the key from an array of bytes to a hexidecimal string.
     *
     * @param bytes The key bytes.
     * @return the key as a hex string.
     */
    public String toHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    @Override
    public String[] getWordList() {
        final String[] allWords = new String[evenWordList.length + oddWordList.length];
        System.arraycopy(evenWordList, 0, allWords, 0, evenWordList.length);
        System.arraycopy(oddWordList, 0, allWords, evenWordList.length, oddWordList.length);
        return allWords;
    }
}