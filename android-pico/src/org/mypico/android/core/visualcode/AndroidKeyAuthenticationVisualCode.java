package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.SafeService;
import org.mypico.android.pairing.ChooseKeyPairingActivity;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.KeyAuthenticationVisualCode;

import static org.mypico.android.core.VisualCodeIntentGenerator.SERVICE;

/**
 * {@link KeyAuthenticationVisualCode} with additional functionality used by the app.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AndroidKeyAuthenticationVisualCode extends KeyAuthenticationVisualCode
    implements AndroidVisualCode {
    private static final String TAG = "AndroidVisualCode";

    public CodeType getCodeType() {
        return CodeType.AUTH;
    }

    /**
     * Create the {@link Intent} that will handle a {@link KeyAuthenticationVisualCode} when it
     * gets scanned.
     * <p>
     * Next activity: {@link ChooseKeyPairingActivity}
     * <p>
     * Extras:
     * <ul>
     * <li>{@link VisualCodeIntentGenerator#SERVICE SERVICE}</li>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_ADDRESS TERMINAL_ADDRESS} (optional)</li>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_COMMITMENT TERMINAL_COMMITMENT} (optional)</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent createIntent(Context context, boolean startedForResult)
        throws InvalidVisualCodeException {
        Log.d(TAG, "Creating Intent from KeyAuthenticationVisualCode instance");

        final Intent intent = new Intent();
        if (!startedForResult) {
            // Set target activity
            intent.setClass(context, ChooseKeyPairingActivity.class);
        }

        // Add service from visual code to intent
        SafeService service = SafeService.fromVisualCode(this);
        intent.putExtra(SERVICE, service);

        // Add terminal details to intent
        VisualCodeIntentGenerator.putTerminalDetailsIfPresent(intent, this);

        // Add extra data to intent
        intent.putExtra("myExtraData", getExtraData());
        Log.d(TAG, "Extra data straight from visual code was " + getExtraData());
        // Return complete intent
        return intent;
    }

}
