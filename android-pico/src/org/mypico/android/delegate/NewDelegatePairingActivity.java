package org.mypico.android.delegate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.ArrayList;

import org.mypico.android.core.ReattachTask;
import org.mypico.android.data.NonceParcel;
import org.mypico.android.data.SafeService;
import org.mypico.android.db.DbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;
import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.SafeLensPairing;
import org.mypico.jpico.comms.JsonMessageSerializer;
import org.mypico.jpico.comms.RendezvousSigmaProxy;
import org.mypico.jpico.comms.org.apache.commons.codec.binary.Base64;
import org.mypico.jpico.crypto.AuthToken;
import org.mypico.jpico.crypto.CryptoFactory;
import org.mypico.jpico.crypto.NewSigmaProver;
import org.mypico.jpico.crypto.NewSigmaProver.ProverAuthRejectedException;
import org.mypico.jpico.crypto.NewSigmaProver.VerifierAuthFailedException;
import org.mypico.jpico.crypto.Nonce;
import org.mypico.jpico.crypto.ProtocolViolationException;
import org.mypico.jpico.crypto.messages.EncPairingDelegationMessage;
import org.mypico.jpico.crypto.messages.PairingDelegationMessage;
import org.mypico.jpico.data.pairing.LensPairing;
import org.mypico.jpico.data.pairing.Pairing;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.db.DbDataFactory;
import org.mypico.rendezvous.RendezvousChannel;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

/**
 * UI for creating and naming a new Pico Lens pairing.
 * Successfully created pairings are persisted to the Pico pairings database
 * using the NewLensPairingIntentService.
 *
 * @author Max Spencer <ms955@cl.cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
 * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
 * @see DelegatePairingIntentService
 */
final public class NewDelegatePairingActivity extends Activity {

    private final static Logger LOGGER = LoggerFactory.getLogger(
        NewDelegatePairingActivity.class.getSimpleName());
//    private final static String[] userNameRegexs =
//    	{"username", "uname", "email", "user.*", "u.*", ".*id.*" };

    private final IntentFilter intentFilter;
    //private final ResponseReceiver responseReceiver = new ResponseReceiver();

//    private ParcelableCredentials credentials;   

    private DbDataFactory dbDataFactory;
    private DbDataAccessor dbDataAccessor;

    {
        // Create an IntentFilter for the actions returned by the NewLensPairingIntentService
        intentFilter = new IntentFilter();
        intentFilter.addAction(DelegatePairingIntentService.IS_PAIRING_PRESENT_ACTION);
        intentFilter.addAction(DelegatePairingIntentService.PERSIST_PAIRING_ACTION);
    }

    /**
     * The AsyncResponse is used to negotiate with the delegatee Pico
     * and deal with the response that comes back
     * <p>
     * TODO: Potentially move this into its own classfile
     *
     * @author David Llewellyn-Jones <David.Llewellyn-Jones@cl.cam.ac.uk>
     */
    private static class AwaitDelegatedResponse extends ReattachTask<Void, Void, Integer> {
        private final static int SUCCESS = 1;
        private final static int FAILURE = 0;
        final URI newTerminalAddress;
        final byte[] newTerminalCommitment;
        //final String newTerminalName;
        final Nonce newTerminalNonce;
        final DbDataFactory dbDataFactory;
        final DbDataAccessor dbDataAccessor;
        final Activity activity;
        String name;

        /**
         * Class constructor
         *
         * @param activity        A reference to this activity
         * @param terminalAddress The terminal address to negotiate with.
         * @param commitment      The commitment of the terminal.
         * @param nonce           The nonce to use for communciation.
         * @param dbDataFactory   The database factory to use.
         * @param dbDataAccessor  The database accessor to use.
         */
        protected AwaitDelegatedResponse(Activity activity, final URI terminalAddress, final byte[] commitment, Nonce nonce, DbDataFactory dbDataFactory, DbDataAccessor dbDataAccessor) {
            super(activity);

            //newTerminalAddress = dpCode.getTerminalAddress();
            this.newTerminalCommitment = checkNotNull(commitment, "newTerminalCommitment cannot be null");
            //newTerminalName = dpCode.getTerminalName();
            this.newTerminalNonce = checkNotNull(nonce, "nonce cannot be null");
            this.newTerminalAddress = checkNotNull(terminalAddress, "terminalAddress cannot be null");
            this.dbDataFactory = checkNotNull(dbDataFactory, "dbDataFactory cannot be null");
            this.dbDataAccessor = checkNotNull(dbDataAccessor, "dbDataAccessor cannot be null");
            this.activity = checkNotNull(activity, "activity cannot be null");
            name = "unknown";
        }

