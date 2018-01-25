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


package org.mypico.android.setup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

import com.example.android.wizardpager.wizard.model.Page;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.R;

/**
 * Fragment for inputting the secret used to encrypt the backup of the Pico pairings and
 * service database as a set of words from the PGP word list.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Seb Aebischer <seb.aebischer@cl.cam.ac.uk>
 * @see R.layout#fragment_pgp_word_list_input
 */
public class PgpWordListInputFragment extends CustomPageFragment {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(PgpWordListInputFragment.class.getSimpleName());

    private static final String ARG_KEY = "key";
    private static final int NUM_PGP_WORDS = 12;
    private static final String SAVED_STATE_WORDS_KEY = "PgpWordListInputFragment.pgpWords";

    private GridView myGrid;
    private TextView errorMessage;

    private List<String> pgpEvenWords, pgpOddWords;
    private ArrayAdapter<String> pgpEvenWordsAdatper;
    private ArrayAdapter<String> pgpOddWordsAdatper;
    private InputFilter[] wordEditorFilters;
    private final String[] pgpWords = new String[NUM_PGP_WORDS];
    private final boolean[] pgpWordIsValid = new boolean[NUM_PGP_WORDS];
    private int lastPosition;

    private int defaultColour;
    private int validWordColour;
    private int invalidWordColour;

