/**
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
 *
 * <p>
 * Provides the general classes and interfaces for supporting Bluetooth in the Pico Android app.
 * This includes both communicating using the SIGMA-I protocol, as well as listening for broadcast
 * beacons from a Pico verifier. Bluetooth functionality is executed in a separate thread.
 */

/**
 * Provides the general classes and interfaces for supporting Bluetooth in the Pico Android app.
 * This includes both communicating using the SIGMA-I protocol, as well as listening for broadcast
 * beacons from a Pico verifier. Bluetooth functionality is executed in a separate thread.
 *
 */
package org.mypico.android.bluetooth;