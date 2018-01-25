package org.mypico.android.bluetooth;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;

import org.mypico.android.core.VisualCodeIntentGenerator;
import org.mypico.android.core.visualcode.AndroidVisualCode;
import org.mypico.android.core.visualcode.CodeType;
import org.mypico.android.data.SafeService;
import org.mypico.android.db.DbHelper;
import org.mypico.jpico.data.service.Service;
import org.mypico.jpico.db.DbDataAccessor;
import org.mypico.jpico.visualcode.InvalidVisualCodeException;
import org.mypico.jpico.visualcode.KeyAuthenticationVisualCode;
import org.mypico.jpico.visualcode.VisualCode;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Represents a nearby device that has signalled its availablity for logging in with Pico.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public class AvailableBluetoothLogin {

    /// the JSON string received from the device
    @NonNull
    public final String json;
    /// the service's commitment
    @Nullable
    public final byte[] commitment;
    /// if a pairing with the service exists, its name, otherwise null
    @Nullable
    public final String name;
    /// the Intent that will authenticate to this service
    @NonNull
    public final Intent intent;

    public AvailableBluetoothLogin(@NonNull String json, @Nullable byte[] commitment,
                                   @Nullable String name, @NonNull Intent intent) {
        this.json = json;
        this.commitment = commitment;
        this.name = name;
        this.intent = intent;
    }

    @Override
    public String toString() {
        return String.format("{login to %s: %s}", name, json);
    }

    /**
     * Parse a JSON string (i.e. QR code text) and do some magic to get the service's name and
     * create an {@link Intent} that will authenticate to it.
     *
     * @param context {@link Context} used to produce an {@link Intent}.
     * @param json    The JSON string received over Bluetooth from the service.
     * @return An {@code AvailableBluetoothLogin} as described by the JSON string.
     */
    @Nullable
    public static AvailableBluetoothLogin fromJson(Context context, String json) {
        final AndroidVisualCode code;
        final Intent intent;
        final byte[] serviceCommitment;
        final String serviceName;

        // Here, rather than use VisualCodeIntentGenerator#getIntent, we do the deserialisation and
        // intent creation as separate steps in order to get the deserialised VisualCode object.

        // deserialise the JSON
        try {
            code = VisualCodeIntentGenerator.deserialiseJson(json);
        } catch (InvalidVisualCodeException e) {
            return null;
        }
        // only authentication codes are allowed for an "available login"
        if (code.getCodeType() != CodeType.AUTH)
            return null;
        // create the authentication Intent for this login
        try {
            intent = code.createIntent(context, false);
        } catch (InvalidVisualCodeException e) {
            return null;
        }

        // if it's a key auth code, look up its pairing to get the service name
        if (code instanceof KeyAuthenticationVisualCode) {
            // get the service from the code
            final KeyAuthenticationVisualCode kaCode = (KeyAuthenticationVisualCode) code;
            final SafeService safeService = SafeService.fromVisualCode(kaCode);
            serviceCommitment = safeService.getCommitment();
            // perform the database lookup to find its name
            serviceName = getServiceName(context, safeService);
        } else {
            serviceCommitment = null;
            serviceName = null;
        }

        return new AvailableBluetoothLogin(json, serviceCommitment, serviceName, intent);
    }

    /**
     * Helper function that looks up a service's name from the database.
     *
     * @param context     {@link Context} for accessing the database.
     * @param safeService The service whose name to get.
     * @return The name of the service, or {@code null} if there is no record for this service.
     */
    @Nullable
    private static String getServiceName(Context context, SafeService safeService) {
        final OrmLiteSqliteOpenHelper helper = OpenHelperManager.getHelper(context, DbHelper.class);
        try {
            final DbDataAccessor accessor = new DbDataAccessor(helper.getConnectionSource());
            final Service service = safeService.getService(accessor);
            if (service != null) {
                return service.getName();
            } else {
                return null;
            }
        } catch (SQLException | IOException e) {
            return null;
        }
    }

}
