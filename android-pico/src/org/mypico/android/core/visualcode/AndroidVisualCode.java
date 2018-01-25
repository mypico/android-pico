package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.VisualCode;

/**
 * Provides common methods to be implemented in the app's {@link VisualCode} subclasses.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public interface AndroidVisualCode {

    /**
     * @return What type of visual code this is.
     */
    CodeType getCodeType();

    /**
     * Create the {@link Intent} that will handle this type of code when it gets scanned.
     *
     * @param context          App {@link Context} for {@link Intent} creation.
     * @param startedForResult Whether the calling Activity was started for a result, and therefore
     *                         whether the Intent returned
     * @return A new {@link Intent} that starts the Activity that handles this {@link VisualCode},
     * or the only data if {@code startedForResult} is {@code true}.
     * @throws InvalidVisualCodeException if the code is invalid in some respect (e.g. invalid
     *                                    signature).
     */
    @NonNull
    Intent createIntent(Context context, boolean startedForResult)
        throws InvalidVisualCodeException;

}
