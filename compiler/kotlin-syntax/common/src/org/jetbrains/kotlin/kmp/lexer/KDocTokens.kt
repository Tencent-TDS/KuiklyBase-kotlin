/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.kmp.lexer

import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.asSyntaxElementTypeSet

object KDocTokens {
    const val KDOC_ID: Int = 1
    const val START_II: Int = KDOC_ID + 1
    const val END_ID: Int = START_II + 1
    const val LEADING_ASTERISK_ID: Int = END_ID + 1
    const val TEXT_ID: Int = LEADING_ASTERISK_ID + 1
    const val CODE_BLOCK_TEXT_ID: Int = TEXT_ID + 1
    const val TAG_NAME_ID: Int = CODE_BLOCK_TEXT_ID + 1
    const val MARKDOWN_LINK_ID: Int = TAG_NAME_ID + 1
    const val MARKDOWN_ESCAPED_CHAR_ID: Int = TAG_NAME_ID + 1
    const val KDOC_LPAR_ID: Int = MARKDOWN_ESCAPED_CHAR_ID + 1
    const val KDOC_RPAR_ID: Int = KDOC_LPAR_ID + 1

    val KDOC: KDocToken = KDocToken("KDoc", KDOC_ID)

    val START: KDocToken = KDocToken("KDOC_START", START_II)
    val END: KDocToken = KDocToken("KDOC_END", END_ID)
    val LEADING_ASTERISK: KDocToken = KDocToken("KDOC_LEADING_ASTERISK", LEADING_ASTERISK_ID)

    val TEXT: KDocToken = KDocToken("KDOC_TEXT", TEXT_ID)
    val CODE_BLOCK_TEXT: KDocToken = KDocToken("KDOC_CODE_BLOCK_TEXT", CODE_BLOCK_TEXT_ID)

    val TAG_NAME: KDocToken = KDocToken("KDOC_TAG_NAME", TAG_NAME_ID)
    val MARKDOWN_LINK: KDocToken = KDocToken("KDOC_MARKDOWN_LINK", MARKDOWN_LINK_ID)
    val MARKDOWN_ESCAPED_CHAR: KDocToken = KDocToken("KDOC_MARKDOWN_ESCAPED_CHAR", MARKDOWN_ESCAPED_CHAR_ID)

    val KDOC_LPAR: KDocToken = KDocToken("KDOC_LPAR", KDOC_LPAR_ID)
    val KDOC_RPAR: KDocToken = KDocToken("KDOC_RPAR", KDOC_RPAR_ID)

    @Suppress("unused")
    val KDOC_HIGHLIGHT_TOKENS: SyntaxElementTypeSet = listOf(
        START,
        END,
        LEADING_ASTERISK,
        TEXT,
        CODE_BLOCK_TEXT,
        MARKDOWN_LINK,
        MARKDOWN_ESCAPED_CHAR,
        KDOC_LPAR,
        KDOC_RPAR
    ).asSyntaxElementTypeSet()

    val CONTENT_TOKENS: SyntaxElementTypeSet = listOf(
        TEXT,
        CODE_BLOCK_TEXT,
        TAG_NAME,
        MARKDOWN_LINK,
        MARKDOWN_ESCAPED_CHAR,
        KDOC_LPAR,
        KDOC_RPAR
    ).asSyntaxElementTypeSet()
}