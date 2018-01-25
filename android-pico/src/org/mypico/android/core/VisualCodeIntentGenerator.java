package org.mypico.android.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.mypico.android.core.visualcode.AndroidDelegatePairingVisualCode;
import org.mypico.android.core.visualcode.AndroidKeyAuthenticationVisualCode;
import org.mypico.android.core.visualcode.AndroidKeyPairingVisualCode;
import org.mypico.android.core.visualcode.AndroidLensAuthenticationVisualCode;
import org.mypico.android.core.visualcode.AndroidLensPairingVisualCode;
import org.mypico.android.core.visualcode.AndroidTerminalPairingVisualCode;
import org.mypico.android.core.visualcode.AndroidVisualCode;
import org.mypico.android.core.visualcode.CodeType;
import org.mypico.android.data.NonceParcel;
import org.mypico.android.data.ParcelableCredentials;
import org.mypico.android.data.SafeService;
import org.mypico.jpico.gson.VisualCodeGson;
import org.mypico.jpico.visualcode.DelegatePairingVisualCode;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.KeyAuthenticationVisualCode;
import org.mypico.jpico.visualcode.KeyPairingVisualCode;
import org.mypico.jpico.visualcode.LensAuthenticationVisualCode;
import org.mypico.jpico.visualcode.LensPairingVisualCode;
import org.mypico.jpico.visualcode.TerminalPairingVisualCode;
import org.mypico.jpico.visualcode.VisualCode;
import org.mypico.jpico.visualcode.WithTerminalDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Take a JSON visual code string and generate an Intent from it.
 * Moved from AcquireCodeActivity.
 * <p>
 * See documentation for each {@link AndroidVisualCode} subclass's {@code getIntent} method for
 * details about the {@link Intent} that will be generated: which Activity it starts next and what
 * extras will be present.
 * <p>
 * The keys for the extras, which contain the parsed data of the QR code, are supplied as constant
 * members of this class. The types of the extras they correspond to are documented in their
 * JavaDoc.
 *
 * @author Max Spencer <ms955@cl.cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cl.cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
public class VisualCodeIntentGenerator {

    private static final Map<String, Class<? extends VisualCode>> typeMap =
        new TreeMap<String, Class<? extends VisualCode>>();

    static {
        typeMap.put(KeyPairingVisualCode.TYPE, AndroidKeyPairingVisualCode.class);
        typeMap.put(KeyAuthenticationVisualCode.TYPE, AndroidKeyAuthenticationVisualCode.class);
        typeMap.put(LensPairingVisualCode.TYPE, AndroidLensPairingVisualCode.class);
        typeMap.put(LensAuthenticationVisualCode.TYPE, AndroidLensAuthenticationVisualCode.class);
        typeMap.put(TerminalPairingVisualCode.TYPE, AndroidTerminalPairingVisualCode.class);
        typeMap.put(DelegatePairingVisualCode.TYPE, AndroidDelegatePairingVisualCode.class);
    }

    private static final Gson gson = VisualCodeGson.custom(typeMap);

    @Deprecated
    public final static String SERVICE_INFO_INTENT =
        SafeService.class.getCanonicalName();

    /**
     * Key for the terminal name extra. This extra is a {@link String}.
     */
    public static final String TERMINAL_NAME =
        AcquireCodeActivity.class.getCanonicalName() + "terminalName";

    /**
     * Key for the nonce extra. This extra is a {@link NonceParcel}.
     */
    public static final String NONCE =
        AcquireCodeActivity.class.getCanonicalName() + "nonce";

    /**
     * Key for the terminal address extra. This extra is a {@link Uri}.
     */
    public static final String TERMINAL_ADDRESS =
        AcquireCodeActivity.class.getCanonicalName() + "terminalAddress";

    /**
     * Key for the terminal commitment extra. This extra is a byte array.
     */
    public static final String TERMINAL_COMMITMENT =
        AcquireCodeActivity.class.getCanonicalName() + "terminalCommit";

    // TODO change these to have the above form. Requires checking that nowhere
    // else is using the current key values.
    /**
     * Key for the service extra. This extra is a {@link SafeService}.
     */
    public final static String SERVICE =
        SafeService.class.getCanonicalName();

    private static final Logger LOGGER =
        LoggerFactory.getLogger(AcquireCodeActivity.class.getSimpleName());

