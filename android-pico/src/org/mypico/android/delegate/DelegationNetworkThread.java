package org.mypico.android.delegate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;

import javax.crypto.spec.SecretKeySpec;

import org.jsoup.Jsoup;
import org.jsoup.nodes.FormElement;
import org.mypico.android.data.SafeLensPairing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.jpico.comms.JsonMessageSerializer;
import org.mypico.jpico.comms.RendezvousSigmaHandler;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.crypto.BrowserPasswordAuthToken;
import org.mypico.jpico.crypto.CryptoFactory;
import org.mypico.jpico.crypto.ISigmaVerifier;
import org.mypico.jpico.crypto.NewSigmaProver;
import org.mypico.jpico.crypto.NewSigmaVerifier;
import org.mypico.jpico.crypto.Nonce;
import org.mypico.jpico.crypto.ProtocolViolationException;
import org.mypico.jpico.crypto.messages.EncPairingDelegationMessage;
import org.mypico.jpico.crypto.messages.EncPicoReauthMessage;
import org.mypico.jpico.crypto.messages.PairingDelegationMessage;
import org.mypico.jpico.crypto.messages.SequenceNumber;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.LensPairingAccessor;
import org.mypico.jpico.data.pairing.Pairing;
import org.mypico.jpico.data.session.Session;
import org.mypico.jpico.data.session.SessionImpFactory;
import org.mypico.jpico.db.DbDataFactory;
import org.mypico.jpico.gson.VisualCodeGson;
import org.mypico.jpico.util.PicoCookieManager;
import org.mypico.jpico.util.WebProverUtils;
import org.mypico.jpico.visualcode.DelegatePairingVisualCode;
import org.mypico.rendezvous.RendezvousChannel;
import org.mypico.rendezvous.RendezvousClient;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * The network thread used to delegate an AuthToken to another Pico
 * Performs a sequence of tasks:
 * 1. Download a cookie from the Web Service to create an AuthToken
 * 2. Generate a QR code containing enough info for the delegatee Pico to
 * contact the delegator Pico: Rendezvous channel, nonce and ephemeral key.
 * The key is ephemeral (used to create an ephemeral pairing as if the
 * delegator is a terminal).
 * 3. Use the SigmaVerifier to pair the two devices and establish a
 * confidential channel.
 * 4. Transfer the full pairing details over the Rendezvous channel to the
 * delegator. Includes name, url, AuthToken and commitment.
 *
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 */
public class DelegationNetworkThread implements Runnable {
    /**
     * Use to log output messages to the LogCat console
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DelegateTaskFragment.class.getSimpleName());

    // Used to collect the details from the database
    final LensPairingAccessor accessor;
    final DbDataFactory dbDataFactory;
    // The pairing containing the data to be transfered
    final SafeLensPairing safePairing;
    // The Rendezvous details to communicate using
    RendezvousClient client;
    RendezvousChannel channel;
    String channelUrl;
    RendezvousSigmaHandler handler;
    // The data structure used to store the QR code details
    DelegatePairingVisualCode delegatePairingData;
    // Keys used to generate the ephemeral pairings between
    // the two Picos
    KeyPair keys;
    // The final result of the thread
    private boolean success;
    // If this ever becomes false, the thread should aim to shutdown as soon as possible
    boolean shouldContinue;
    // The fragment that ran this thread
    DelegateTaskFragment fragment;

    /**
     * Class constructor
     *
     * @param fragment      a reference to this activity
     * @param accessor      the accessor to be used to extract the {@link Pairing} information
     * @param dbDataFactory factory used to access the pairings database.
     * @param safePairing   the pairing to delegate.
     *                      from the Pico credentials database
     */
    protected DelegationNetworkThread(DelegateTaskFragment fragment, LensPairingAccessor accessor, DbDataFactory dbDataFactory, SafeLensPairing safePairing) {
        //super(activity);
        LOGGER.trace("Generating QR code");

        this.accessor = checkNotNull(accessor, "accessor cannot be null");
        this.dbDataFactory = checkNotNull(dbDataFactory, "dbDataFactory cannot be null");
        this.safePairing = checkNotNull(safePairing, "checkNotNull cannot be null");
        this.fragment = fragment;
        handler = null;
        shouldContinue = true;
    }