    /**
     * {@link InputFilter} applied to the word entry textboxes. All non-letter characters are
     * rejected.
     */
    private static class LettersFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            String result = source.subSequence(0, start).toString();
            boolean changed = false;
            // filter out non-letter characters
            for (int i = start; i < end; i++) {
                char k = source.charAt(i);
                if (!Character.isLetter(k)) {
                    // skip it
                    changed = true;
                } else {
                    result += k;
                }
            }
            // if we removed anything, return the modified string, otherwise return null
            return changed ? result : null;
        }
    }

    /**
     * Tokenizer that takes the entire text as the token. This is the sensible mode of operation
     * when given that it's one word per textbox.
     */
    private static class NullTokenizer implements MultiAutoCompleteTextView.Tokenizer {
        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            return 0;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            return text.length();
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            return text;
        }
    }

    /**
     * Adapter for the word input {@link GridView}. There are always {@link #NUM_PGP_WORDS} items,
     * which display as autocopmleting text entry fields (using {@code layout/pgp_word_entry_cell}
     * for the layout). Various aspects of word entry are also handled within this class, including
     * calling {@link #onEnteredValidWord}.
     */
    private class PgpWordGridAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return NUM_PGP_WORDS;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final View view;
            if (convertView == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.pgp_word_entry_cell, parent, false);
            } else {
                view = convertView;
            }

            // set the numeral
            final TextView numeral = (TextView) view.findViewById(R.id.numeral);
            numeral.setText(getString(R.string.fragment_pgp_word_list__numeral_format, position + 1));

            // set up the autocomplete list field
            final MultiAutoCompleteTextView wordBox = (MultiAutoCompleteTextView)
                view.findViewById(R.id.word_box);
            final boolean isEven = (position & 1) == 0;
            wordBox.setThreshold(1);
            wordBox.setTokenizer(new NullTokenizer());
            wordBox.setAdapter(isEven ? pgpEvenWordsAdatper : pgpOddWordsAdatper);
            wordBox.setFilters(wordEditorFilters);
            // if it's the last field, make the keyboard say "Done" instead of "Next"
            wordBox.setImeOptions((position == getCount() - 1) ?
                EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
            // remove any existing text change listener -- important to do this BEFORE setting text!
            Object oldListener = wordBox.getTag(R.id.tag_textwatcher);
            if (oldListener != null)
                wordBox.removeTextChangedListener((TextWatcher) oldListener);
            // set the word (note: may be null, but this is okay)
            final String word = pgpWords[position];
            wordBox.setText(word);
            // set the colour (see onFocusChange(false))
            final List<String> wordList = isEven ? pgpEvenWords : pgpOddWords;
            final boolean isValid = wordList.contains(word);
            if (!wordBox.hasFocus())
                wordBox.setTextColor(isValid ? validWordColour : invalidWordColour);

            // create the text changed listener for this box
            final TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    final String word = s.toString();
                    final boolean isValid = wordList.contains(word);
                    // update the entry in the word list
                    LOGGER.debug("Set word in position " + position + " to \"" + word + "\"");
                    pgpWords[position] = word;
                    pgpWordIsValid[position] = isValid;
                    if (isValid) {
                        // if it's valid, see if we can proceed
                        onEnteredValidWord(wordBox);
                        // make it green
                        wordBox.setTextColor(validWordColour);
                    } else {
                        // otherwise we definitely can't
                        disableNextPage();
                        // make sure the colour is not green (e.g. backspace after valid word)
                        wordBox.setTextColor(defaultColour);
                    }
                }
            };
            // assign it and keep the reference in a tag
            wordBox.addTextChangedListener(watcher);
            wordBox.setTag(R.id.tag_textwatcher, watcher);

            // validate the word when the textbox loses focus
            wordBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    final String word = wordBox.getText().toString();
                    final boolean isValid = wordList.contains(word);
                    if (!hasFocus) {
                        // give visual feedback about whether the word is valid
                        wordBox.setTextColor(isValid ? validWordColour : invalidWordColour);
                        // update the error message as necessary
                        updateErrorMessage();
                    } else {
                        // while they're editing, use the default colour, unless it's valid
                        wordBox.setTextColor(isValid ? validWordColour : defaultColour);
                        // make sure this cell is visible
                        safeScrollToPosition(position);
                    }
                }
            });

            // The onFocusChange(true) doesn't happen if it's already got focus, and we may need to
            // scroll again. This solves the pathological case: user gives focus to box 12, keyboard
            // appears, user dismisses keyboard, clicks again so keyboard reappears
            wordBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    safeScrollToPosition(position);
                }
            });

            return view;
        }
    }

    /**
     * Called when a valid word has been entered in one of the word entry boxes.
     *
     * @param v The textbox that the word was entered into.
     */
    private void onEnteredValidWord(MultiAutoCompleteTextView v) {
        // don't show the suggestions list anymore (should happen automatically but doesn't)
        v.dismissDropDown();
        // also update the error message at this point, rather than wait for them to lose focus
        updateErrorMessage();

        // see whether we have all the words now
        for (int i = 0; i < NUM_PGP_WORDS; i++) {
            if (!pgpWordIsValid[i])
                return;
        }
        // we do; hide the keyboard and enable the Next button
        hideSoftKeyboard(v);
        enableNextPage();
    }

    /**
     * Hide the keyboard.
     *
     * @param view The view showing the keyboard.
     */
    private void hideSoftKeyboard(View view) {
        final InputMethodManager imm = (InputMethodManager)
            getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * Enable the Next button in the wizard so that the user can proceed to restore.
     * This should be done when the user has entered twelve valid words.
     */
    private void enableNextPage() {
        mPage.getData().putString(Page.SIMPLE_DATA_KEY, TextUtils.join(" ", pgpWords));
        mPage.notifyDataChanged();
    }

    /**
     * Disable the Next button in the wizard.
     * This should be done if the user has entered an invalid set of words.
     */
    private void disableNextPage() {
        Bundle pageData = mPage.getData();
        if (!TextUtils.isEmpty(pageData.getString(Page.SIMPLE_DATA_KEY))) {
            pageData.putString(Page.SIMPLE_DATA_KEY, "");
            mPage.notifyDataChanged();
        }
    }

    /**
     * Updates the error message displayed at the top of the page. If all words are valid (ignoring
     * empty ones), no message is displayed. Otherwise, if there's an invalid word, the message is
     * shown.
     */
    private void updateErrorMessage() {
        // count how many non-empty invalid words there are
        int numInvalid = 0;
        for (int i = 0; i < NUM_PGP_WORDS; i++) {
            if (pgpWords[i] != null && pgpWords[i].length() != 0 && !pgpWordIsValid[i])
                numInvalid++;
        }

        // hide the message if there are no invalid words
        errorMessage.setVisibility(numInvalid == 0 ? View.GONE : View.VISIBLE);
    }

    /**
     * Scroll the word entry grid to show the specified cell. This takes some additional precautions
     * including a workaround for if the keyboard shows when they click (in which case the cell may
     * be obscured by the keyboard), and also making sure that the row below is visible if we're
     * going down, so that these cells (and their textboxes) are instantiated; otherwise the Next
     * button on the keyboard may not work.
     */
    private void safeScrollToPosition(final int position) {
        final int scrollTo = (lastPosition < position) ?
            Math.min(position + 2, NUM_PGP_WORDS - 1) :
            position;
        lastPosition = position;
        // scroll there now
        myGrid.smoothScrollToPosition(scrollTo);
        // do it again after a short delay in case the keyboard is about to appear
        myGrid.postDelayed(new Runnable() {
            @Override
            public void run() {
                myGrid.smoothScrollToPosition(scrollTo);
            }
        }, 250);
    }


    /**
     * Factory for crearing a new <code>PgpWordListInputFragment</code> fragment.
     *
     * @param key The key to use for the word list.
     * @return The <code>PgpWordListInputFragment</code> created.
     */
    public static PgpWordListInputFragment newInstance(final String key) {
        // Verify the method's preconditions
        checkNotNull(key);

        final PgpWordListInputFragment frag = new PgpWordListInputFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Resources res = getResources();

        // get colour resources
        defaultColour = res.getColor(android.R.color.primary_text_dark);
        validWordColour = res.getColor(R.color.success_green);
        invalidWordColour = res.getColor(R.color.failure_red);

        // load the PGP word lists
        pgpEvenWords = Arrays.asList(res.getStringArray(R.array.pgp_word_list_even));
        pgpOddWords = Arrays.asList(res.getStringArray(R.array.pgp_word_list_odd));
        // put them in adapters for the autocomplete fields
        pgpEvenWordsAdatper = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_list_item_1, pgpEvenWords);
        pgpOddWordsAdatper = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_list_item_1, pgpOddWords);

        // prepare filters for word entry
        wordEditorFilters = new InputFilter[1];
        wordEditorFilters[0] = new LettersFilter();

        // don't break if they rotate the screen
        setRetainInstance(true);

        // restore saved instance state if there is some
        if (savedInstanceState != null) {
            // saved words
            final String[] words = savedInstanceState.getStringArray(SAVED_STATE_WORDS_KEY);
            if (words != null) {
                for (int i = 0; i < NUM_PGP_WORDS; i++) {
                    // fill in this word
                    pgpWords[i] = words[i];
                    // and fill in whether it's valid
                    if ((i & 1) == 0) {
                        pgpWordIsValid[i] = pgpEvenWords.contains(pgpWords[i]);
                    } else {
                        pgpWordIsValid[i] = pgpOddWords.contains(pgpWords[i]);
                    }
                }
            }
        }

    }

    @SuppressLint("InflateParams")
    // "There are of course instances where you can truly justify a null parent during inflation, 
    // but they are few. One such instance occurs when you are inflating a custom layout to be
    // attached to an AlertDialog."
    // http://www.doubleencore.com/2013/05/layout-inflation-as-intended/
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_pgp_word_list_input, null);
        ((TextView) view.findViewById(android.R.id.title)).setText(mPage.getTitle());

        myGrid = (GridView) view.findViewById(R.id.pgpwordlistGridView);
        myGrid.setAdapter(new PgpWordGridAdapter());

        errorMessage = (TextView) view.findViewById(R.id.fragment_pgp_word_list_input__message);

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray(SAVED_STATE_WORDS_KEY, pgpWords);
    }

}