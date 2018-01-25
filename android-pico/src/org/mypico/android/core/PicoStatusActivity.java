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

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.android.core.PicoServiceImpl.PicoServiceBinder;
import org.mypico.android.data.SafeSession;
import org.mypico.android.pairing.PairingsFragment;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;

/**
 * Provides the tab-based user interface in the Pico application, for example containing the list
 * of pairings and running sessions. The implementation uses the {@link TabsActivity} class to
 * create the UI from the fragments defined here.
 * <p>
 * This class also listens for events triggered by clicking on the service items in the session
 * list of the UI.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class PicoStatusActivity extends TabsActivity
    implements CurrentSessionListFragment.Listener, ServiceConnection {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(PicoStatusActivity.class.getSimpleName());

    public static final int START_AUTHENTICATION_CODE = 1;
    public static final int CONFIGURE_BACKUP_PROVIDER_CODE = 2;

    public static final int INDEX_SESSIONS_TAB = 0;
    public static final int INDEX_SERVICES_TAB = 1;

    @Override
    protected List<Class<? extends android.support.v4.app.Fragment>> getFragments() {
        final List<Class<? extends android.support.v4.app.Fragment>> fragments =
            new LinkedList<Class<? extends android.support.v4.app.Fragment>>();
        fragments.add(CurrentSessionListFragment.class);
        fragments.add(PairingsFragment.class);
        return fragments;
    }

    @Override
    protected List<CharSequence> getTitles() {
        final List<CharSequence> fragments = new LinkedList<CharSequence>();
        //fragments.add(getString(R.string.title_fragment_recent));
        fragments.add(getString(R.string.title_fragment_recent));
        fragments.add(getString(R.string.title_fragment_pairings));
        return fragments;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * These methods implement the ServiceConnection interface and deal with binding and unbinding 
     * the PicoService during the activity lifecycle. 
     */
    private PicoService picoService;
    private boolean bound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to the Pico service
        Intent intent = new Intent(this, PicoServiceImpl.class);
        bindService(intent, this, BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from the Pico service
        if (bound) {
            unbindService(this);
            bound = false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        picoService = ((PicoServiceBinder) binder).getService();
        bound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bound = false;
    }

    /*
     * These methods handle events coming from the nested session list fragments.
     */
    @Override
    public void onSessionResume(SafeSession session) {
        if (bound) {
            picoService.resumeSession(session);
        } else {
            LOGGER.warn("pico service not bound, cannot resume session");
        }
    }

    @Override
    public void onSessionPause(SafeSession session) {
        if (bound) {
            picoService.pauseSession(session);
        } else {
            LOGGER.warn("pico service not bound, cannot pause session");
        }
    }

    @Override
    public void onSessionClose(SafeSession session) {
        if (bound) {
            picoService.closeSession(session);
        } else {
            LOGGER.warn("pico service not bound, cannot close session");
        }
    }
}
