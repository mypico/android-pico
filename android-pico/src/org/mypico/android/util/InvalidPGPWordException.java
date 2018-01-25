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
 * Exception used to indicate that one of the PGP words entered by the user is invalid.
 * <p>
 * The possible words depend on the words in the PGP word list, which have either three or two
 * syllables depending on their parity in the key. Therefore it's possible to hint to the user
 * that the word's they've entered are incorrect, independent of whether the key itself is
 * correct.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public final class InvalidPGPWordException extends InvalidWordException {

    private static final long serialVersionUID = 1L;
    private final int position;
    private final String word;

    /**
     * Constructor.
     *
     * @param position The position of the incorrect word in the word list as entered by the user.
     * @param word     The incorrect word entered by the user.
     */
    public InvalidPGPWordException(final int position, final String word) {
        super(String.format("Word %s at position %d is not in the " +
            (position % 2 == 0 ? "even" : "odd") + " PGP word List", word, position));
        this.position = position;
        this.word = word;
    }

    /**
     * Convert the exception into a human-readable error message.
     *
     * @return The human-readable version of the error message.
     */
    public String getHumanErrorMessage() {
        final StringBuilder sb = new StringBuilder();
        sb.append(position);
        sb.append(word);
        return sb.toString();
    }

    /**
     * Get the position of the incorrect word in the word list.
     *
     * @return the position.
     */
    public int getWordPosition() {
        return position;
    }

    /**
     * Get the incorrect word.
     *
     * @return the incorrect word.
     */
    public String getWord() {
        return word;
    }

}