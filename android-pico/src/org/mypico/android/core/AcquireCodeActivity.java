/*
 * (C) Copyright Cambridge Authentication Ltd, 2017
 *
 * This file is part of android-pico.
 *
 * android-pico is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * android-pico is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with android-pico. If not, see
 * <http://www.gnu.org/licenses/>.
 */


package org.mypico.android.core;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.EnumSet;

import org.mypico.android.R;
import org.mypico.android.core.InvalidCodeDialog.InvalidCodeCallbacks;
import org.mypico.android.core.visualcode.CodeType;
import org.mypico.android.data.SafeService;
import org.mypico.android.pairing.ChooseKeyPairingActivity;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

import com.google.gson.JsonParseException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;

/**
 * Activity for acquiring and parsing visual codes. This activity can be used in
 * two ways:
 * <p>
 * <p>
 * <ul>
 * <li>If started for a result (
 * {@link Activity#startActivityForResult(Intent, int)}) it will return the
 * parsed data contained in the visual code in the result intent.
 * <li>If a result is not requested ({@link Activity#startActivity(Intent)}) it
 * will start another activity appropriate for the type of visual code scanned.
 * In this case the parsed data from the visual code will be attached to the
 * intent which starts that next activity.
 * </ul>
 *
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */
final public class AcquireCodeActivity extends Activity implements InvalidCodeCallbacks {

    // extras that specific what type of code is allowed to be scanned
    public final static String AUTH_ALLOWED =
        AcquireCodeActivity.class.getCanonicalName() + "authAllowed";
    public final static String PAIRING_ALLOWED =
        AcquireCodeActivity.class.getCanonicalName() + "pairingAllowed";
    public final static String TERMINAL_PAIRING_ALLOWED =
        AcquireCodeActivity.class.getCanonicalName() + "terminalPairingAllowed";
    public final static String NO_MENU =
        "org.mypico.android.core.NO_MENU";

    /**
     * String for getting boolean flag from another app specifying not to scan a
     * QR code, but to get details from that app
     */
    public static final String DATA_FROM_EXTERNAL_APP =
        "DATA_FROM_EXTERNAL_APP";

    // TODO change these to have the above form. Requires checking that nowhere
    // else is using the current key values.
    /**
     * Key for the service extra. This extra is a {@link SafeService}.
     */
    public final static String SERVICE =
        SafeService.class.getCanonicalName();


    private static final Logger LOGGER =
        LoggerFactory.getLogger(AcquireCodeActivity.class.getSimpleName());
    private static final int CAPTURE_CODE = 0;
    private static final String IS_SCANNING = "isScanning";
    private static final String INVALID_CODE_DIALOG = "invalidCodeDialog";

    private boolean isScanning;
    private boolean startedForResult;
    private EnumSet<CodeType> allowedTypes;

