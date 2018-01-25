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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.mypico.android.data.SafeSession;
import org.mypico.android.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Provides the UI elements for displaying sessions in the UI session list. This provides the
 * individual entries for each running session.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @see ArrayAdapter
 * @see SessionListFragment
 * @see ListFragment
 * @see CurrentSessionListFragment
 */
final class SessionArrayAdapter extends ArrayAdapter<SafeSession> {

    @SuppressLint("SimpleDateFormat")
    private static final DateFormat lastAuthFormat =
        new SimpleDateFormat("d MMM yyyy, HH:mm:ss");

    public SessionArrayAdapter(Context context, int resource) {
        super(context, resource);
    }

    public SessionArrayAdapter(Context context, int resource, List<SafeSession> sessions) {
        super(context, resource, sessions);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Inflate the view and get references to the inner views
        LayoutInflater inflater =
            (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.list_session_info, parent, false);
        TextView pairingNameView = (TextView) view.findViewById(
            R.id.list_session_info__pairing_name);
        TextView lastAuthView = (TextView) view.findViewById(
            R.id.list_session_info__last_auth);
        ImageView statusIconView = (ImageView) view.findViewById(
            R.id.list_session_info__status_icon);

        // Get the session info for this list item
        SafeSession sessionInfo = getItem(position);

        // Insert relevant values into the view
        pairingNameView.setText(sessionInfo.getSafePairing().getDisplayName());
        lastAuthView.setText(lastAuthFormat.format(sessionInfo.getLastAuthDate()));
        switch (sessionInfo.getStatus()) {
            case ACTIVE:
                statusIconView.setImageResource(android.R.drawable.presence_online);
                break;
            case PAUSED:
                statusIconView.setImageResource(android.R.drawable.presence_away);
                break;
            case ERROR:
                statusIconView.setImageResource(android.R.drawable.presence_busy);
                break;
            case CLOSED:
            default:
                statusIconView.setImageResource(android.R.drawable.presence_invisible);
                break;
        }

        return view;
    }
}
