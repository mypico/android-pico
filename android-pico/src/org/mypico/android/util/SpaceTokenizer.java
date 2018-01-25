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


/*
 * Copyright (C) 2007 The Android Open Source Project
 * Modifications (CommaTokenizer to SpaceTokenizer) copyright (C) 2014 University of Cambridge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mypico.android.util;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.MultiAutoCompleteTextView;

/**
 * Tokenise a word list, with space used as a delimeter.
 *
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 */
public final class SpaceTokenizer implements MultiAutoCompleteTextView.Tokenizer {

    @Override
    public int findTokenStart(final CharSequence text, final int cursor) {
        int i = cursor;
        // step back until we're either at the beginning of the string
        // or the previous char is a space
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        // Move forward through spaces to the start of the token
        while (i < cursor && (text.charAt(i) == ' ')) {
            i++;
        }
        return i;
    }

    @Override
    public int findTokenEnd(final CharSequence text, final int cursor) {
        int i = cursor;
        int len = text.length();
        while (i < len) {
            if (text.charAt(i) == ' ') {
                return i;
            } else {
                i++;
            }
        }
        return len;
    }

    @Override
    public CharSequence terminateToken(final CharSequence text) {
        int i = text.length();
        while (i > 0 && (text.charAt(i - 1) == ' ')) {
            i--;
        }
        if (i > 0 && (text.charAt(i - 1) == ' ')) {
            return text;
        } else {
            if (text instanceof Spanned) {
                SpannableString sp = new SpannableString(text + " ");
                TextUtils.copySpansFrom((Spanned) text, 0, text.length(),
                    Object.class, sp, 0);
                return sp;
            } else {
                return text + " ";
            }
        }
    }
}