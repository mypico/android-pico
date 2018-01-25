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
 * Provides the general classes and interfaces needed to interact with terminal verifiers. A
 * terminal, in Pico parlance, is the system with which the Pico performs a SIGMA-I protocol. This
 * includes a mutual authentication, but may not be the service the user actually wants to
 * authenticate to and access. Rather, it may be the terminal that the user is accessing a service
 * through.
 * <p>
 * The most likely configuration is that the user is interacting with a web browser (the terminal)
 * in order to interact with a website (the service). In this case, the Pico will authenticate
 * with the service directly and pass the resulting authentication session cookie on to the
 * terminal.
 */

/**
 * Provides the general classes and interfaces needed to interact with terminal verifiers. A
 * terminal, in Pico parlance, is the system with which the Pico performs a SIGMA-I protocol. This
 * includes a mutual authentication, but may not be the service the user actually wants to
 * authenticate to and access. Rather, it may be the terminal that the user is accessing a service
 * through.
 *
 * The most likely configuration is that the user is interacting with a web browser (the terminal)
 * in order to interact with a website (the service). In this case, the Pico will authenticate
 * with the service directly and pass the resulting authentication session cookie on to the
 * terminal.
 *
 */
package org.mypico.android.terminal;