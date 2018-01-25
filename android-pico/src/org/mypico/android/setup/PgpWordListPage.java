package org.mypico.android.setup;

import com.example.android.wizardpager.wizard.model.ModelCallbacks;
import com.example.android.wizardpager.wizard.model.Page;
import com.example.android.wizardpager.wizard.model.ReviewItem;

import java.util.ArrayList;

/**
 * Base class for the PGP word input/output pages. Provides an implementation of
 * {@link #getReviewItems} that takes the secret word string and arranges it two words per line.
 *
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see PgpWordListInputPage
 * @see PgpWordListOutputPage
 */
public abstract class PgpWordListPage extends Page {

    protected PgpWordListPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
        // get the words and bail out if they're not there
        final String words = mData.getString(SIMPLE_DATA_KEY);
        if (words == null)
            return;
        // rearrange so there are two words per line, for consistent presentation of the words
        final String prettyWords = twoWordsPerLine(words);
        dest.add(new ReviewItem(getTitle(), prettyWords, getKey()));
    }

    /**
     * Split the words and regroup so that there are at most two per line.
     *
     * @param words The words, as a space-delimited string.
     * @return The same words, but with line breaks after every second word.
     */
    private static String twoWordsPerLine(String words) {
        final String[] splitWords = words.split("\\s+");
        String twoPerLine = splitWords[0] + " " + splitWords[1];
        int i;
        for (i = 2; i < splitWords.length - 1; i += 2)
            twoPerLine += "\n" + splitWords[i] + " " + splitWords[i + 1];
        if (i < splitWords.length) // case when there is an odd number of words
            twoPerLine += "\n" + splitWords[i];
        return twoPerLine;
    }

}
