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

import java.util.Locale;

import org.mypico.android.data.SafeService;
import org.mypico.android.R;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Display a single service entry in the UI.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class ServiceFragment extends Fragment {

    private static final String TAG = ServiceFragment.class.getSimpleName();

    private View fragmentView;
    private SafeService service;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getActivity().getIntent();
        service = (SafeService) intent.getParcelableExtra(
            SafeService.class.getCanonicalName());
        if (service != null) {
            Log.d(TAG, "Got service info from parent activity's intent");
        } else {
            Log.w(TAG, "Failed to get service info from parent activity's intent");
        }
        Log.d(TAG, "Fragment created");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        fragmentView = inflater.inflate(R.layout.fragment_service, container, false);
        updateView();
        Log.d(TAG, "Fragment view created");
        return fragmentView;
    }

    /**
     * Update the UI.
     */
    private void updateView() {
        if (service != null) {
            String serviceName;
            final Uri serviceAddress;
            String serviceHost;

            if ((serviceName = service.getName()) == null) {
                serviceName = getString(R.string.unknown_service_name);
            }

            if ((serviceAddress = service.getAddress()) == null ||
                (serviceHost = serviceAddress.getHost()) == null) {
                serviceHost = getString(R.string.unknown_service_host);
            }

            ((TextView) fragmentView.findViewById(
                R.id.fragment_service__text1)).setText(serviceName);
            ((TextView) fragmentView.findViewById(
                R.id.fragment_service__text2)).setText(serviceHost);

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
                R.id.fragment_service__logo)).setImageResource(resource);
        }
    }
}