        /* (non-Javadoc)
         * Collect the {@link Pairing} info from the Pico credentials database asynchronously.
         * This kicks things off.
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Integer doInBackground(Void... params) {
            int result = FAILURE;
            // Discuss with other Pico
            LOGGER.debug("Awaiting delegated response");

            // Return the result as a broadcast
            try {
                LOGGER.debug("Using Rendezvous channel: " + newTerminalAddress);
                RendezvousChannel channel;
                channel = new RendezvousChannel(newTerminalAddress.toURL());

                // Create new Pico keys for the terminal pairing
                final KeyPair picoKeyPair = CryptoFactory.INSTANCE.ecKpg().generateKeyPair();

                // Convert newTerminalAddress from Uri to URL
                //final URL terminalUrl = newTerminalAddress.toURL();

                // Make a proxy for the terminal's sigma verifier to handle communication via the
                // rendezvous point.
                LOGGER.trace("Creating Rendezvous Sigma Proxy");
                final RendezvousSigmaProxy proxy = new RendezvousSigmaProxy(
                    channel,
                    new JsonMessageSerializer());

                LOGGER.trace("Creating Sigma Prover");
                // Make the sigma prover which will authenticate to the terminal and send the required
                // nonce.
                final NewSigmaProver prover = new NewSigmaProver(
                    NewSigmaProver.VERSION_1_1,
                    picoKeyPair,
                    newTerminalNonce.getValue(),
                    proxy,
                    newTerminalCommitment,
                    null);

                // Carry out the sigma protocol with the terminal
                try {
                    LOGGER.trace("Proving");
                    boolean proverResult = prover.prove();
                    LOGGER.debug("Result: " + proverResult);
                    LOGGER.trace("Proved");
                    byte[] extra = prover.getReceivedExtraData();
                    LOGGER.debug("Extra data: " + new String(extra));

                    // Deserialise the message
                    JsonMessageSerializer serializer = new JsonMessageSerializer();
                    EncPairingDelegationMessage delegationMessage = serializer.deserialize(extra, EncPairingDelegationMessage.class);
                    PairingDelegationMessage message = delegationMessage.decrypt(prover.getSharedKey());

                    // Collect the data from the message
                    final String serviceName = message.getServiceName();
                    name = serviceName;
                    LOGGER.debug("Service name: " + serviceName);
                    final AuthToken token = message.getAuthToken();
                    LOGGER.debug("Decrypted auth token: " + token.getFull());

                    final byte[] commitment = message.getCommitment();
                    final Uri address = SafeService.URIToUri(new URI(message.getAddress()));
                    final Uri logoUri = null;

                    // Create a new service from the data received
                    SafeService service = new SafeService(serviceName, commitment, address, logoUri);
                    SafeLensPairing pairing = new SafeLensPairing(serviceName, service);

                    // Store the AuthToken in the credentials map
                    // TODO: This should be stored in a separate field
                    HashMap<String, String> credentials = new HashMap<String, String>();
                    final byte[] tokenByteArray = token.toByteArray();
                    final String tokenString = Base64.encodeBase64String(tokenByteArray);
                    credentials.put("AuthToken", tokenString);

                    // Create and save the resulting pairing in the database
                    LensPairing newPairing = pairing.createLensPairing(dbDataFactory, dbDataAccessor, credentials, new ArrayList<String>());
                    newPairing.save();

                    result = SUCCESS;

                    // If no exceptions are thrown above, the authentication was successful, so save
                    // the new Terminal record.
                    LOGGER.trace("Prover handshake completed successfully");
                } catch (IOException e) {
                    LOGGER.warn("failed to create new terminal pairing (IOException)", e);
                    throw e;
                } catch (ProverAuthRejectedException e) {
                    LOGGER.warn("failed to create new terminal pairing (rejected)", e);
                    throw e;
                } catch (ProtocolViolationException e) {
                    LOGGER.warn("failed to create new terminal pairing (protocol violation)", e);
                    throw e;
                } catch (VerifierAuthFailedException e) {
                    LOGGER.warn("failed to create new terminal pairing (verifier auth)", e);
                    throw e;
                }
            } catch (MalformedURLException e) {
                LOGGER.warn("Unable to convert newTerminalAddress from Uri to URL");
                result = FAILURE;
            } catch (Exception e) {
                LOGGER.warn("Exception");
                result = FAILURE;
            } finally {
                LOGGER.debug("Finally here");
            }

            return result;
        }

        /* (non-Javadoc)
         * Executed on the UI thread
         *
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        public void onPostExecute(Integer result) {
            if (result == SUCCESS) {
                // The result was successful
                showMessageDelegationSuccess(activity, name);
            } else {
                // The result wasn't successful
                showMessageDelegationFail(activity, name);
            }

            // Everything is done
            activity.finish();
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
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGGER.trace("onCreate NewDelegatePairingActivity");
        setContentView(R.layout.activity_new_delegate);

        Intent intent = getIntent();

        // Unpack results
        LOGGER.trace("Unpacking results");
        final String terminalName = intent.getStringExtra(VisualCodeIntentGenerator.TERMINAL_NAME);
        LOGGER.debug("Terminal name: " + terminalName);
        Uri address = (Uri) intent.getParcelableExtra(VisualCodeIntentGenerator.TERMINAL_ADDRESS);
        URI terminalAddress = null;
        try {
            terminalAddress = new java.net.URI(address.toString());
            LOGGER.debug("Terminal address: " + terminalAddress.toString());
        } catch (URISyntaxException e) {
            LOGGER.debug("Terminal address could not be converted to URI:" + address.toString());
        }
        final byte[] commitment = intent.getByteArrayExtra(VisualCodeIntentGenerator.TERMINAL_COMMITMENT);
        Nonce nonce = ((NonceParcel) intent.getParcelableExtra(VisualCodeIntentGenerator.NONCE)).getNonce();

        // Ormlite helper
        final OrmLiteSqliteOpenHelper helper =
            OpenHelperManager.getHelper(this, DbHelper.class);
        try {
            dbDataFactory = new DbDataFactory(helper.getConnectionSource());
            dbDataAccessor = new DbDataAccessor(helper.getConnectionSource());
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to database");
            throw new RuntimeException(e);
        }

        // Kick off the async task that will wait for the results to come back from the delegator Pico
        new AwaitDelegatedResponse(this, terminalAddress, commitment, nonce, dbDataFactory, dbDataAccessor).execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