    /* (non-Javadoc)
     * Perform the network operations in sequence.
     * @see android.os.AsyncTask#doInBackground(Params[])
     */
    @Override
    //protected Session doInBackground(SafeLensPairing... params) {
    public void run() {
        Session session = null;
        handler = null;

        try {
            // Retrieve the full pairing from the database
            LOGGER.trace("Obtain pairing");
            final LensPairing pairing = safePairing.getLensPairing(accessor);
            if (pairing != null) {
                // Collect the session details from the database
                session = establishSessionwithAuthToken(pairing, dbDataFactory);
            } else {
                LOGGER.error("Pairing not found");
            }

            if (session != null) {
                LOGGER.trace("Create Rendezvous channel");
                // Create a rendezvous channel
                // TODO: Should this happen somewhere so it can be accessed universally?
                // TODO: The Rendezvous URL should probably be a configuration option
                client = new RendezvousClient(new URL("http://rendezvous.mypico.org"));
                channel = client.newChannel();
                channelUrl = channel.getUrl().toString();
                LOGGER.debug("Channel URL: " + channelUrl.toString());
            } else {
                LOGGER.trace("Session was not created");
            }

            // Generate ephemeral key material
            LOGGER.trace("Generate ephemeral key material");
            keys = CryptoFactory.INSTANCE.ecKpg().generateKeyPair();

            // Perform a temporary pairing with the Pico delegatee
            final Nonce nonce = Nonce.getRandomInstance();

            // Generate the QR code to display in the UI
            LOGGER.trace("Create QR code image");
            delegatePairingData = generateQRCode(channelUrl, nonce, keys);

            if (fragment.getUIActivity() != null) {
                // If the UI exists, display the QR code
                // This needs to be done on the UI thread
                final ProgressUpdate onProgressUpdate = new ProgressUpdate((DelegateActivity) fragment.getUIActivity(), fragment.getQRBitmap());
                fragment.getUIActivity().runOnUiThread(onProgressUpdate);
            }

            if (session != null) {
                // Pefrom the network activity
                negotiateDelegation(session);
            } else {
                LOGGER.trace("Session is null or QR code note generated");
            }

            //return session;
        } catch (IOException e) {
            LOGGER.warn("IOException while loading credentials", e);
        }

        if (fragment.getUIActivity() != null) {
            // Finally, display the result (success/failure of the delegation)
            final PostExecute onPostExecute = new PostExecute(fragment.getUIActivity(), session);
            fragment.getUIActivity().runOnUiThread(onPostExecute);
        }
    }

    /**
     * The final step has to happen back on the UI thread
     *
     * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
     */
    private class PostExecute implements Runnable {
        private final Session session;
        private final Activity activity;

        public PostExecute(Activity activity, Session session) {
            this.activity = activity;
            this.session = session;
        }

        public void run() {
            if (shouldContinue) {
                Intent intent = new Intent();
                if (session != null) {
                    if (success == true) {
                        // The delegation was successful, so show through the UI
                        showMessageDelegationSuccess(activity, session.getPairing().getName());
                        activity.setResult(Activity.RESULT_OK, intent);
                    } else {
                        // The delegation was unsuccessful, so show through the UI
                        showMessageDelegationFail(activity, session.getPairing().getName());
                        activity.setResult(Activity.RESULT_OK, intent);
                    }
                } else {
                    // The delegation was unsuccessful, and we didn't even manage to figure out
                    // what the service was
                    showMessageDelegationFail(activity, "Unknown Service");
                    activity.setResult(Activity.RESULT_OK, intent);
                }
            }
            // And we're done
            activity.finish();
        }
    }

    /**
     * Set the QR code in the UI thread
     *
     * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
     */
    private class ProgressUpdate implements Runnable {
        final Bitmap qrbitmap;
        final DelegateActivity activity;

        public ProgressUpdate(DelegateActivity activity, Bitmap qrbitmap) {
            this.activity = activity;
            this.qrbitmap = qrbitmap;
        }

        public void run() {
            if (qrbitmap != null) {
                activity.setQRCode(qrbitmap);
            }
        }
    }

    /**
     * Hide the QR code in the UI thread
     *
     * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
     */
    private class ProgressToNetwork implements Runnable {
        final DelegateActivity activity;

        public ProgressToNetwork(DelegateActivity activity) {
            this.activity = activity;
        }

        public void run() {
            activity.hideQRCode();
        }
    }

