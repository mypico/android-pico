package org.mypico.android.qrscanner;

import com.google.zxing.Result;

/**
 * Listener for when a QR code is detected.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public interface OnQRCodeFoundListener {
    /**
     * Called when a QR code is detected.
     *
     * @param result Info about the QR code.
     * @return True if handled successfully, false otherwise. If true is returned, scanning for
     * more QR codes will stop.
     */
    boolean onQRCodeFound(Result result);
}
