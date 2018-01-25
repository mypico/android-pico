package org.mypico.android.qrscanner;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;

import com.google.zxing.Result;

import org.mypico.android.R;

/**
 * Handler that receives messages from {@link ScannerHandler} after a frame is processed. If no QR
 * code was found, another frame is requested, otherwise the {@link OnQRCodeFoundListener} is
 * notified.
 * <p>
 * This {@code Handler} handles the following messages:
 * <ul>
 * <li>{@code R.id.previewStarted}: posted by the {@link #onPreviewStarted()} method to trigger the
 * {@link OnPreviewStartedListener} on the main thread.</li>
 * <li>{@code R.id.codeFound}: posted by the {@link ScannerHandler} after processing a frame
 * containing a valid QR code.</li>
 * <li>{@code R.id.codeNotFound}: </li>
 * </ul>
 * <p>
 * According to the Android Docs the camera is not thread-safe, so this handler should run in the
 * same thread that set up the camera.
 * <p>
 * Based on {@code com.google.zxing.client.android.CaptureActivityHandler}.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
class ResultHandler extends Handler {

    Camera camera;
    Camera.PreviewCallback callback;
    OnQRCodeFoundListener foundCodeListener;
    OnPreviewStartedListener previewListener;

    /**
     * Set a listener that will be triggered when a QR code is found.
     *
     * @param listener The listener.
     */
    public void setQRCodeFoundListener(OnQRCodeFoundListener listener) {
        foundCodeListener = listener;
    }

    /**
     * Set a listener that will be triggered when the camera's preview is started.
     *
     * @param listener The listener.
     */
    public void setPreviewStartedListener(OnPreviewStartedListener listener) {
        previewListener = listener;
    }

    /**
     * Set camera stuff.
     *
     * @param camera   The Camera object we're getting images from
     * @param callback The callback for camera frames that forwards them for processing.
     */
    public void setCameraStuff(Camera camera, Camera.PreviewCallback callback) {
        this.camera = camera;
        this.callback = callback;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case R.id.previewStarted:
                if (previewListener != null)
                    previewListener.onPreviewStarted();
                break;

            case R.id.codeFound:
                // found a code
                Result result = (Result) msg.obj;
                boolean handled = false;
                if (foundCodeListener != null)
                    handled = foundCodeListener.onQRCodeFound(result);
                // request another frame only if it wasn't handled
                if (!handled)
                    requestPreviewFrame();
                break;

            case R.id.codeNotFound:
                // no code found, request next frame
                requestPreviewFrame();
                break;

            default:
                super.handleMessage(msg);
        }
    }

    /**
     * Basically requests a new frame from the camera, which will be sent to the callback passed to
     * {@link #setCameraStuff(Camera, Camera.PreviewCallback)}.
     */
    void requestPreviewFrame() {
        if (camera != null)
            camera.setOneShotPreviewCallback(callback);
    }

    /**
     * Called when the camera preview has been successfully started. Request the first frame for
     * processing, and send a message to this handler (since this method gets called from a
     * different thread) to trigger the {@link OnPreviewStartedListener} callback.
     */
    public void onPreviewStarted() {
        Message.obtain(this, R.id.previewStarted).sendToTarget();
        requestPreviewFrame();
    }

}
