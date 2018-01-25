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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;

import android.app.Activity;
import android.app.ListFragment;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Fragment for selecting from a list of Lens pairings.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
final public class LensPairingListFragment extends ListFragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        LensPairingListFragment.class.getSimpleName());
    private static final String PAIRINGS = "PAIRINGS";
    private static final String SERVICE = "SERVICE";

    private Listener listener;
    private ArrayAdapter<SafePairing> adapter;
    private View fragmentView;

    /**
     * Factory for creating a new {@link LensPairingListFragment}.
     *
     * @param pairings The pairing to show.
     * @param service  The service associated with the pairing.
     * @return The generated {@ilnk LensPairingListFragment}.
     */
    static LensPairingListFragment newInstance(final ArrayList<? extends SafePairing> pairings,
                                               final SafeService service) {
        final LensPairingListFragment frag = new LensPairingListFragment();

        // Supply index input as an argument.
        final Bundle args = new Bundle();
        args.putParcelableArrayList(PAIRINGS, pairings);
        args.putParcelable(SERVICE, service);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Listener for reacting to user interaction.
     */
    public interface Listener {
        /**
         * Called when a pairing is clicked by the user.
         *
         * @param pairing The pairing the user clicked.
         */
        public void onPairingClicked(SafePairing pairing);
    }

    /**
     * Update the view for a particular service if it changes.
     *
     * @param service The service to update.
     */
    private void updateView(final SafeService service) {
        if (service != null) {
            String serviceName;
            final Uri serviceAddress;
            String serviceHost;

            if ((serviceName = service.getName()) == null) {
                serviceName = getString(R.string.unknown_service_name);
            }

            if ((serviceAddress = service.getAddress()) == null) {
                serviceHost = getString(R.string.unknown_service_host);
            } else {
                serviceHost = serviceAddress.getHost();
            }

            ((TextView) fragmentView.findViewById(
                R.id.fragment_lens_pairing_list__text1)).setText(serviceName);
            ((TextView) fragmentView.findViewById(
                R.id.fragment_lens_pairing_list__text2)).setText(serviceHost);

            // TODO change the way this works, it needs to be a bit more
            // complicated to allow users to not retrieve logos.
            // ((ImageView) fragmentView.findViewById(
            // R.id.fragment_service__logo)).setImageURI(service.getLogoUri());
            // For now just load a small set of them
            final String serviceNameLower = serviceName.toLowerCase(Locale.UK);
            final int resource;
            if (serviceNameLower.contains("gmail"))
                resource = R.drawable.gmail;
            else if (serviceHost.contains("facebook.com"))
                resource = R.drawable.facebook;
            else if (serviceHost.contains("linkedin.com"))
                resource = R.drawable.linkedin;
            else if (serviceNameLower.contains("wordpress"))
                resource = R.drawable.wordpress;
            else
                resource = R.drawable.generic_website;

            ((ImageView) fragmentView.findViewById(
                R.id.fragment_lens_pairing_list__logo)).setImageResource(resource);
        }
    }

    /**
     * Update the list of pairings to be shown in the UI.
     *
     * @param pairings The pairings to be shown.
     */
    private void updatePairingList(final List<SafePairing> pairings) {
        LOGGER.debug("Found pairings {}", pairings);

        adapter.clear();
        adapter.addAll(pairings);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity; // throws ClassCastException
    }

    @Override
    public View onCreateView(
        final LayoutInflater inflater,
        final ViewGroup container,
        final Bundle savedInstanceState) {
        adapter = new ArrayAdapter<SafePairing>(
            inflater.getContext(),
            android.R.layout.simple_list_item_1
        );
        adapter = new PairingsAdapter(inflater.getContext(), R.layout.row_pairing);
        setListAdapter(adapter);

        fragmentView = inflater.inflate(R.layout.fragment_lens_pairing_list, container, false);
        return fragmentView;
    }

    @Override
    public void onListItemClick(
        final ListView l,
        final View v,
        final int position,
        final long id) {
        final SafePairing pairing =
            (SafePairing) adapter.getItem(position);
        listener.onPairingClicked(pairing);
    }

    public void onResume() {
        super.onResume();

        final List<SafePairing> pairings =
            getArguments().getParcelableArrayList(PAIRINGS);
        updatePairingList(pairings);

        final SafeService service =
            getArguments().getParcelable(SERVICE);
        updateView(service);
    }
}