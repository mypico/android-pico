package org.mypico.android.core.visualcode;

import org.mypico.android.R;

/**
 * Enumeration of code types: authentication ({@link CodeType#AUTH}), pairing
 * ({@link CodeType#PAIRING}), or terminal pairing ({@link CodeType#TERMINAL_PAIRING}).
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 */

public enum CodeType {

    AUTH(R.string.a_login_qr_code),
    PAIRING(R.string.a_pairing_qr_code),
    TERMINAL_PAIRING(R.string.a_terminal_pairing_qr_code);

    private final int withArticleResId;

    CodeType(int withArticleResId) {
        this.withArticleResId = withArticleResId;
    }

    /**
     * Returns the resource id for the article.
     *
     * @return the resource id.
     */
    public int withArticleResId() {
        return withArticleResId;
    }

}