    /**
     * AcquireCodeActivity lifecycle method. Restores the previously acquired
     * visual code data (if stored) and acquires a visual code.
     *
     * @param savedInstanceState the Bundle to restore the visual code from.
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set a layout so that when this activity appears in the "recent apps"
        // menu it looks
        // reasonable
        setContentView(R.layout.activity_acquire_code);

        // Determine whether this activity has been started for a result
        startedForResult = (getCallingActivity() != null);

        // Determine the allowed types based on the extras in the received
        // intent
        allowedTypes = EnumSet.noneOf(CodeType.class);
        if (getIntent().getBooleanExtra(AUTH_ALLOWED, false)) {
            allowedTypes.add(CodeType.AUTH);
        }
        if (getIntent().getBooleanExtra(PAIRING_ALLOWED, false)) {
            allowedTypes.add(CodeType.PAIRING);
        }
        if (getIntent().getBooleanExtra(TERMINAL_PAIRING_ALLOWED, false)) {
            allowedTypes.add(CodeType.TERMINAL_PAIRING);
        }
        if (allowedTypes.isEmpty()) {
            // ...if none were set, default to allowing auth and pairing codes
            allowedTypes.add(CodeType.AUTH);
            allowedTypes.add(CodeType.PAIRING);
        }

        // Restore isScanning flag from saved state
        if (savedInstanceState != null) {
            isScanning = savedInstanceState.getBoolean(IS_SCANNING, false);
        } else {
            isScanning = false;
        }

        // get data straight from another app instead of QR code
        if (getIntent().getBooleanExtra(DATA_FROM_EXTERNAL_APP, false)) {
            LOGGER.debug("Getting data from external application");
            Intent intent = new Intent(getIntent());
            String serviceCommitment = getIntent().getStringExtra("EXTERNAL_SERVICE_COMMITMENT");
            byte[] decodedServiceCommitment = Base64.decode(serviceCommitment);
            Uri address = Uri.parse(getIntent().getStringExtra("EXTERNAL_SERVICE_ADDRESS"));
            SafeService service = new SafeService(null, decodedServiceCommitment, address, null);
            intent.putExtra(SERVICE, service);

            // This activity was just started and is not supposed to return
            // a result, in this case we just start the activity specified
            // in the intent (as set in intentFromCode)
            intent.setClass(this, ChooseKeyPairingActivity.class);
            startActivity(intent);
            LOGGER.debug("AcquireCodeActivity is finishing");
            finish();
        } else {
            // ...then start scanning if isScanning is not set
            if (!isScanning) {
                acquireCode();
            }
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean(IS_SCANNING, isScanning);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    final Intent resultIntent) {
        if (requestCode == CAPTURE_CODE) {
            if (resultCode == RESULT_OK) {
                LOGGER.debug("PicoCaptureActivity returned a result");

                // Get results as a string (JSON serialisation)
                final String codeContent = resultIntent.getStringExtra(PicoCaptureActivity.SCAN_RESULT_EXTRA);

                if (!isNullOrEmpty(codeContent)) {
                    LOGGER.trace("QR code contents: " + codeContent);
                    try {
                        Intent intent = new VisualCodeIntentGenerator()
                            .getIntent(this, codeContent, allowedTypes, startedForResult);
                        if (intent != null) {
                            if (startedForResult) {
                                // This activity was started for result, so set the result data
                                // (the intent) and finish()
                                setResult(Activity.RESULT_OK, intent);
                                finish();
                            } else {
                                // This activity was just started and is not supposed to return
                                // a result, in this case we just start the activity specified
                                // in the intent.
                                startActivity(intent);
                                finish();
                            }
                        }

                    } catch (JsonParseException e) {
                        // In this case the QR code could not be parsed by the VisualCodeGson
                        // instance and is therefore not a valid Pico QR code.
                        LOGGER.warn("Not a Pico visual code", e);
                        InvalidCodeDialog.getInstance(R.string.not_a_pico_qr_code)
                            .show(getFragmentManager(), INVALID_CODE_DIALOG);

                    } catch (InvalidVisualCodeException e) {
                        // In this case the QR code be parsed as a valid type of Pico QR code, but
                        // is still somehow invalid, e.g. bad signature, missing fields
                        LOGGER.warn("Invalid Pico visual code", e);
                        InvalidCodeDialog.getInstance(R.string.invalid_pico_qr_code)
                            .show(getFragmentManager(), INVALID_CODE_DIALOG);

                    } catch (VisualCodeIntentGenerator.WrongCodeTypeException e) {
                        // Visual code type allowed
                        final String message = getString(R.string.wrong_qr_type,
                            allowedTypesString(), e.wrongTypeWithArticle(this));
                        InvalidCodeDialog.getInstance(message)
                            .show(getFragmentManager(), INVALID_CODE_DIALOG);
                    }

                } else {
                    LOGGER.warn("Result is null or empty!");
                    // This is really an error condition, but as it originates with the ZXing
                    // activity (and hopefully isn't actually possible), we will silently handle it
                    // by just finishing this activity as if it were cancelled.
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                }

            } else if (resultCode == RESULT_CANCELED) {
                LOGGER.debug("PicoCaptureActivity was cancelled, finishing activity");
                if (startedForResult) {
                    setResult(Activity.RESULT_CANCELED);
                }
                finish();
            }
        }
    }

    /**
     * Returns a human-readable string that expresses the types of code that are allowed in the
     * current context of acquiring a QR code.
     *
     * @return A string capturing the types of QR code that are allowed.
     */
    private String allowedTypesString() {
        final int n = allowedTypes.size();
        final CodeType[] types = new CodeType[n];
        allowedTypes.toArray(types);

        if (types.length == 1) {
            return getString(types[0].withArticleResId());
        } else if (types.length == 2) {
            return getString(R.string.or_2, getString(types[0].withArticleResId()),
                getString(types[1].withArticleResId()));
        } else if (types.length == 3) {
            return getString(R.string.or_3, getString(types[0].withArticleResId()),
                getString(types[1].withArticleResId()), getString(types[2].withArticleResId()));
        } else {
            return getString(R.string.nothing);
        }
    }

    /**
     * Launch {@link PicoCaptureActivity} to acquire the visual code. Result is received via
     * onActivityResult().
     */
    private void acquireCode() {
        LOGGER.debug("Starting PicoCaptureActivity...");

        // Set isScanning flag
        isScanning = true;

        final Intent intent = new Intent(this, PicoCaptureActivity.class);
        intent.putExtra(NO_MENU, getIntent().getBooleanExtra(NO_MENU, false));
        intent.setAction(PicoCaptureActivity.ACTION_SCAN);

        // Start
        startActivityForResult(intent, CAPTURE_CODE);
    }

    @Override
    public void onScanAnother() {
        acquireCode();
    }

    @Override
    public void onCancel() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