    /**
     * Download a cookie from the service Website to delegate to the
     * other Pico
     * <p>
     * TODO: This should be created inside a Prover, to mimic org.mypico.jpico.crypto.LensProver?
     *
     * @param pairing        the LensPairing containing the info for logging in to the website
     * @param sessionFactory for accessing the data in the database
     * @return the full Session pulled from the database
     */
    private Session establishSessionwithAuthToken(final LensPairing pairing, final SessionImpFactory sessionFactory) {
        final PicoCookieManager cookieManager = new PicoCookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        AuthToken token = null;

        // Get the address to authenticate to
        URL serviceAddress;
        try {
            serviceAddress = pairing.getService().getAddress().toURL();
            final HttpURLConnection formConnection = WebProverUtils.makeRequest(serviceAddress, null, null, "");

            StringBuffer loginPage = new StringBuffer();
            if ((formConnection.getResponseCode() == 200) && (formConnection.getInputStream() != null)) {
                BufferedReader br = new BufferedReader(new InputStreamReader((formConnection.getInputStream())));
                String line;
                while ((line = br.readLine()) != null) {
                    loginPage.append(line);
                }
            }

            // Get the login form
            LOGGER.trace("Downloading login form");
            final FormElement loginForm = WebProverUtils.getLoginForm(Jsoup.parseBodyFragment(loginPage.toString(), serviceAddress.toString()));

            // Build the POST data for the form submission
            final String postData = WebProverUtils.buildPostData(loginForm, pairing.getCredentials());

            // Make form submission POST request
            final URL connUrl = loginForm.submit().request().url();
            LOGGER.debug("Making form submission to {}", connUrl);

            // Do not follow any further redirects, we can now
            // pass back the data the Browser needs to complete the job
            // TODO: Check this cookie is anything sensible at all
            LOGGER.trace("Extract cookies");
            String cookieString = null;
            if (!cookieManager.getCookieStore().get(serviceAddress.toURI()).isEmpty()) {
                cookieString = cookieManager.getCookieStore().get(serviceAddress.toURI()).get(0).toString();
            }
            final HttpURLConnection loginConnection =
                WebProverUtils.makeRequest(connUrl, serviceAddress, postData, cookieString);
            URL redirectUrl = WebProverUtils.getRedirectUrl(loginConnection);
            if (redirectUrl == null) {
                redirectUrl = connUrl;
            }

            StringBuffer sb = new StringBuffer();
            // Read the response body
            if (loginConnection.getResponseCode() == 200) {

                if (loginConnection.getInputStream() != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader((loginConnection.getInputStream())));
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
                LOGGER.trace("Response body = {}", sb.toString());
            }
            LOGGER.trace("Response body = {}", sb.toString());

            // Make browser auth token result
            token = new BrowserPasswordAuthToken(
                cookieManager.getRawCookies(),
                serviceAddress,
                redirectUrl,
                sb.toString(),
                pairing.getCredentials());
        } catch (MalformedURLException e) {
            LOGGER.error("MalformedURLException occurred!", e);
        } catch (IOException e) {
            LOGGER.error("IOException occurred!", e);
        } catch (URISyntaxException e) {
            LOGGER.error("URISyntaxException occurred!", e);
        }

        LOGGER.trace("Return new session details");
        return Session.newInstanceClosed(sessionFactory, null, pairing, token);
    }

    /**
     * Perform the network negotiation
     *
     * @param session the session containing the data and AuthToken to be delegated
     */
    protected void negotiateDelegation(Session session) {
        success = false;
        LOGGER.trace("Await response from another Pico");

        if (channel != null) {
            LOGGER.trace("Setting up RendezvousSigmaHandler");

            // Set up the Sigma verifier
            SigmaVerifierClient client = new SigmaVerifierClient();

            LOGGER.trace("Creating verifier");
            final NewSigmaVerifier verifier = new NewSigmaVerifier(
                NewSigmaProver.VERSION_1_1,
                keys,
                1,
                client,
                false);
            client.verifier = verifier;
            client.session = session;

            // Attach the verifier to the rendzvous channel
            LOGGER.trace("Creating Rendezvous Sigma Handler");
            handler = new RendezvousSigmaHandler(channel, new JsonMessageSerializer(), verifier);

            try {
                // This is the part which potentially blocks
                LOGGER.trace("Call Rendezvous Sigma Handler");
                // Perform the network communication
                handler.call();
                LOGGER.trace("Exited Rendezvous Sigma Handler");
                if (shouldContinue) {
                    success = true;
                }
            } catch (IOException e) {
                LOGGER.trace("IOException in Rendezvous Sigma Handler");
            } catch (ProtocolViolationException e) {
                LOGGER.trace("ProtocolViolationException in Rendezvous Sigma Handler");
            }
        } else {
            LOGGER.warn("Channel is null");
        }
    }

    /**
     * Receive the callback from the SigmaVerfier to establish whether the
     * delegatee Pico returned the correct nonce
     * If so, send the data back to the delegatee with the AuthToken
     *
     * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
     */
    class SigmaVerifierClient implements ISigmaVerifier.Client {
        public NewSigmaVerifier verifier;
        Session session;

