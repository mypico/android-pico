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
 * Exception used to indicate that one of the words entered by the user is invalid.
 * <p>
 * See the {@link InvalidPGPWordException} for an implementation of this abstract class.
 *
 * @author Chris Warrington Chris Warrington Chris Warrington <cw471@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public abstract class InvalidWordException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param description A description of the exception.
     */
    public InvalidWordException(final String description) {
        super(description);
    }

    /**
     * Convert the exception into a human-readable error message.
     *
     * @return The human-readable version of the error message.
     */
    public abstract String getHumanErrorMessage();

    /**
     * Get the position of the incorrect word in the word list.
     *
     * @return the position.
     */
    public abstract int getWordPosition();

    /**
     * Get the incorrect word.
     *
     * @return the incorrect word.
     */
    public abstract String getWord();
}