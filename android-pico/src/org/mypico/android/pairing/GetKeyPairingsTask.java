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


package org.mypico.android.pairing;

import java.io.IOException;
import java.util.List;

import org.mypico.android.data.SafeKeyPairing;
import org.mypico.android.data.SafeService;
import org.mypico.android.core.PicoService;

import android.os.AsyncTask;

/**
 * Abstract class, the implementation of which allows {@link SafeKeyPairing}s to be retrieved
 * from the database.
 */
abstract class GetKeyPairingsTask
    extends AsyncTask<SafeService, Void, List<SafeKeyPairing>> {

    private PicoService picoService;
    protected Throwable problem;

    public GetKeyPairingsTask(PicoService picoService) {
        this.picoService = picoService;
    }

    @Override
    protected List<SafeKeyPairing> doInBackground(SafeService... params) {
        SafeService service = params[0];
        try {
            return picoService.getKeyPairings(service);
        } catch (IOException e) {
            problem = e;
            return null;
        }
    }

    public abstract void onPostExecute(List<SafeKeyPairing> pairings);
}
