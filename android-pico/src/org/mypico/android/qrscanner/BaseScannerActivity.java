package org.mypico.android.qrscanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.google.zxing.Result;

import org.mypico.android.R;

/**
 * Extendable {@link Activity} base class for a QR code scanner.
 * <p>
 * The layout for this {@code Activity} must include a {@link CameraView} with @+id/viewfinder,
 * which should fill the whole screen. Additionally, if there is a {@code View} with @+id/loading,
 * this will be hidden once the camera preview is started.
 * <p>
 * To provide a better experience, the screen will not automatically dim or turn off while this
 * {@code Activity} is open, and the window is set to use a fullscreen layout. To reinforce this you
 * should set {@code windowTranslucentStatus=true} in the {@code Activity}'s theme, and either use
 * no Action Bar or a transparent overlayed Action Bar.
 * <p>
 * Once a QR code has been scanned, the activity will finish and return the QR code's contents in
 * the {@link #SCAN_RESULT_EXTRA} extra of its result {@code Intent}.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class BaseScannerActivity extends Activity
    implements OnQRCodeFoundListener, OnPreviewStartedListener {

    /**
     * Intent action to scan a QR code and then finish. The contents of the QR code will be
     * returned in the SCAN_RESULT_EXTRA extra.
     */
    public static final String ACTION_SCAN =
        BaseScannerActivity.class.getCanonicalName() + ".SCAN";
    /**
     * Extra that will be put in the Activity's result Intent when started with ACTION_SCAN, and
     * will contain the contents of the scanned QR code.
     */
    public static final String SCAN_RESULT_EXTRA =
        BaseScannerActivity.class.getCanonicalName() + ".RESULT";

    QRCodeScanner scanner;
    Handler delayedRunnableHandler;
    View loadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // keep the screen on while the scanner is open
        final Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // request that the layout covers the whole screen, even if status bars are present, so that
        // the camera preview does not get stretched
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        delayedRunnableHandler = new Handler();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        postSetContentView();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        postSetContentView();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        postSetContentView();
    }

    /**
     * After setting the {@code Activity}'s content, find the viewfinder and create the
     * {@link QRCodeScanner} instance.
     */
    private void postSetContentView() {
        // don't create two QRCodeScanners
        if (scanner != null) {
            scanner.stop();
            scanner = null;
        }

        // find the viewfinder
        final View view = findViewById(R.id.viewfinder);
        if (view == null || !(view instanceof CameraView)) {
            throw new IllegalStateException(
                "The content view you set doesn't contain a CameraView with @+id/viewfinder.");
        }
        // find the loading view (if there is one)
        loadingView = findViewById(R.id.loading);

        // create the QR code scanner and tie in with this Activity
        scanner = new QRCodeScanner((CameraView) view, this);
        scanner.setPreviewStartedListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (loadingView != null)
            loadingView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (scanner == null)
            throw new IllegalStateException("You need to call setContentView in onCreate.");

        scanner.setDisplay(getWindowManager().getDefaultDisplay());
        scanner.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanner.stop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // the Back button cancels the scanner
                setResult(RESULT_CANCELED);
                finish();
                return true;

            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
                // gobble these so they don't open the camera
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPreviewStarted() {
        // hide the loading spinner
        if (loadingView != null)
            loadingView.setVisibility(View.GONE);
    }

    @Override
    public boolean onQRCodeFound(Result result) {
        // give the user some feedback that the code was scanned
        // (the following avoids having to add the VIBRATE permission)
        getWindow().getDecorView().performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY |
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        // send the QR code text to the calling activity
        setResultAndFinish(result.getText());
        return true;
    }

    /**
     * Put the QR code text in the result Intent in a way compatible with the ZXing's
     * {@code CaptureActivity}, and finish this Activity.
     *
     * @param text The QR code text.
     */
    private void setResultAndFinish(String text) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(SCAN_RESULT_EXTRA, text);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /**
     * After the given delay, continue searching for QR codes.
     * <p>
     * The expected use case is that, having scanned a QR code, it is not valid for whatever reason,
     * and you want to inform the user and let them scan another. However, simply returning
     * {@code false} from {@link OnQRCodeFoundListener#onQRCodeFound(Result)} will immediately scan
     * the same QR code again. Instead you can return {@code true} after calling
     * {@code restartPreviewAfterDelay}, which will pause QR code scanning for the specified
     * time.
     * <p>
     * This should be compatible with the method of the same name in Barcode Scanner's
     * {@code CaptureActivity}.
     *
     * @param delay The delay, in milliseconds.
     */
    public void restartPreviewAfterDelay(long delay) {
        delayedRunnableHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanner != null)
                    scanner.findMore();
            }
        }, delay);
    }

}
