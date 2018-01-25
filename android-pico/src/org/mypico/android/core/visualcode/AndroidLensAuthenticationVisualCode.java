package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.pairing.AuthenticateActivity;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.LensAuthenticationVisualCode;

/**
 * {@link LensAuthenticationVisualCode} with additional functionality used by the app.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AndroidLensAuthenticationVisualCode extends LensAuthenticationVisualCode
    implements AndroidVisualCode {
    private static final String TAG = "AndroidVisualCode";

    public CodeType getCodeType() {
        return CodeType.AUTH;
    }

    /**
     * Create the {@link Intent} that will handle a {@link LensAuthenticationVisualCode} when it
     * gets scanned.
     * <p>
     * Next activity: {@link AuthenticateActivity}
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
        Log.d(TAG, "Creating Intent from LensAuthenticationVisualCode instance");

        final Intent intent = new Intent();
        if (!startedForResult) {
            intent.setClass(context, AuthenticateActivity.class);
        }

        // Add the terminal details to the next intent (if present)
        VisualCodeIntentGenerator.putTerminalDetailsIfPresent(intent, this);

        return intent;
    }

}