    /**
     * Key for the credentials extra. This extra is a
     * {@link ParcelableCredentials}.
     */
    public final static String CREDENTIALS =
        ParcelableCredentials.class.getCanonicalName();

    /**
     * Exception that will be thrown if the user has tried to scan a code that is not allowed.
     * For example, a pairing code is allowed only when in pairing mode.
     */
    public static class WrongCodeTypeException extends Exception {

        private static final long serialVersionUID = 882410541131303071L;

        private CodeType wrongType;

        public WrongCodeTypeException(CodeType wrongType) {
            this.wrongType = wrongType;
        }

        public String wrongTypeWithArticle(Context context) {
            return context.getString(wrongType.withArticleResId());
        }
    }

    /**
     * If present add the terminal details from a visual code to an intent.
     *
     * @param intent the intent
     * @param code   the visual code
     * @return <code>true</code> if the terminal details were present or
     * <code>false</code> otherwise
     */
    public static boolean putTerminalDetailsIfPresent(Intent intent, WithTerminalDetails code) {
        if (code.hasTerminal()) {
            Uri terminalAddress = SafeService.URIToUri(code.getTerminalAddress());
            intent.putExtra(TERMINAL_ADDRESS, terminalAddress);
            intent.putExtra(TERMINAL_COMMITMENT, code.getTerminalCommitment());
            LOGGER.debug("Visual code contains terminal details ({},{})", terminalAddress, code.getTerminalCommitment());
            return true;
        } else {
            LOGGER.debug("Visual code does not contain terminal details");
            return false;
        }
    }

    /**
     * Add the terminal details from a visual code to an intent.
     *
     * @param intent the intent
     * @param code   the visual code
     * @throws InvalidVisualCodeException if the terminal details are not present
     */
    public static void putTerminalDetails(Intent intent, WithTerminalDetails code)
        throws InvalidVisualCodeException {
        if (!putTerminalDetailsIfPresent(intent, code)) {
            throw new InvalidVisualCodeException("Visual code does not contain terminal details");
        }
    }

    /**
     * The main method of this class. Given the JSON string of a visual code (known as "QR text"),
     * make an Intent that takes you to the Activity associated with this code type, with all the
     * necessary extras set.
     *
     * @param context          Calling context, needed to make an Intent.
     * @param json             The QR text; the JSON content of the code.
     * @param allowedTypes     Set of types of code that are allowed. If the code's type is not in this
     *                         set, a WrongCodeTypeException is raised.
     * @param startedForResult If the caller is an Activity, whether that Activity was started for
     *                         a result. This affects some properties of the generated Intent. Specifically, passing
     *                         {@code true} will NOT set the Intent's next activity class, for it is assumed that
     *                         the Activity's parent will take appropriate action itself.
     * @return An Intent, todo: describe it!
     * @throws InvalidVisualCodeException if the JSON is malformed, or does not represent a valid
     *                                    Pico visual code.
     * @throws WrongCodeTypeException     if the code represented by the JSON is not of a type specified
     *                                    in allowedTypes.
     */
    public Intent getIntent(Context context, String json, EnumSet<CodeType> allowedTypes,
                            boolean startedForResult)
        throws InvalidVisualCodeException, WrongCodeTypeException, JsonParseException {
        // deserialise the JSON string into a VisualCode object
        final AndroidVisualCode code = deserialiseJson(json);

        // check that this type of code is allowed
        final CodeType type = code.getCodeType();
        if (!allowedTypes.contains(type)) {
            LOGGER.debug(type.name() + " codes not allowed");
            throw new WrongCodeTypeException(type);
        }

        return code.createIntent(context, startedForResult);
    }

    /**
     * Deserialises a JSON string into a {@link VisualCode} object.
     *
     * @param json The JSON string to deserialise.
     * @return The {@link VisualCode} object represented by the JSON.
     * @throws InvalidVisualCodeException if the given string is not valid JSON or does not describe
     *                                    a valid {@link VisualCode}.
     */
    @NonNull
    public static AndroidVisualCode deserialiseJson(String json) throws InvalidVisualCodeException {
        final VisualCode code = gson.fromJson(json, VisualCode.class);
        if (code == null || !code.isValid()) {
            throw new InvalidVisualCodeException();
        }
        // check that the proceeding cast is safe
        if (!(code instanceof AndroidVisualCode)) {
            throw new InvalidVisualCodeException();
        }
        return (AndroidVisualCode) code;
    }

}
