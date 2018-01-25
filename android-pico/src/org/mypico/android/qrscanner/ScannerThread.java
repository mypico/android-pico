package org.mypico.android.qrscanner;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the hard work analysing camera frames to find QR codes. The actual work is
 * implemented in the ScannerHandler, to which frames get send via Messages for processing. The
 * thread may be terminated by sending a quit Message (R.id.quit).
 * <p>
 * Based on {@code com.google.zxing.client.android.DecodeThread}.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
class ScannerThread extends Thread {
    private static final String TAG = ScannerThread.class.getSimpleName();

    ScannerHandler handler;
    Handler resultHandler;
    final CountDownLatch handlerInitLatch;

    public ScannerThread(Handler resultHandler) {
        this.resultHandler = resultHandler;
        handlerInitLatch = new CountDownLatch(1);
    }

    /**
     * Note: Blocks until the handler is ready.
     *
     * @return The {@link Handler} to which frame messages should be sent.
     * @see ScannerHandler
     */
    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new ScannerHandler(resultHandler);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
