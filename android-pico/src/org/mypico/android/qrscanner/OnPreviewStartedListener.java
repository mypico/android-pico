package org.mypico.android.qrscanner;

/**
 * Listener for when the camera preview has started. This can be used, for instance, to hide a
 * loading indicator that is initially visible.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public interface OnPreviewStartedListener {
    /**
     * Called when the camera preview has been started.
     */
    void onPreviewStarted();
}
