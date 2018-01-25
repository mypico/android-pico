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

import org.mypico.android.core.PicoService;
import org.mypico.android.core.PicoServiceImpl;
import org.mypico.android.data.SafePairing;
import org.mypico.android.data.SafeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ListFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Abstract base class for PairingListFragments, a concrete subclass such as
 * {@link KeyPairingListFragment} or {@link LensPairingListFragment} must be used.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public abstract class PairingListFragment
    extends ListFragment implements ServiceConnection {

    private final static Logger LOGGER =
        LoggerFactory.getLogger(PairingListFragment.class.getSimpleName());

    protected ArrayAdapter<SafePairing> adapter;
    protected SafeService serviceInfo;

    protected PicoService picoService;
    private boolean bound = false;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get service info from parent activity's intent
        final Intent intent = getActivity().getIntent();
        serviceInfo = (SafeService) intent.getParcelableExtra(
            SafeService.class.getCanonicalName());

        if (serviceInfo != null) {
            LOGGER.debug("Got service info from activity's intent");
        } else {
            LOGGER.warn("Failed to get service info from activity's intent");
        }

        LOGGER.debug("Fragment created");
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
        setListAdapter(adapter);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to the Pico service
        final Intent intent =
            new Intent(getActivity(), PicoServiceImpl.class);
        getActivity().bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    public void onResume() {
        super.onResume();

        // If the pico service is bound, then update the pairing list.
        if (bound) {
            updatePairingList();
        }
    }

    protected abstract void updatePairingList();

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from the Pico service
        if (bound) {
            getActivity().unbindService(this);
            bound = false;
        }
    }

    @Override
    public abstract void onListItemClick(
        final ListView l,
        final View v,
        final int position,
        final long id);

    // implements ServiceConnection

    @Override
    public void onServiceConnected(
        final ComponentName name, final IBinder binder) {
        picoService = ((PicoServiceImpl.PicoServiceBinder) binder).getService();
        bound = true;

        updatePairingList();
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        bound = false;
    }
}
