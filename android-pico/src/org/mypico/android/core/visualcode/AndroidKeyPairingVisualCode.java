package org.mypico.android.core.visualcode;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.data.SafeService;
import org.mypico.android.pairing.NewKeyPairingActivity;
import org.mypico.jpico.crypto.CryptoFactory;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.KeyPairingVisualCode;
import org.mypico.jpico.visualcode.SignedVisualCode;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.mypico.android.core.VisualCodeIntentGenerator.SERVICE;

/**
 * {@link KeyPairingVisualCode} with additional functionality used by the app.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AndroidKeyPairingVisualCode extends KeyPairingVisualCode
    implements AndroidVisualCode {
    private static final String TAG = "AndroidVisualCode";

    public CodeType getCodeType() {
        return CodeType.PAIRING;
    }

    /**
     * Create the {@link Intent} that will handle a {@link KeyPairingVisualCode} when it gets
     * scanned.
     * <p>
     * Next activity: {@link NewKeyPairingActivity}
     * <p>
     * Extras:
     * <ul>
     * <li>{@link VisualCodeIntentGenerator#SERVICE SERVICE}</li>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_ADDRESS TERMINAL_ADDRESS} (optional)</li>
     * <li>{@link VisualCodeIntentGenerator#TERMINAL_COMMITMENT TERMINAL_COMMITMENT} (optional)</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent createIntent(Context context, boolean startedForResult)
        throws InvalidVisualCodeException {
        Log.d(TAG, "Creating Intent from KeyPairingVisualCode instance");

        if (!verifyKeyPairingVisualCode(this)) {
            throw new InvalidVisualCodeException("Visual code has invalid signature");
        }

        final Intent intent = new Intent();
        if (!startedForResult) {
            // Set target activity
            intent.setClass(context, NewKeyPairingActivity.class);
        }

        // Add service from visual code to intent
        SafeService service = SafeService.fromVisualCode(this);
        intent.putExtra(SERVICE, service);

        // Add extra data to intent
        intent.putExtra("myExtraData", getExtraData());
        Log.d(TAG, "Extra data straight from visual code was " + getExtraData());

        // Add terminal details to intent
        VisualCodeIntentGenerator.putTerminalDetailsIfPresent(intent, this);

        return intent;
    }

    /**
     * Verifies the signature of a {@link SignedVisualCode}.
     *
     * @param visualCode  the visual code to verify
     * @param signedBytes the bytes the signature should be over
     * @param publicKey   the public key to verify the signature with
     * @return <code>true</code> if the signature of <code>visualCode</code>
     * verifies with the supplied bytes and public key, or
     * <code>false</code> otherwise.
     * @throws InvalidKeyException
     * @throws SignatureException
     */
    private static boolean verifyVisualCode(final SignedVisualCode visualCode,
                                            final byte[] signedBytes,
                                            final PublicKey publicKey)
        throws InvalidKeyException, SignatureException {
        checkNotNull(visualCode, "visualCode cannot be null");
        checkNotNull(signedBytes, "signedBytes cannot be null");
        checkNotNull(publicKey, "publicKey cannot be null");

        final Signature verifier = CryptoFactory.INSTANCE.sha256Ecdsa();
        verifier.initVerify(publicKey);
        verifier.update(signedBytes);
        return verifier.verify(visualCode.getSignature());
    }

    /**
     * Verify the signature of a {@link KeyPairingVisualCode} using the public
     * key it contains.
     *
     * @param code the visual code
     * @return <code>true</code> if the signature verifies or <code>false</code>
     * otherwise
     * @throws InvalidVisualCodeException if the signature cannot be verified
     */
    private static boolean verifyKeyPairingVisualCode(final KeyPairingVisualCode code)
        throws InvalidVisualCodeException {
        checkNotNull(code, "code cannot be null");

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final byte[] signedBytes;
        try {
            os.write(code.getServiceName().getBytes("UTF-8"));
            os.write(code.getServiceAddress().toString().getBytes("UTF-8"));
            signedBytes = os.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("ByteArrayOutputStream should never throw an IOException", e);
        }

        try {
            return verifyVisualCode(code, signedBytes, code.getServicePublicKey());
        } catch (InvalidKeyException e) {
            throw new InvalidVisualCodeException("Signed visual code has an invalid key", e);
        } catch (SignatureException e) {
            throw new InvalidVisualCodeException("Unable to verify visual code signature", e);
        }
    }

}
