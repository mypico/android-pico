package org.mypico.android.qrscanner;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shows the camera preview.
 * <p>
 * To get notified when the preview starts, call {@link #setResultHandler(ResultHandler)} before
 * setting the camera. The {@link ResultHandler}'s {@link OnPreviewStartedListener} will be notified
 * when the preview is started.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class CameraView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = CameraView.class.getSimpleName();

    /**
     * How close an apsect ratio must be to another to be considered the same.
     */
    private static final float ASPECT_RATIO_EPSILON = 0.01f;
    /**
     * The largest number of pixels allowed in the preview image, to limit the time taken to
     * process frames. For reference, 1 MP is approximately 1280x720.
     */
    private static final int PREVIEW_MAX_PIXELS = 1000000;

    SurfaceHolder holder;
    Camera camera;
    ResultHandler handler;
    Point displaySize;
    int rotation = 90;
    boolean hasSurface = false;
    boolean previewStarted = false;

    /**
     * Constructor that allows the UI context to be set for the view.
     *
     * @param context The context.
     */
    public CameraView(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor that allows the UI context and attributes to be set for the view.
     *
     * @param context The context.
     * @param attrs   Attributes to set.
     */
    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor that allows the UI context, attributes and style attributes to be set for the
     * view.
     *
     * @param context      The context.
     * @param attrs        Attributes to set.
     * @param defStyleAttr Style attributes to set.
     */
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initialise the view.
     */
    private void init() {
        holder = getHolder();
        holder.addCallback(this);
    }

    /**
     * Set the {@link ResultHandler}. It will be sent a message when the camera preview is started.
     *
     * @param handler The {@link ResultHandler}.
     */
    public void setResultHandler(ResultHandler handler) {
        this.handler = handler;
    }

    /**
     * Set the {@link Camera} that this {@code View} will display. Its parameters will be set as
     * appropriate and then the preview started (provided the View is ready).
     *
     * @param newCamera The camera. You may pass {@code null} to turn off the current camera and
     *                  null its reference.
     */
    public synchronized void setCamera(Camera newCamera) {
        if (camera != null) {
            try {
                camera.stopPreview();
            } catch (Exception ignored) {
            }
            previewStarted = false;
        }

        camera = newCamera;
        if (camera == null)
            return;

        setCameraParameters();

        if (hasSurface)
            startCamera(holder);
    }

    /**
     * Configure the camera the way we want it. This involves choosing the best preview resolution
     * and accounting for the display's rotation. Additionally, we try to enable autofocus and use
     * the barcode scene mode, if the device supports these.
     * <p>
     * TODO: ZXing also configures the following parameters, although not all may be necessary:
     * - Exposure
     * - Torch/flash
     * - Video stabilisation
     * - Framerate
     * Also worth considering that may be easier to include the ZXing {@code android-core} library
     * too and use the {@code CameraConfigurationUtils} it provides.
     */
    void setCameraParameters() {
        final Camera.Parameters params = camera.getParameters();
        if (params == null) {
            Log.e(TAG, "No camera parameters available to set");
            return;
        }

        // set the camera resolution
        final Camera.Size bestSize = chooseBestPreviewSize(params);
        Log.d(TAG, "Chosen best image size: " + bestSize.width + "x" + bestSize.height);
        params.setPreviewSize(bestSize.width, bestSize.height);

        // pick focus mode
        final List<String> focusModes = params.getSupportedFocusModes();
        String focusMode = null;
        Log.d(TAG, "Default focus mode is " + params.getFocusMode());
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_MACRO;
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
        } else {
            Log.w(TAG, "Camera does not support auto or macro focus modes; default is " +
                params.getFocusMode());
        }
        if (focusMode != null && !focusMode.equals(params.getFocusMode()))
            params.setFocusMode(focusMode);

        // set the scene mode to optimise for barcode scanning
        final List<String> sceneModes = params.getSupportedSceneModes();
        if (Camera.Parameters.SCENE_MODE_BARCODE.equals(params.getSceneMode()) &&
            sceneModes.contains(Camera.Parameters.SCENE_MODE_BARCODE))
            params.setSceneMode(Camera.Parameters.SCENE_MODE_BARCODE);

        // apply the updated parameters
        camera.setParameters(params);

        // adjust camera rotation for display rotation
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        Log.d(TAG, "Orientation: " + info.orientation + " - " + rotation);
        int orientation;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            orientation = (info.orientation + rotation) % 360;
            orientation = (360 - orientation) % 360;
        } else {
            orientation = (360 + info.orientation - rotation) % 360;
        }
        camera.setDisplayOrientation(orientation);

    }

    /**
     * Pick the "best" preview size - one that has the same aspect ratio as the device's screen but
     * is also a decent resolution, ideally between 480 and 720p.
     * <p>
     * The best preview resolution is chosen as follows: out of all possible sizes, find the largest
     * of the correct aspect ratio that has fewer than {@link #PREVIEW_MAX_PIXELS} pixels. If none
     * of the sizes is of the correct aspect ratio, we choose whichever has the closest.
     *
     * @param params The camera's parameters from which possible preview sizes will be obtained.
     * @return The best preview size.
     */
    private Camera.Size chooseBestPreviewSize(Camera.Parameters params) {
        Camera.Size result = null;

        Log.d(TAG, "Target size " + displaySize.x + "x" + displaySize.y);
        final float targetRatio = landscapeRatio(displaySize.x, displaySize.y);
        // get all possible preview sizes
        final List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        // sort them by area (ascending)
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                final int areaA = a.width * a.height;
                final int areaB = b.width * b.height;
                return areaA - areaB;
            }
        });
        // search them for sizes of the right aspect ratio
        for (Camera.Size size : sizes) {
            final float ratio = landscapeRatio(size.width, size.height);
            if (Math.abs(ratio - targetRatio) < ASPECT_RATIO_EPSILON) {
                // it's the right shape: take the largest that's smaller than PREVIEW_MAX_PIXELS
                Log.d(TAG, "Possible compatible size: " + size.width + "x" + size.height);
                if (result != null && size.width * size.height > PREVIEW_MAX_PIXELS) {
                    Log.d(TAG, "Maximum pixels exceeded: " + (size.width * size.height));
                    break;
                }
                result = size;
            }
        }
        // check that a suitable size was found
        if (result == null) {
            // there was no size of the right aspect ratio, so try to find a closest match
            float minDelta = 1000f;
            for (Camera.Size size : sizes) {
                final float ratio = landscapeRatio(size.width, size.height);
                final float delta = Math.abs(ratio - targetRatio);
                // if it's roughly the same or better...
                if (delta < minDelta + ASPECT_RATIO_EPSILON) {
                    minDelta = delta;
                    result = size;
                }
            }
        }

        return result;
    }

    /**
     * @return The aspect ratio between {@code width} and {@code height}, turning to landscape if
     * necessary. The result is always >= 1.
     */
    private float landscapeRatio(int width, int height) {
        return (width > height) ?
            width / (float) height :
            height / (float) width;
    }

    /**
     * Specify the size/resolution of the display that the camera preview will be shown on. This
     * will be used to select the camera's aspect ratio. Use {@link Display#getSize(Point)}.
     *
     * @param size The display size.
     */
    public void setDisplaySize(Point size) {
        displaySize = new Point(size);
    }

    /**
     * Specify the rotation of the display that the camera preview will be shown on. Use
     * {@link Display#getRotation()}.
     *
     * @param rotation The display's rotation.
     */
    public void setDisplayRotation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                this.rotation = 0;
                break;
            case Surface.ROTATION_90:
                this.rotation = 90;
                break;
            case Surface.ROTATION_180:
                this.rotation = 180;
                break;
            case Surface.ROTATION_270:
                this.rotation = 270;
                break;
            default:
                // There is a comment in the ZXing client source that says they've sometimes seen an
                // invalid value passed in, and so also have a case for (negative) multiples of 90.
                // (see com.google.zxing.client.android.camera.CameraConfigurationManager)
                int r = rotation + 360;
                if (r % 90 == 0) {
                    this.rotation = rotation % 360;
                } else {
                    throw new IllegalArgumentException("Invalid display rotation: " + rotation);
                }
        }
    }

    /**
     * Start the camera preview. This function returns immediately; the {@link ResultHandler} will
     * be notified once the preview has actually started.
     *
     * @param holder Where to display the camera preview.
     */
    void startCamera(SurfaceHolder holder) {
        if (previewStarted)
            return;
        previewStarted = true;
        Log.d(TAG, "Starting camera preview");
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.e(TAG, "Failed to set camera preview display");
            e.printStackTrace();
            return;
        }
        Log.d(TAG, "Spawned preview starter thread; preview will start imminently");
        new Thread() {
            @Override
            public void run() {
                camera.startPreview();
                if (handler != null)
                    handler.onPreviewStarted();
                Log.d(TAG, "Preview started");
            }
        }.start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        hasSurface = true;
        if (camera != null)
            startCamera(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        hasSurface = false;
    }

}
