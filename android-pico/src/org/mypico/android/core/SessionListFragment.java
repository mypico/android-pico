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


package org.mypico.android.core;

import org.mypico.android.data.SafeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

/**
 * Provides the abstract class for the session list UI for displaying currently running sessions.
 * <p>
 * The implementation used by Pico can be found as the {@link CurrentSessionListFragment} class.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @see ArrayAdapter
 * @see SessionArrayAdapter
 * @see ListFragment
 * @see CurrentSessionListFragment
 */
abstract class SessionListFragment extends ListFragment {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SessionListFragment.class.getSimpleName());

    private class SessionUpdateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (PicoServiceImpl.SESSION_INFO_UPDATE.equals(intent.getAction())) {
                // Get session info from intent and check not null
                SafeSession session = (SafeSession) intent.getParcelableExtra(
                    SafeSession.class.getCanonicalName());
                if (session != null) {
                    LOGGER.debug("session info retrieved from broadcast update intent");
                    // Call on the subclass to cope with this new update
                    update(session);
                } else {
                    LOGGER.warn("no session info in broadcast update intent");
                }
            } else if (PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS.equals(intent.getAction())) {
                ArrayList<SafeSession> list = intent.getParcelableArrayListExtra(ArrayList.class.getCanonicalName());
                for (SafeSession session : list) {
                    update(session);
                }
            } else {
                LOGGER.warn("Unrecognized action {}", intent.getAction());
            }
        }
    }

    protected SessionArrayAdapter adapter;
    private LocalBroadcastManager broadcastManager;
    private BroadcastReceiver broadcastReceiver;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        broadcastManager = LocalBroadcastManager.getInstance(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        broadcastReceiver = new SessionUpdateReceiver();
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        adapter = new SessionArrayAdapter(inflater.getContext(), R.layout.list_session_info);
        setListAdapter(adapter);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setEmptyText(getText(R.string.no_session_history));
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(PicoServiceImpl.SESSION_INFO_UPDATE);
        filter.addAction(PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);

        broadcastManager.registerReceiver(broadcastReceiver, filter);
        LOGGER.debug("Registered broadcast receiver");

        final Intent requestIntent = new Intent(getActivity(), PicoServiceImpl.class);
        requestIntent.setAction(PicoServiceImpl.ACTION_BROADCAST_ALL_SESSIONS);
        getActivity().startService(requestIntent);
    }

    @Override
    public void onPause() {
        super.onPause();
        broadcastManager.unregisterReceiver(broadcastReceiver);
        LOGGER.debug("Unregistered broadcast receiver");
    }

    /**
     * Update one of the session elements. This will be called automatically when the
     * {@link SessionUpdateReceiver} recieves a notification about the session.
     *
     * @param session The session to update.
     */
    public abstract void update(SafeSession session);
}
