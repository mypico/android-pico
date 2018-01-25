package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.NonceParcel;
import org.mypico.android.delegate.NewDelegatePairingActivity;
import org.mypico.jpico.visualcode.DelegatePairingVisualCode;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;

import static org.mypico.android.core.VisualCodeIntentGenerator.NONCE;
import static org.mypico.android.core.VisualCodeIntentGenerator.TERMINAL_NAME;

/**
 * {@link DelegatePairingVisualCode} with additional functionality used by the app.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AndroidDelegatePairingVisualCode extends DelegatePairingVisualCode
    implements AndroidVisualCode {
    private static final String TAG = "AndroidVisualCode";

    public CodeType getCodeType() {
        return CodeType.PAIRING;
    }

    /**
     * Create the {@link Intent} that will handle a {@link DelegatePairingVisualCode} when it gets
     * scanned.
     * <p>
     * Next activity: {@link NewDelegatePairingActivity}
     * <p>
     * Extras:
     * <ul>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_ADDRESS TERMINAL_ADDRESS}</li>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_COMMITMENT TERMINAL_COMMITMENT}</li>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_NAME TERMINAL_NAME}</li>
     * <li>{@link VisualCodeIntentGenerator#NONCE NONCE}</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent createIntent(Context context, boolean startedForResult)
        throws InvalidVisualCodeException {
        Log.d(TAG, "Creating Intent from DelegatePairingVisualCode instance");

        final Intent intent = new Intent();
        if (!startedForResult) {
            intent.setClass(context, NewDelegatePairingActivity.class);
        }

        // Add terminal details
        VisualCodeIntentGenerator.putTerminalDetails(intent, this);

        // Add terminal name (not included above)
        intent.putExtra(TERMINAL_NAME, getTerminalName());

        // Add nonce
        intent.putExtra(NONCE, new NonceParcel(getNonce()));

        return intent;
    }

}
