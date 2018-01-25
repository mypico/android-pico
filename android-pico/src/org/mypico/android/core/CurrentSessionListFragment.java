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
import org.mypico.android.R;
import org.mypico.jpico.data.session.Session;
import org.mypico.jpico.data.session.Session.Status;

import android.app.Activity;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ListView;

/**
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 */
public class CurrentSessionListFragment extends SessionListFragment {

    public interface Listener {
        void onSessionResume(SafeSession session);

        void onSessionPause(SafeSession session);

        void onSessionClose(SafeSession session); // return true if closed successfully
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
        // throws ClassCastException if activity doesn't implement Listener interface.
    }

    /**
     * Resume a currently paused continuous authentication session.
     */
    private synchronized void resumeSelected() {
        SparseBooleanArray b = getListView().getCheckedItemPositions();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (b.get(i)) {
                SafeSession session = adapter.getItem(i);
                if (session.getStatus() == Status.PAUSED) {
                    // only resume sessions which are currently paused
                    listener.onSessionResume(session);
                }
            }
        }
    }

    /**
     * Pause a currently active continuous authentication session.
     */
    private synchronized void pauseSelected() {
        SparseBooleanArray b = getListView().getCheckedItemPositions();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (b.get(i)) {
                SafeSession session = adapter.getItem(i);
                if (session.getStatus() == Status.ACTIVE) {
                    // on pause session which are currently active
                    listener.onSessionPause(session);
                }
            }
        }
    }

    /**
     * Close a currently active or paused continuous authentication session.
     */
    private synchronized void closeSelected() {
        SparseBooleanArray b = getListView().getCheckedItemPositions();
        for (int i = 0; i < adapter.getCount(); i++) {
            SafeSession session = adapter.getItem(i);
            Session.Status status = session.getStatus();
            if (b.get(i)) {
                if (status == Status.ACTIVE || status == Status.PAUSED) {
                    // only stop sessions which are currently active or paused
                    // (should be all of them in a current session list)
                    listener.onSessionClose(session);
                }
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final ListView listView = getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new MultiChoiceModeListener() {

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_resume) {
                    resumeSelected();
                    mode.finish();
                    return true;
                } else if (itemId == R.id.action_pause) {
                    pauseSelected();
                    mode.finish();
                    return true;
                } else if (itemId == R.id.action_close) {
                    closeSelected();
                    mode.finish();
                    return true;
                } else {
                    // Not handled here
                    return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate the menu for the contextual action bar
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.session_list, menu);
                menu.findItem(R.id.action_pause).setVisible(false);
                menu.findItem(R.id.action_resume).setVisible(false);
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Default changes when context ended
            }

            @Override
            public boolean onPrepareActionMode(ActionMode arg0, Menu arg1) {
                // Not used
                return false;
            }

            @Override
            public void onItemCheckedStateChanged(
                ActionMode mode, int position, long id, boolean checked) {
                // Not used -- no changes required when checked state changes
            }

        });
    }

    @Override
    public synchronized void update(SafeSession session) {
        // Ensure the list adapter is initialised
        if (adapter == null) {
            throw new IllegalStateException("adapter not initialised");
        }

        int index = adapter.getPosition(session);
        if (index >= 0) {
            // already present, need to remove the current adapter/list entry
            adapter.remove(session);
        } else {
            // not present
            index = 0;
        }

        // add the adapter/list entry
        if (session.getStatus() == Status.ACTIVE || session.getStatus() == Status.PAUSED) {
            adapter.insert(session, index);
        }
    }
}