        @Override
        public ClientAuthorisation onAuthenticate(final PublicKey picoPublicKey,
                                                  final byte[] receivedExtraData) throws IOException {
            LOGGER.info("Pico authenticated");

            final ProgressToNetwork onProgressUpdate = new ProgressToNetwork((DelegateActivity) fragment.getUIActivity());
            fragment.getUIActivity().runOnUiThread(onProgressUpdate);

            final Nonce receivedNonce = Nonce.getInstance(receivedExtraData);
            if (receivedNonce.equals(delegatePairingData.getNonce())) {
                // Pico has sent the same nonce that appeared in the QR code
                // Set up the message containing the AuthToken
                LOGGER.debug("Correct nonce received");
                EncPairingDelegationMessage delegationMessage = generatePairingDelegationMessage(session, verifier.getSharedKey().getEncoded());

                // Serialise the message
                LOGGER.debug("Serialise");
                JsonMessageSerializer serializer = new JsonMessageSerializer();
                final byte[] serializedMsg = serializer.serialize(delegationMessage, EncPicoReauthMessage.class);

                // Send the data as the final part of the handshake
                LOGGER.debug("Accept with serialised message");
                ClientAuthorisation auth = ClientAuthorisation.accept(serializedMsg);

                return auth;
            } else {
                // Pico has sent an incorrect nonce
                LOGGER.error("Incorrect nonce received");
                return ClientAuthorisation.reject();
            }
        }

    }

    /**
     * Set up a message containing all of the data to be delegated
     *
     * @param session           the session containing the details including the AuthToken
     * @param terminalSharedKey the ephemeral terminal shared key
     * @return the message containing the required data
     */
    private EncPairingDelegationMessage generatePairingDelegationMessage(Session session, byte[] terminalSharedKey) {
        try {
            // TODO: Update constructor to deal properly with null extraData
            final PairingDelegationMessage message = new PairingDelegationMessage(
                session.getId(), SequenceNumber.getRandomInstance(),
                session.getPairing().getName(),
                session.getAuthToken(),
                session.getPairing().getService().getCommitment(),
                session.getPairing().getService().getAddress().toString(),
                new byte[0]);

            return message.encrypt(new SecretKeySpec(terminalSharedKey, "AES/GCM/NoPadding"));

        } catch (InvalidKeyException e) {
            LOGGER.warn("Invalid key generating reauth message");
        }
        return null;
    }

    /**
     * Display a message indicating a successful delegation.
     * This needs to be executed in the UI thread
     *
     * @param activity the UI activity
     * @param name     of the service that was delegated
     */
    void showMessageDelegationSuccess(Activity activity, String name) {
        final String toastFmt = activity.getString(R.string.delegation_successful_fmt);
        final String toastMessage = String.format(toastFmt, name);
        Toast.makeText(activity, toastMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Display a message indicating an unsuccessful delegation.
     * This needs to be executed in the UI thread
     *
     * @param activity the UI activity
     * @param name     of the service that failed to be delegated
     */
    void showMessageDelegationFail(Activity activity, String name) {
        final String toastFmt = activity.getString(R.string.delegation_failed_fmt);
        final String toastMessage = String.format(toastFmt, name);
        Toast.makeText(activity, toastMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Generate a QR code for delegating a {@link Pairing} from one Pico to another.
     *
     * @param channelUrl The channel through which the delegation will be performed.
     * @param nonce      The random nonce to use to make this delegation QR code unique.
     * @param keys       The keys to use to secure the pairing.
     * @return The QR code object generated.
     */
    public DelegatePairingVisualCode generateQRCode(String channelUrl, final Nonce nonce, final KeyPair keys) {
        DelegatePairingVisualCode delegatePairingData = null;

        // For info about generating QR-codes see
        // See http://codeisland.org/2013/generating-qr-codes-with-zxing/
        QRCodeWriter writer = new QRCodeWriter();
        try {
            // TODO: Give the terminal a proper name
            // Create a DelegatePairingVisualCode (type: "DP") from this data. We'll use the JSON structure it can generate to produce our QR code
            delegatePairingData = DelegatePairingVisualCode.getInstance("Temporary", nonce, new URI(channelUrl), keys.getPublic());
            String delegatePairingJson = VisualCodeGson.gson.toJson(delegatePairingData);

            // Generate a QR code image from the JSON structure (type: "LP")
            BitMatrix qrbitmatrix = writer.encode(delegatePairingJson, BarcodeFormat.QR_CODE, 300, 300);
            Bitmap qrbitmap = toBitmap(qrbitmatrix);
            fragment.setQRBitmap(qrbitmap);
        } catch (WriterException e) {
            LOGGER.error("Failed to write QR code BitMatrix: WriterException");
        } catch (URISyntaxException e) {
            LOGGER.error("Failed to write QR code BitMatrix: URISyntaxException");
        }

        return delegatePairingData;
    }

    /**
     * Convert the zxing BitMatrix into an Android Bitmap
     *
     * @param matrix - the zxing BitMatrix created when generating a QR code
     * @return the image as a Bitmap
     */
    public Bitmap toBitmap(BitMatrix matrix) {
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

    /**
     * If the user aborts (e.g. hits the 'back' button) the thread should finish as
     * swiftly as possible.
     * <p>
     * This function notifies the thread it should attempt to finish
     */
    public void abort() {
        shouldContinue = false;
        if (handler != null) {
            handler.abort();
        }
    }
}
