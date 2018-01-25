package org.mypico.android.delegate;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.pairing.LensPairingDetailActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.jpico.data.pairing.Pairing;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.zxing.common.BitMatrix;

/**
 * Activity where a user can transfer a {@link Pairing} with a given service from one Pico to another.
 * The {@link Pairing} is sent via QR code and scanned to be installed on another device.
 *
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class DelegateActivity extends Activity {
    /**
     * Use to log output messages to the LogCat console
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DelegateActivity.class.getSimpleName());

    static final String TERMINAL_SHARED_KEY = "SECRET_KEY";

    /**
     * Extended data for the intent. Stores the details of the {@link Pairing} for delegation as
     * a SafeLensPairing structure (using the Parcelable interface)
     */
    public static final String PAIRING =
        LensPairingDetailActivity.class.getCanonicalName() + "pairing";

    /**
     * Object for storing the {@link Pairing} to be delegated
     */
    private SafeLensPairing pairing;

    private static final String TAG_DELEGATE_TASK_FRAGMENT = "delegate_task_fragment";
    DelegateTaskFragment taskFragment;


    /**
     * Display the QR code in the user interface
     *
     * @param qrbitmap graphic of the QR code to display
     */
    public void setQRCode(Bitmap qrbitmap) {
        if (qrbitmap != null) {
            // Show the image in the activity interface
            ImageView qrimage = (ImageView) (findViewById(R.id.qrdelegate));
            qrimage.setImageBitmap(qrbitmap);
            showQRCode();
        }
    }

    /**
     * Show the delegation QR code on the page.
     */
    public void showQRCode() {
        // qrdelegate_spinner
        ImageView qrimage = (ImageView) (findViewById(R.id.qrdelegate));
        qrimage.setVisibility(View.VISIBLE);

        ProgressBar spinner = (ProgressBar) (findViewById(R.id.qrdelegate_spinner));
        spinner.setVisibility(View.GONE);
    }

    /**
     * Hide the delegation QR code from the page.
     */
    public void hideQRCode() {
        // qrdelegate_spinner
        ImageView qrimage = (ImageView) (findViewById(R.id.qrdelegate));
        qrimage.setVisibility(View.GONE);

        ProgressBar spinner = (ProgressBar) (findViewById(R.id.qrdelegate_spinner));
        spinner.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delegate);

        hideQRCode();

        // Get safe lens pairing from received intent
        if (getIntent().hasExtra(PAIRING)) {
            pairing = (SafeLensPairing) getIntent().getParcelableExtra(PAIRING);
        } else {
            LOGGER.warn("safe pairing extra missing, finishing activity");
            Intent intent = new Intent();
            // Actually, it's not OK
            setResult(Activity.RESULT_OK, intent);
            // Bail
            finish();
        }

        // The fragment is used so the network thread can persist even if this activity doesn't
        FragmentManager fragmentManager = getFragmentManager();
        taskFragment = (DelegateTaskFragment) fragmentManager.findFragmentByTag(TAG_DELEGATE_TASK_FRAGMENT);

        // If the fragment already exists, it'll be because this is a fresh activity
        // otherwise, we should use the existing fragment (e.g. may be due to re-orientation)
        if (taskFragment == null) {
            taskFragment = new DelegateTaskFragment();
            taskFragment.setPairing(pairing);
            fragmentManager.beginTransaction().add(taskFragment, TAG_DELEGATE_TASK_FRAGMENT).commit();
        }

        // If the QR code has already been created, we should use it
        setQRCode(taskFragment.getQRBitmap());

        // Display the correct pairing name in the interface
        ((TextView) findViewById(R.id.delegate_name)).setText(pairing.getDisplayName());
    }

    @Override
    public void onDestroy() {
        LOGGER.debug("PicoServiceImpl is being destroyed...");
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // We don't want a menu, so this is just in case we want to add it back in again in the future

        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.delegate, menu);
        //return true;
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // There's no menu with this activity, so this should never get triggered

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //int id = item.getItemId();
        //if (id == R.id.action_settings) {
        //	return true;
        //}
        return super.onOptionsItemSelected(item);
    }

    /**
     * Convert the zxing BitMatrix into an Android Bitmap
     *
     * @param matrix - the zxing BitMatrix created when generating a QR code
     * @return the image as a Bitmap
     */
    public static Bitmap toBitmap(BitMatrix matrix) {
        int height = matrix.getHeight();
        int width = matrix.getWidth();

        // Create a bitmap of the correct size and depth
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        // Copy out each pixel individually
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }

        return bitmap;
    }
}
