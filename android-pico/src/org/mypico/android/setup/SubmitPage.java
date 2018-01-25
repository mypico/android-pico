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


package org.mypico.android.setup;

/**
 * Interface for allowing a page to be submitted.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public interface SubmitPage {

    /**
     * This method should be set to return true if a wizard page is ready to submit (e.g. the user
     * has completed all of the required fields on the page) or false otherwise.
     * <p>
     * If this function returns false, the button at the bottom of the screen to move to the next
     * page will be greyed out, otherwise it will be active.
     *
     * @return true if the user is ready to move to the next page, false o/w.
     */
    public boolean isReadyToSubmit();
}
