package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.SafeService;
import org.mypico.android.pairing.NewLensPairingActivity;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.LensPairingVisualCode;

import static org.mypico.android.core.VisualCodeIntentGenerator.SERVICE;

/**
 * {@link LensPairingVisualCode} with additional functionality used by the app.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AndroidLensPairingVisualCode extends LensPairingVisualCode
    implements AndroidVisualCode {
    private static final String TAG = "AndroidVisualCode";

    public CodeType getCodeType() {
        return CodeType.PAIRING;
    }

    /**
     * Create the {@link Intent} that will handle a {@link LensPairingVisualCode} when it gets
     * scanned.
     * <p>
     * Next activity: {@link NewLensPairingActivity}
     * <p>
     * Extras:
     * <ul>
     * <li>{@link VisualCodeIntentGenerator#SERVICE SERVICE}</li>
     * <li>{@link VisualCodeIntentGenerator#CREDENTIALS CREDENTIALS}</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent createIntent(Context context, boolean startedForResult)
        throws InvalidVisualCodeException {
        Log.d(TAG, "Creating Intent from LensPairingVisualCode instance");

        final Intent intent = new Intent();
        if (!startedForResult) {
            intent.setClass(context, NewLensPairingActivity.class);
        }

        // Add service from visual code to intent
        SafeService service = SafeService.fromVisualCode(this);
        intent.putExtra(SERVICE, service);

        VisualCodeIntentGenerator.putTerminalDetailsIfPresent(intent, this);

        return intent;
    }

}
