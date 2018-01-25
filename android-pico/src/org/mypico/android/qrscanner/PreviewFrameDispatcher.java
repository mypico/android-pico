package org.mypico.android.qrscanner;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;

import org.mypico.android.R;

/**
 * Implements the {@link Camera.PreviewCallback} interface to receive frames from the camera preview
 * at regular intervals (see {@link ResultHandler#requestPreviewFrame()}). These are dispatched
 * through messages to a {@link ScannerHandler} for processing (see
 * {@link ScannerHandler#handleFrame(byte[], int, int)}).
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see ResultHandler
 * @see ScannerHandler
 */
class PreviewFrameDispatcher implements Camera.PreviewCallback {

    Handler handler;

    /**
     * @param handler The Handler that will receive messages containing frames.
     */
    public PreviewFrameDispatcher(Handler handler) {
        this.handler = handler;
    }

    /**
     * Called by the camera at some point after requesting a frame.
     *
     * @param data   The preview frame image data.
     * @param camera The {@link Camera} object that produced the frame.
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        // get the preview size
        Camera.Size size;
        try {
            Camera.Parameters params = camera.getParameters();
            size = params.getPreviewSize();
        } catch (RuntimeException e) {
            // On one occasion this was called after the camera had been released, causing
            // "java.lang.RuntimeException: Method called after release()".
            // In this case we just ignore it.
            return;
        }

        // pack into a message and send to the handler
        Message.obtain(handler, R.id.handleFrame, size.width, size.height, data).sendToTarget();
    }

}
