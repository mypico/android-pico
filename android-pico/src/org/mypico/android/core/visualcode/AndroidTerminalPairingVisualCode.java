package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.NonceParcel;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.TerminalPairingVisualCode;

import static org.mypico.android.core.VisualCodeIntentGenerator.NONCE;
import static org.mypico.android.core.VisualCodeIntentGenerator.TERMINAL_NAME;

/**
 * {@link TerminalPairingVisualCode} with additional functionality used by the app.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AndroidTerminalPairingVisualCode extends TerminalPairingVisualCode
    implements AndroidVisualCode {
    private static final String TAG = "AndroidVisualCode";

    public CodeType getCodeType() {
        return CodeType.TERMINAL_PAIRING;
    }

    /**
     * Create the {@link Intent} that will handle a {@link TerminalPairingVisualCode} when it gets
     * scanned.
     * <p>
     * Next activity: None (this activity will just finish)
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
        Log.d(TAG, "Creating Intent from TerminalPairingVisualCode instance");

        final Intent intent = new Intent();
        if (!startedForResult) {
            // No sensible target activity for this type of code
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
