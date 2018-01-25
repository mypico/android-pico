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

import java.util.List;

import org.mypico.android.data.SafeKeyPairing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.view.View;
import android.widget.ListView;

final public class KeyPairingListFragment extends PairingListFragment {

    public interface Listener {
        public void onNoPairings();

        public void onSinglePairing(SafeKeyPairing pairing);

        public void onMultiplePairings(int count);

        public void onPairingClicked(SafeKeyPairing pairing);
    }

    private class UpdateTask extends GetKeyPairingsTask {

        public UpdateTask() {
            super(picoService);
        }

        @Override
        public void onPostExecute(final List<SafeKeyPairing> pairings) {
            LOGGER.info(
                "{} pairings retrieved from the database",
                pairings.size());

            // Notify parent activity of the pairings returned if any. The
            // different callbacks allow the activity to take different
            // actions in each case.
            final int count = pairings.size();
            if (count == 0) {
                listener.onNoPairings();
            } else if (count == 1) {
                listener.onSinglePairing(pairings.get(0));
            } else if (count > 1) {
                listener.onMultiplePairings(count);

                // Update list adapter
                adapter.clear();
                adapter.addAll(pairings);
                adapter.notifyDataSetChanged();
            } else {
                LOGGER.error("Cannot have {} pairings", count);
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(
        KeyPairingListFragment.class.getSimpleName());

    private Listener listener;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity; // throws ClassCastException
    }

    @Override
    protected void updatePairingList() {
        // Update the list of pairings (happens asynchronously)
        LOGGER.debug("Updating pairings list...");
        new UpdateTask().execute(serviceInfo);
    }

    @Override
    public void onListItemClick(
        final ListView l,
        final View v,
        final int position,
        final long id) {
        final SafeKeyPairing pairing = (SafeKeyPairing) adapter.getItem(position);
        listener.onPairingClicked(pairing);
    }
}
