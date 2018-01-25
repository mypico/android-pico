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


package org.mypico.android.terminal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import org.mypico.android.data.SafeLensPairing;
import org.mypico.android.data.SafeService;
import org.mypico.jpico.crypto.messages.PicoReauthMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.jpico.crypto.AuthToken;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.common.base.Optional;

/**
 * Attempts to write an authentication token (cookie) to a terminal using HTTP.
 * <p>
 * In practice this class isn't used and the HTTP channel is likely to be insecure. The current
 * implementation sends the {@see AuthToken} to the terminal in a {@link PicoReauthMessage} message,
 * as can be seen in {@link org.mypico.android.pairing.AuthenticateIntentService#authenticatePairing(SafeLensPairing, SafeService, String, String, Uri, byte[])}.
 * <p>
 * Given this it's not clear what this class is really intended to be used for.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
final public class HttpWriteAuthToken extends WriteAuthToken {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        HttpWriteAuthToken.class.getSimpleName());

    public static final String PARAM_NAME = "auth_token";
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    public static enum Method {
        GET,
        POST
    }

    private URL url;
    private Method method;
    private int timeout;

    /**
     * Constructor.
     *
     * @param context  The UI context.
     * @param fallback Intent to use in case writing the token over this channel fails.
     * @param url      The URL to write the token to.
     * @param method   The HTTP method to use (GET or POST). Default is POST.
     * @param timeout  Timeout after which to stop the write.
     */
    public HttpWriteAuthToken(Context context, Optional<Intent> fallback, URL url, Method method, int timeout) {
        super(context, fallback);

        this.url = url;
        this.method = method;
        this.timeout = timeout;
    }

    @Override
    protected boolean write(AuthToken token) {
        LOGGER.debug("Sending auth token to ", url.getHost());

        String data;
        try {
            data = PARAM_NAME + "=" + URLEncoder.encode(token.getFull(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 not supported", e);
        }

        HttpURLConnection connection;
        HttpURLConnection.setFollowRedirects(false);

        try {
            switch (method) {
                case GET:
                    URL requestUrl = null;
                    try {
                        requestUrl = new URL(url, "?" + data);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Unexpected MalformedURLException", e);
                    }
                    LOGGER.debug("GET {}", requestUrl);

                    connection = (HttpURLConnection) requestUrl.openConnection();
                    connection.setRequestMethod("GET");
                case POST:
                default:
                    LOGGER.debug("POST {}", url);

                    final byte[] postBytes = data.getBytes("UTF-8");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", CONTENT_TYPE);
                    connection.setRequestProperty("Content-Length", Integer.toString(postBytes.length));

                    connection.setDoOutput(true);
                    OutputStream os = null;
                    try {
                        os = connection.getOutputStream();
                        os.write(postBytes);
                        os.flush();
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                    }
            }
            LOGGER.trace("Request headers: {}", connection.getRequestProperties());

            connection.setReadTimeout(timeout);
            return (connection.getResponseCode() == HttpURLConnection.HTTP_OK);
        } catch (IOException e) {
            LOGGER.warn("IOException occured whilst writing auth token", e);
            return false;
        }
    }
}
