package org.mypico.android.qrscanner;

import android.graphics.Point;
import android.hardware.Camera;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;

import com.google.zxing.Result;

import org.mypico.android.R;

/**
 * Wrapper class for all the QR code scanner stuff.
 * <p>
 * Usage is simple: create an instance of this class inside your {@code Activity}, and invoke
 * {@link #start()} to start scanning and {@link #stop()} to stop scanning. A good place to do this
 * is in the {@code Activity}'s {@code onResume} and {@code onPause} methods, respectively. Before
 * calling {@link #start()} you must also call {@link #setDisplay(Display)}, otherwise the camera
 * preview image will likely be squished or rotated or both.
 * <p>
 * For the scanner to be useful you should also set listeners using
 * {@link #setQRCodeFoundListener(OnQRCodeFoundListener)}, which will receive callbacks when a QR
 * code is found, and optionally {@link #setPreviewStartedListener(OnPreviewStartedListener)},
 * which will receive callbacks when the camera preview has started.
 * <p>
 * If once a QR code is found the callback {@link OnQRCodeFoundListener#onQRCodeFound(Result)}
 * returns {@code true} (indicating that the code has been handled), the scanner will stop searching
 * for further QR codes until you call {@link #findMore()}. The camera's preview will remain live
 * until you call {@link #stop()}.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see OnQRCodeFoundListener
 * @see OnPreviewStartedListener
 */
@SuppressWarnings("deprecation")
public class QRCodeScanner {
    private static final String TAG = QRCodeScanner.class.getSimpleName();

    Camera camera = null;
    CameraView cameraView;
    ScannerThread scannerThread;
    ResultHandler resultHandler;
    boolean started = false;

    /**
     * Constructor that allows a {@link CameraView} to be associated with the scanner.
     *
     * @param view The view for the scanner to use.
     */
    public QRCodeScanner(CameraView view) {
        resultHandler = new ResultHandler();
        this.cameraView = view;
    }

    /**
     * Constructor that allows a {@link CameraView} to be associated with the scanner, along with
     * a callback that will be triggered when a QR code is scanned.
     *
     * @param view     The view for the scanner to use.
     * @param listener Callback to trigger when a QR code is read.
     */
    public QRCodeScanner(CameraView view, OnQRCodeFoundListener listener) {
        this(view);
        setQRCodeFoundListener(listener);
    }

    /**
     * Set callback listener for when a QR code is found.
     * Note that once a QR code is found, the scanner will stop looking. Call findMore to start it
     * again.
     *
     * @param listener The listener.
     */
    public void setQRCodeFoundListener(OnQRCodeFoundListener listener) {
        resultHandler.setQRCodeFoundListener(listener);
    }

    /**
     * Set callback listener for when the camera preview is started. Can be used to hide a throbber.
     *
     * @param listener The listsner.
     */
    public void setPreviewStartedListener(OnPreviewStartedListener listener) {
        resultHandler.setPreviewStartedListener(listener);
    }

    /**
     * Turn on the camera preview and start looking for QR codes.
     * <p>
     * This method returns immediately, although the camera may take a couple of seconds to start.
     * You can be notified of when the preview (and therefore scanning) has started by setting a
     * listener using {@link #setPreviewStartedListener(OnPreviewStartedListener)}.
     */
    public synchronized void start() {
        if (started)
            return;
        started = true;

        scannerThread = new ScannerThread(resultHandler);
        scannerThread.start();

        cameraView.setResultHandler(resultHandler);
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusCamera();
            }
        });

        new CameraStarterThread().start();

    }

    /**
     * Stop scanning and turn off the camera preview.
     */
    public synchronized void stop() {
        if (!started)
            return;
        started = false;

        // turn off the camera
        cameraView.setCamera(null);
        cameraView.setOnClickListener(null);
        resultHandler.setCameraStuff(null, null);
        if (camera != null) {
            camera.release();
            camera = null;
        }

        // kill the scanner thread
        if (scannerThread != null) {
            Message.obtain(scannerThread.getHandler(), R.id.quit).sendToTarget();
            try {
                scannerThread.join(500);
            } catch (InterruptedException ignored) {
            }
            scannerThread = null;
        }

        // flush messages
        resultHandler.removeMessages(R.id.codeFound);
        resultHandler.removeMessages(R.id.codeNotFound);
    }

    /**
     * Once a QR code is found, the scanner will stop looking for more. Call this method to start
     * it again.
     */
    public void findMore() {
        Message.obtain(resultHandler, R.id.codeNotFound).sendToTarget();
    }


    /**
     * Specify the display that the camera preview will be shown on, so that we have its dimensions
     * and relative orientation so that the picture is the right way up and has the
     * correct aspect ratio.
     * Call before calling {@link #start()}.
     *
     * @param display The display, for example, {@code getWindowManager().getDefaultDisplay()}.
     */
    public void setDisplay(Display display) {
        final Point size = new Point();
        display.getSize(size);
        cameraView.setDisplaySize(size);
        cameraView.setDisplayRotation(display.getRotation());
    }

    /**
     * Trigger the camera's auto-focus.
     */
    synchronized void focusCamera() {
        if (camera == null)
            return;

        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                Log.d(TAG, "Auto-focus complete");
            }
        });
    }

    /**
     * Thread that opens the camera, since this operation blocks for a second or so.
     */
    private class CameraStarterThread extends Thread {
        @Override
        public void run() {
            // try to get the camera object
            Log.d("CameraStarterThread", "Opening camera");
            try {
                camera = Camera.open();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // mutex with the start and stop methods
            synchronized (QRCodeScanner.this) {
                if (camera == null) {
                    Log.e(TAG, "Could not get camera instance");
                    return;
                }
                // if the scanner has been stopped since opening the camera, dispose of the camera
                if (!started) {
                    camera.release();
                    camera = null;
                    return;
                }
            }

            Log.d("CameraStarterThread", "success");

            // link up the ResultHandler and wait for the ScannerThread to start
            resultHandler.setCameraStuff(camera,
                new PreviewFrameDispatcher(scannerThread.getHandler()));

            // give the CameraView the camera, which will start the preview
            cameraView.setCamera(camera);

        }
    }

}
