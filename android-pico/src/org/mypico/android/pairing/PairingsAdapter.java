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

import org.mypico.android.data.SafePairing;
import org.mypico.android.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * Shows the pairings in the UI.
 *
 * @author Max Spences <ms955@cam.ac.uk>
 */
public class PairingsAdapter extends ArrayAdapter<SafePairing> {

    private class ViewHolder {
        TextView name;
    }

    public PairingsAdapter(Context context, int resource) {
        super(context, resource);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the view holder
        final ViewHolder holder;
        if (convertView == null) {
            // Inflate the layout
            convertView = LayoutInflater.from(
                getContext()).inflate(R.layout.row_pairing, parent, false);

            // Store the views in a holder
            holder = new ViewHolder();
            holder.name = (TextView) convertView.findViewById(R.id.row_pairing__name);

            // Store the holder with the view
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Get the Terminal for this list item position
        final SafePairing pairing = getItem(position);

        // Fill in row details
        holder.name.setText(pairing.getDisplayName());

        return convertView;
    }
}
