package org.mypico.android.qrscanner;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.mypico.android.R;

/**
 * {@link Handler} that receives and processes camera frames, running in the {@link ScannerThread}.
 * Once a frame has been processed the result (or lack of) is sent to the {@link ResultHandler}.
 * <p>
 * This {@code Handler} handles the following messages:
 * <ul>
 * <li>{@code R.id.handleFrame}: a frame has been obtained for processing. The image data should be
 * passed in {@link Message#obj}, while its width and height should be passed in
 * {@link Message#arg1} and {@link Message#arg2}, respectively.</li>
 * <li>{@code R.id.quit}: tell the parent thread's {@link Looper} to quit.</li>
 * </ul>
 * <p>
 * Based on {@code com.google.zxing.client.android.DecodeHandler}.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
class ScannerHandler extends Handler {
    private static final String TAG = ScannerHandler.class.getSimpleName();

    QRCodeReader qrCodeReader;
    Handler resultHandler;
    boolean running = true;

    public ScannerHandler(Handler resultHandler) {
        this.resultHandler = resultHandler;
        qrCodeReader = new QRCodeReader();
    }

    @Override
    public void handleMessage(Message msg) {
        if (!running)
            return;

        switch (msg.what) {
            case R.id.handleFrame:
                if (msg.obj instanceof byte[])
                    handleFrame((byte[]) msg.obj, msg.arg1, msg.arg2);
                break;

            case R.id.quit:
                running = false;
                Looper.myLooper().quit();
                break;

            default:
                super.handleMessage(msg);
        }
    }

    /**
     * Process a camera frame that was posted to this {@code Handler} to see whether it contains a
     * QR code, and, if it does, post a message to the {@link ResultHandler} containing the
     * scanner's {@link Result}.
     *
     * @param frame  The raw frame bytes.
     * @param width  The frame's width in pixels.
     * @param height The frame's height in pixels.
     */
    void handleFrame(byte[] frame, int width, int height) {
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(frame,
            width, height, 0, 0, width, height, false);
        BinaryBitmap bmp = new BinaryBitmap(new HybridBinarizer(source));
        Result result = null;

        try {
            result = qrCodeReader.decode(bmp);
        } catch (FormatException ignored) {
            //Log.e(TAG, "Found code but could not read format");
        } catch (ChecksumException e) {
            Log.e(TAG, "Found code but checksum failed");
        } catch (NotFoundException ignored) {
        } finally {
            qrCodeReader.reset();
        }

        if (result != null) {
            Log.d(TAG, "Found valid code");
            if (resultHandler != null)
                Message.obtain(resultHandler, R.id.codeFound, result).sendToTarget();
        } else {
            if (resultHandler != null)
                Message.obtain(resultHandler, R.id.codeNotFound).sendToTarget();
        }

    }

}
