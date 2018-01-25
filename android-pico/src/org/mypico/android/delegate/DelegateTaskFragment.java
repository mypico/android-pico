package org.mypico.android.delegate;

import java.sql.SQLException;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.data.pairing.LensPairingAccessor;
import org.mypico.jpico.data.pairing.Pairing;
import org.mypico.jpico.db.DbDataFactory;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.os.Bundle;

import com.google.common.base.Optional;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

/**
 * Fragment for performing the background network thread.
 * The purpose is to allow the fragment to persist even
 * across activity destruction/recreation using setRetainInstance
 * See https://developer.android.com/reference/android/app/Fragment.html#setRetainInstance%28boolean%29
 * and http://www.androiddesignpatterns.com/2013/04/retaining-objects-across-config-changes.html
 * for more info.
 *
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class DelegateTaskFragment extends Fragment {
    /**
     * Use to log output messages to the LogCat console
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DelegateTaskFragment.class.getSimpleName());

    /**
     * Store the activity that the fragment is attached to
     * Beware that this may be null (e.g. in the middle of a
     * screen re-orientation).
     */
    Activity activity;

    /**
     * Accessor used to extract data from the Pico credentials database
     */
    private Optional<LensPairingAccessor> accessor = Optional.absent();

    /**
     * The thread to run the delegation network code on
     */
    private Thread networkThread;

    /**
     * The Runnable class that will execute as the thread
     */
    private DelegationNetworkThread networkAsync;
    /**
     * Object for storing the {@link Pairing} to be delegated
     */
    private SafeLensPairing pairing;
    /**
     * Database access is needed for collecting the LensPairing details
     */
    private DbDataFactory dbDataFactory;
    /**
     * Once the bitmap is generated it can be used each time the fragment
     * is re-attached to the activity. This will be null until the QR code
     * has been generated.
     */
    Bitmap qrbitmap;

    /**
     * Default constructor
     */
    public DelegateTaskFragment() {
        // These values should be null until the thread and QR code have been set up
        qrbitmap = null;
        networkAsync = null;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // The fragmet has been attached (or re-attached) to an activity
        this.activity = activity;
    }

    /**
     * Set the pairing that contains the account info to be delegated
     *
     * @param pairing the pairing to delegate
     */
    void setPairing(SafeLensPairing pairing) {
        this.pairing = pairing;
    }

    /**
     * Get the QR code for the delegation
     *
     * @return the QR code, or null if it's not yet been generated
     */
    public Bitmap getQRBitmap() {
        return qrbitmap;
    }

    /**
     * Used by the network thread to return the QR code bitmap to the fragment
     *
     * @param qrbitmap the QR code bitmap for this delegation
     */
    public void setQRBitmap(Bitmap qrbitmap) {
        this.qrbitmap = qrbitmap;
    }

    /**
     * Get the activity of the UI thread. Needed so that the thread can make changes
     * to the UI as the network process progresses (e.g. displaying the QR code).
     * The thread can't store its own copy because the UI activity may change without
     * it knowing (but the fragment will be told).
     *
     * @return the UI activity, or null if there isn't one (e.g. mid re-orientation)
     */
    public Activity getUIActivity() {
        return activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure the fragment will be retained even if the activity is destroyed/recreated
        setRetainInstance(true);

        // Ormlite helper
        final OrmLiteSqliteOpenHelper helper =
            OpenHelperManager.getHelper(activity, DbHelper.class);
        try {
            // The database is used to retrieve the LensPairing
            dbDataFactory = new DbDataFactory(helper.getConnectionSource());
            accessor = Optional.of(DbHelper.getInstance(activity).getLensPairingAccessor());
        } catch (SQLException e) {
            // TODO: We should probably do something more drastic than just log the error
            // like finish the activity and throw up an error message
            LOGGER.error("Failed to connect to database");
        }

        // Execute thread
        networkAsync = new DelegationNetworkThread(this, accessor.get(), dbDataFactory, pairing);
        networkThread = new Thread(networkAsync);
        networkThread.setName("DelegateActivityNetwork");
        networkThread.start();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // The underlying activity has been destroyed
        activity = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Do our best to close down the network thread
        // In practice, the HTTP read operation may need to timeout first
        if (networkAsync != null) {
            networkAsync.abort();
        }
    }
}
