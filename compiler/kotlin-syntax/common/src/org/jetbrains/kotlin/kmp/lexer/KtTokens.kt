/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kmp.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.asSyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes

object KtTokens {
    const val INVALID_Id: Int = 0
    const val EOF_Id: Int = 1
    const val RESERVED_Id: Int = 2
    const val BLOCK_COMMENT_Id: Int = 3
    const val EOL_COMMENT_Id: Int = 4
    const val SHEBANG_COMMENT_Id: Int = 5
    const val INTEGER_LITERAL_Id: Int = 6
    const val FLOAT_LITERAL_Id: Int = 7
    const val CHARACTER_LITERAL_Id: Int = 8
    const val CLOSING_QUOTE_Id: Int = 9
    const val OPEN_QUOTE_Id: Int = 10
    const val REGULAR_STRING_PART_Id: Int = 11
    const val ESCAPE_SEQUENCE_Id: Int = 12
    const val SHORT_TEMPLATE_ENTRY_START_Id: Int = 13
    const val LONG_TEMPLATE_ENTRY_START_Id: Int = 14
    const val LONG_TEMPLATE_ENTRY_END_Id: Int = 15
    const val DANGLING_NEWLINE_Id: Int = 16
    const val PACKAGE_KEYWORD_Id: Int = 17
    const val AS_KEYWORD_Id: Int = 18
    const val TYPE_ALIAS_KEYWORD_Id: Int = 19
    const val CLASS_KEYWORD_Id: Int = 20
    const val THIS_KEYWORD_Id: Int = 21
    const val SUPER_KEYWORD_Id: Int = 22
    const val VAL_KEYWORD_Id: Int = 23
    const val VAR_KEYWORD_Id: Int = 24
    const val FUN_KEYWORD_Id: Int = 25
    const val FOR_KEYWORD_Id: Int = 26
    const val NULL_KEYWORD_Id: Int = 27
    const val TRUE_KEYWORD_Id: Int = 28
    const val FALSE_KEYWORD_Id: Int = 29
    const val IS_KEYWORD_Id: Int = 30
    const val IN_KEYWORD_Id: Int = 31
    const val THROW_KEYWORD_Id: Int = 32
    const val RETURN_KEYWORD_Id: Int = 33
    const val BREAK_KEYWORD_Id: Int = 34
    const val CONTINUE_KEYWORD_Id: Int = 35
    const val OBJECT_KEYWORD_Id: Int = 36
    const val IF_KEYWORD_Id: Int = 37
    const val TRY_KEYWORD_Id: Int = 38
    const val ELSE_KEYWORD_Id: Int = 39
    const val WHILE_KEYWORD_Id: Int = 40
    const val DO_KEYWORD_Id: Int = 41
    const val WHEN_KEYWORD_Id: Int = 42
    const val INTERFACE_KEYWORD_Id: Int = 43
    const val TYPEOF_KEYWORD_Id: Int = 44
    const val AS_SAFE_Id: Int = 45
    const val IDENTIFIER_Id: Int = 46
    const val FIELD_IDENTIFIER_Id: Int = 47
    const val LBRACKET_Id: Int = 48
    const val RBRACKET_Id: Int = 49
    const val LBRACE_Id: Int = 50
    const val RBRACE_Id: Int = 51
    const val LPAR_Id: Int = 52
    const val RPAR_Id: Int = 53
    const val DOT_Id: Int = 54
    const val PLUSPLUS_Id: Int = 55
    const val MINUSMINUS_Id: Int = 56
    const val MUL_Id: Int = 57
    const val PLUS_Id: Int = 58
    const val MINUS_Id: Int = 59
    const val EXCL_Id: Int = 60
    const val DIV_Id: Int = 61
    const val PERC_Id: Int = 62
    const val LT_Id: Int = 63
    const val GT_Id: Int = 64
    const val LTEQ_Id: Int = 65
    const val GTEQ_Id: Int = 66
    const val EQEQEQ_Id: Int = 67
    const val ARROW_Id: Int = 68
    const val DOUBLE_ARROW_Id: Int = 69
    const val EXCLEQEQEQ_Id: Int = 70
    const val EQEQ_Id: Int = 71
    const val EXCLEQ_Id: Int = 72
    const val EXCLEXCL_Id: Int = 73
    const val ANDAND_Id: Int = 74
    const val AND_Id: Int = 75
    const val OROR_Id: Int = 76
    const val SAFE_ACCESS_Id: Int = 77
    const val ELVIS_Id: Int = 78
    const val QUEST_Id: Int = 79
    const val COLONCOLON_Id: Int = 80
    const val COLON_Id: Int = 81
    const val SEMICOLON_Id: Int = 82
    const val DOUBLE_SEMICOLON_Id: Int = 83
    const val RANGE_Id: Int = 84
    const val RANGE_UNTIL_Id: Int = 85
    const val EQ_Id: Int = 86
    const val MULTEQ_Id: Int = 87
    const val DIVEQ_Id: Int = 88
    const val PERCEQ_Id: Int = 89
    const val PLUSEQ_Id: Int = 90
    const val MINUSEQ_Id: Int = 91
    const val NOT_IN_Id: Int = 92
    const val NOT_IS_Id: Int = 93
    const val HASH_Id: Int = 94
    const val AT_Id: Int = 95
    const val COMMA_Id: Int = 96
    const val EOL_OR_SEMICOLON_Id: Int = 97
    const val FILE_KEYWORD_Id: Int = 98
    const val FIELD_KEYWORD_Id: Int = 99
    const val PROPERTY_KEYWORD_Id: Int = 100
    const val RECEIVER_KEYWORD_Id: Int = 101
    const val PARAM_KEYWORD_Id: Int = 102
    const val SETPARAM_KEYWORD_Id: Int = 103
    const val DELEGATE_KEYWORD_Id: Int = 104
    const val IMPORT_KEYWORD_Id: Int = 105
    const val WHERE_KEYWORD_Id: Int = 106
    const val BY_KEYWORD_Id: Int = 107
    const val GET_KEYWORD_Id: Int = 108
    const val SET_KEYWORD_Id: Int = 109
    const val CONSTRUCTOR_KEYWORD_Id: Int = 110
    const val INIT_KEYWORD_Id: Int = 111
    const val CONTEXT_KEYWORD_Id: Int = 112
    const val ABSTRACT_KEYWORD_Id: Int = 113
    const val ENUM_KEYWORD_Id: Int = 114
    const val CONTRACT_KEYWORD_Id: Int = 115
    const val OPEN_KEYWORD_Id: Int = 116
    const val INNER_KEYWORD_Id: Int = 117
    const val OVERRIDE_KEYWORD_Id: Int = 118
    const val PRIVATE_KEYWORD_Id: Int = 119
    const val PUBLIC_KEYWORD_Id: Int = 120
    const val INTERNAL_KEYWORD_Id: Int = 121
    const val PROTECTED_KEYWORD_Id: Int = 122
    const val CATCH_KEYWORD_Id: Int = 123
    const val OUT_KEYWORD_Id: Int = 124
    const val VARARG_KEYWORD_Id: Int = 125
    const val REIFIED_KEYWORD_Id: Int = 126
    const val DYNAMIC_KEYWORD_Id: Int = 127
    const val COMPANION_KEYWORD_Id: Int = 128
    const val SEALED_KEYWORD_Id: Int = 129
    const val FINALLY_KEYWORD_Id: Int = 130
    const val FINAL_KEYWORD_Id: Int = 131
    const val LATEINIT_KEYWORD_Id: Int = 132
    const val DATA_KEYWORD_Id: Int = 133
    const val VALUE_KEYWORD_Id: Int = 134
    const val INLINE_KEYWORD_Id: Int = 135
    const val NOINLINE_KEYWORD_Id: Int = 136
    const val TAILREC_KEYWORD_Id: Int = 137
    const val EXTERNAL_KEYWORD_Id: Int = 138
    const val ANNOTATION_KEYWORD_Id: Int = 139
    const val CROSSINLINE_KEYWORD_Id: Int = 140
    const val OPERATOR_KEYWORD_Id: Int = 141
    const val INFIX_KEYWORD_Id: Int = 142
    const val CONST_KEYWORD_Id: Int = 143
    const val SUSPEND_KEYWORD_Id: Int = 144
    const val EXPECT_KEYWORD_Id: Int = 145
    const val ACTUAL_KEYWORD_Id: Int = 146
    const val INTERPOLATION_PREFIX_Id: Int = 147
    const val ALL_KEYWORD_Id: Int = 148

    val EOF: KtToken = KtToken("EOF", EOF_Id)

    val RESERVED: KtToken = KtToken("RESERVED", RESERVED_Id)

    val BLOCK_COMMENT: KtToken = KtToken("BLOCK_COMMENT", BLOCK_COMMENT_Id)
    val EOL_COMMENT: KtToken = KtToken("EOL_COMMENT", EOL_COMMENT_Id)
    val SHEBANG_COMMENT: KtToken = KtToken("SHEBANG_COMMENT", SHEBANG_COMMENT_Id)

    val DOC_COMMENT: KDocToken = KDocTokens.KDOC

    val WHITE_SPACE: SyntaxElementType = SyntaxTokenTypes.WHITE_SPACE

    val INTEGER_LITERAL: KtToken = KtToken("INTEGER_LITERAL", INTEGER_LITERAL_Id)
    val FLOAT_LITERAL: KtToken = KtToken("FLOAT_CONSTANT", FLOAT_LITERAL_Id)
    val CHARACTER_LITERAL: KtToken =
        KtToken("CHARACTER_LITERAL", CHARACTER_LITERAL_Id)

    val INTERPOLATION_PREFIX: KtToken =
        KtToken("INTERPOLATION_PREFIX", INTERPOLATION_PREFIX_Id)
    val CLOSING_QUOTE: KtToken = KtToken("CLOSING_QUOTE", CLOSING_QUOTE_Id)
    val OPEN_QUOTE: KtToken = KtToken("OPEN_QUOTE", OPEN_QUOTE_Id)
    val REGULAR_STRING_PART: KtToken =
        KtToken("REGULAR_STRING_PART", REGULAR_STRING_PART_Id)
    val ESCAPE_SEQUENCE: KtToken = KtToken("ESCAPE_SEQUENCE", ESCAPE_SEQUENCE_Id)
    val SHORT_TEMPLATE_ENTRY_START: KtToken =
        KtToken("SHORT_TEMPLATE_ENTRY_START", SHORT_TEMPLATE_ENTRY_START_Id)
    val LONG_TEMPLATE_ENTRY_START: KtToken =
        KtToken("LONG_TEMPLATE_ENTRY_START", LONG_TEMPLATE_ENTRY_START_Id)
    val LONG_TEMPLATE_ENTRY_END: KtToken =
        KtToken("LONG_TEMPLATE_ENTRY_END", LONG_TEMPLATE_ENTRY_END_Id)
    val DANGLING_NEWLINE: KtToken =
        KtToken("DANGLING_NEWLINE", DANGLING_NEWLINE_Id)

    val PACKAGE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("package", PACKAGE_KEYWORD_Id)
    val AS_KEYWORD: KtKeywordToken = KtKeywordToken.keyword("as", AS_KEYWORD_Id)
    val TYPE_ALIAS_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("typealias", TYPE_ALIAS_KEYWORD_Id)
    val CLASS_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("class", CLASS_KEYWORD_Id)
    val THIS_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("this", THIS_KEYWORD_Id)
    val SUPER_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("super", SUPER_KEYWORD_Id)
    val VAL_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("val", VAL_KEYWORD_Id)
    val VAR_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("var", VAR_KEYWORD_Id)
    val FUN_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.keywordModifier("fun", FUN_KEYWORD_Id)
    val FOR_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("for", FOR_KEYWORD_Id)
    val NULL_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("null", NULL_KEYWORD_Id)
    val TRUE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("true", TRUE_KEYWORD_Id)
    val FALSE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("false", FALSE_KEYWORD_Id)
    val IS_KEYWORD: KtKeywordToken = KtKeywordToken.keyword("is", IS_KEYWORD_Id)
    val IN_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.keywordModifier("in", IN_KEYWORD_Id)
    val THROW_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("throw", THROW_KEYWORD_Id)
    val RETURN_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("return", RETURN_KEYWORD_Id)
    val BREAK_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("break", BREAK_KEYWORD_Id)
    val CONTINUE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("continue", CONTINUE_KEYWORD_Id)
    val OBJECT_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("object", OBJECT_KEYWORD_Id)
    val IF_KEYWORD: KtKeywordToken = KtKeywordToken.keyword("if", IF_KEYWORD_Id)
    val TRY_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("try", TRY_KEYWORD_Id)
    val ELSE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("else", ELSE_KEYWORD_Id)
    val WHILE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("while", WHILE_KEYWORD_Id)
    val DO_KEYWORD: KtKeywordToken = KtKeywordToken.keyword("do", DO_KEYWORD_Id)
    val WHEN_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("when", WHEN_KEYWORD_Id)
    val INTERFACE_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("interface", INTERFACE_KEYWORD_Id)

    // Reserved for future use:
    val TYPEOF_KEYWORD: KtKeywordToken =
        KtKeywordToken.keyword("typeof", TYPEOF_KEYWORD_Id)

    val `AS_SAFE`: KtToken = KtKeywordToken.keyword("AS_SAFE", AS_SAFE_Id)

    val IDENTIFIER: KtToken = KtToken("IDENTIFIER", IDENTIFIER_Id)

    val FIELD_IDENTIFIER: KtToken =
        KtToken("FIELD_IDENTIFIER", FIELD_IDENTIFIER_Id)
    val LBRACKET: KtSingleValueToken =
        KtSingleValueToken("LBRACKET", "[", LBRACKET_Id)
    val RBRACKET: KtSingleValueToken =
        KtSingleValueToken("RBRACKET", "]", RBRACKET_Id)
    val LBRACE: KtSingleValueToken = KtSingleValueToken("LBRACE", "{", LBRACE_Id)
    val RBRACE: KtSingleValueToken = KtSingleValueToken("RBRACE", "}", RBRACE_Id)
    val LPAR: KtSingleValueToken = KtSingleValueToken("LPAR", "(", LPAR_Id)
    val RPAR: KtSingleValueToken = KtSingleValueToken("RPAR", ")", RPAR_Id)
    val DOT: KtSingleValueToken = KtSingleValueToken("DOT", ".", DOT_Id)
    val PLUSPLUS: KtSingleValueToken =
        KtSingleValueToken("PLUSPLUS", "++", PLUSPLUS_Id)
    val MINUSMINUS: KtSingleValueToken =
        KtSingleValueToken("MINUSMINUS", "--", MINUSMINUS_Id)
    val MUL: KtSingleValueToken = KtSingleValueToken("MUL", "*", MUL_Id)
    val PLUS: KtSingleValueToken = KtSingleValueToken("PLUS", "+", PLUS_Id)
    val MINUS: KtSingleValueToken = KtSingleValueToken("MINUS", "-", MINUS_Id)
    val EXCL: KtSingleValueToken = KtSingleValueToken("EXCL", "!", EXCL_Id)
    val DIV: KtSingleValueToken = KtSingleValueToken("DIV", "/", DIV_Id)
    val PERC: KtSingleValueToken = KtSingleValueToken("PERC", "%", PERC_Id)
    val LT: KtSingleValueToken = KtSingleValueToken("LT", "<", LT_Id)
    val GT: KtSingleValueToken = KtSingleValueToken("GT", ">", GT_Id)
    val LTEQ: KtSingleValueToken = KtSingleValueToken("LTEQ", "<=", LTEQ_Id)
    val GTEQ: KtSingleValueToken = KtSingleValueToken("GTEQ", ">=", GTEQ_Id)
    val EQEQEQ: KtSingleValueToken =
        KtSingleValueToken("EQEQEQ", "===", EQEQEQ_Id)
    val ARROW: KtSingleValueToken = KtSingleValueToken("ARROW", "->", ARROW_Id)
    val DOUBLE_ARROW: KtSingleValueToken =
        KtSingleValueToken("DOUBLE_ARROW", "=>", DOUBLE_ARROW_Id)
    val EXCLEQEQEQ: KtSingleValueToken =
        KtSingleValueToken("EXCLEQEQEQ", "!==", EXCLEQEQEQ_Id)
    val EQEQ: KtSingleValueToken = KtSingleValueToken("EQEQ", "==", EQEQ_Id)
    val EXCLEQ: KtSingleValueToken = KtSingleValueToken("EXCLEQ", "!=", EXCLEQ_Id)
    val EXCLEXCL: KtSingleValueToken =
        KtSingleValueToken("EXCLEXCL", "!!", EXCLEXCL_Id)
    val ANDAND: KtSingleValueToken = KtSingleValueToken("ANDAND", "&&", ANDAND_Id)
    val AND: KtSingleValueToken = KtSingleValueToken("AND", "&", AND_Id)
    val OROR: KtSingleValueToken = KtSingleValueToken("OROR", "||", OROR_Id)
    val SAFE_ACCESS: KtSingleValueToken =
        KtSingleValueToken("SAFE_ACCESS", "?.", SAFE_ACCESS_Id)
    val ELVIS: KtSingleValueToken = KtSingleValueToken("ELVIS", "?:", ELVIS_Id)
    val QUEST: KtSingleValueToken = KtSingleValueToken("QUEST", "?", QUEST_Id)
    val COLONCOLON: KtSingleValueToken =
        KtSingleValueToken("COLONCOLON", "::", COLONCOLON_Id)
    val COLON: KtSingleValueToken = KtSingleValueToken("COLON", ":", COLON_Id)
    val SEMICOLON: KtSingleValueToken =
        KtSingleValueToken("SEMICOLON", ";", SEMICOLON_Id)
    val DOUBLE_SEMICOLON: KtSingleValueToken =
        KtSingleValueToken("DOUBLE_SEMICOLON", ";;", DOUBLE_SEMICOLON_Id)
    val RANGE: KtSingleValueToken = KtSingleValueToken("RANGE", "..", RANGE_Id)
    val RANGE_UNTIL: KtSingleValueToken =
        KtSingleValueToken("RANGE_UNTIL", "..<", RANGE_UNTIL_Id)
    val EQ: KtSingleValueToken = KtSingleValueToken("EQ", "=", EQ_Id)
    val MULTEQ: KtSingleValueToken = KtSingleValueToken("MULTEQ", "*=", MULTEQ_Id)
    val DIVEQ: KtSingleValueToken = KtSingleValueToken("DIVEQ", "/=", DIVEQ_Id)
    val PERCEQ: KtSingleValueToken = KtSingleValueToken("PERCEQ", "%=", PERCEQ_Id)
    val PLUSEQ: KtSingleValueToken = KtSingleValueToken("PLUSEQ", "+=", PLUSEQ_Id)
    val MINUSEQ: KtSingleValueToken =
        KtSingleValueToken("MINUSEQ", "-=", MINUSEQ_Id)
    val NOT_IN: KtKeywordToken =
        KtKeywordToken.keyword("NOT_IN", "!in", NOT_IN_Id)
    val NOT_IS: KtKeywordToken =
        KtKeywordToken.keyword("NOT_IS", "!is", NOT_IS_Id)
    val HASH: KtSingleValueToken = KtSingleValueToken("HASH", "#", HASH_Id)
    val AT: KtSingleValueToken = KtSingleValueToken("AT", "@", AT_Id)

    val COMMA: KtSingleValueToken = KtSingleValueToken("COMMA", ",", COMMA_Id)

    val EOL_OR_SEMICOLON: KtToken =
        KtToken("EOL_OR_SEMICOLON", EOL_OR_SEMICOLON_Id)
    val ALL_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("all", ALL_KEYWORD_Id)
    val FILE_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("file", FILE_KEYWORD_Id)
    val FIELD_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("field", FIELD_KEYWORD_Id)
    val PROPERTY_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("property", PROPERTY_KEYWORD_Id)
    val RECEIVER_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("receiver", RECEIVER_KEYWORD_Id)
    val PARAM_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("param", PARAM_KEYWORD_Id)
    val SETPARAM_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("setparam", SETPARAM_KEYWORD_Id)
    val DELEGATE_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("delegate", DELEGATE_KEYWORD_Id)
    val IMPORT_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("import", IMPORT_KEYWORD_Id)
    val WHERE_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("where", WHERE_KEYWORD_Id)
    val BY_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("by", BY_KEYWORD_Id)
    val GET_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("get", GET_KEYWORD_Id)
    val SET_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("set", SET_KEYWORD_Id)
    val CONSTRUCTOR_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("constructor", CONSTRUCTOR_KEYWORD_Id)
    val INIT_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("init", INIT_KEYWORD_Id)
    val CONTEXT_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("context", CONTEXT_KEYWORD_Id)

    val ABSTRACT_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("abstract", ABSTRACT_KEYWORD_Id)
    val ENUM_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("enum", ENUM_KEYWORD_Id)
    val CONTRACT_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("contract", CONTRACT_KEYWORD_Id)
    val OPEN_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("open", OPEN_KEYWORD_Id)
    val INNER_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("inner", INNER_KEYWORD_Id)
    val OVERRIDE_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("override", OVERRIDE_KEYWORD_Id)
    val PRIVATE_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("private", PRIVATE_KEYWORD_Id)
    val PUBLIC_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("public", PUBLIC_KEYWORD_Id)
    val INTERNAL_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("internal", INTERNAL_KEYWORD_Id)
    val PROTECTED_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("protected", PROTECTED_KEYWORD_Id)
    val CATCH_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("catch", CATCH_KEYWORD_Id)
    val OUT_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("out", OUT_KEYWORD_Id)
    val VARARG_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("vararg", VARARG_KEYWORD_Id)
    val REIFIED_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("reified", REIFIED_KEYWORD_Id)
    val DYNAMIC_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("dynamic", DYNAMIC_KEYWORD_Id)
    val COMPANION_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("companion", COMPANION_KEYWORD_Id)
    val SEALED_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("sealed", SEALED_KEYWORD_Id)

    val DEFAULT_VISIBILITY_KEYWORD: KtModifierKeywordToken = PUBLIC_KEYWORD

    val FINALLY_KEYWORD: KtKeywordToken =
        KtKeywordToken.softKeyword("finally", FINALLY_KEYWORD_Id)
    val FINAL_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("final", FINAL_KEYWORD_Id)

    val LATEINIT_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("lateinit", LATEINIT_KEYWORD_Id)

    val DATA_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("data", DATA_KEYWORD_Id)
    val VALUE_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("value", VALUE_KEYWORD_Id)
    val INLINE_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("inline", INLINE_KEYWORD_Id)
    val NOINLINE_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("noinline", NOINLINE_KEYWORD_Id)
    val TAILREC_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("tailrec", TAILREC_KEYWORD_Id)
    val EXTERNAL_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("external", EXTERNAL_KEYWORD_Id)
    val ANNOTATION_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("annotation", ANNOTATION_KEYWORD_Id)
    val CROSSINLINE_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("crossinline", CROSSINLINE_KEYWORD_Id)
    val OPERATOR_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("operator", OPERATOR_KEYWORD_Id)
    val INFIX_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("infix", INFIX_KEYWORD_Id)

    val CONST_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("const", CONST_KEYWORD_Id)

    val SUSPEND_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("suspend", SUSPEND_KEYWORD_Id)

    val EXPECT_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("expect", EXPECT_KEYWORD_Id)
    val ACTUAL_KEYWORD: KtModifierKeywordToken =
        KtModifierKeywordToken.softKeywordModifier("actual", ACTUAL_KEYWORD_Id)


    val KEYWORDS: SyntaxElementTypeSet = listOf(
        PACKAGE_KEYWORD, AS_KEYWORD, TYPE_ALIAS_KEYWORD, CLASS_KEYWORD, INTERFACE_KEYWORD,
        THIS_KEYWORD, SUPER_KEYWORD, VAL_KEYWORD, VAR_KEYWORD, FUN_KEYWORD, FOR_KEYWORD,
        NULL_KEYWORD,
        TRUE_KEYWORD, FALSE_KEYWORD, IS_KEYWORD,
        IN_KEYWORD, THROW_KEYWORD, RETURN_KEYWORD, BREAK_KEYWORD, CONTINUE_KEYWORD, OBJECT_KEYWORD, IF_KEYWORD,
        ELSE_KEYWORD, WHILE_KEYWORD, DO_KEYWORD, TRY_KEYWORD, WHEN_KEYWORD,
        NOT_IN, NOT_IS, `AS_SAFE`,
        TYPEOF_KEYWORD
    ).asSyntaxElementTypeSet()

    val SOFT_KEYWORDS: SyntaxElementTypeSet = listOf(
        FILE_KEYWORD, IMPORT_KEYWORD, WHERE_KEYWORD, BY_KEYWORD, GET_KEYWORD,
        SET_KEYWORD, ABSTRACT_KEYWORD, ENUM_KEYWORD, CONTRACT_KEYWORD, OPEN_KEYWORD, INNER_KEYWORD,
        OVERRIDE_KEYWORD, PRIVATE_KEYWORD, PUBLIC_KEYWORD, INTERNAL_KEYWORD, PROTECTED_KEYWORD,
        CATCH_KEYWORD, FINALLY_KEYWORD, OUT_KEYWORD, FINAL_KEYWORD, VARARG_KEYWORD, REIFIED_KEYWORD,
        DYNAMIC_KEYWORD, COMPANION_KEYWORD, CONSTRUCTOR_KEYWORD, INIT_KEYWORD, SEALED_KEYWORD,
        FIELD_KEYWORD, PROPERTY_KEYWORD, RECEIVER_KEYWORD, PARAM_KEYWORD, SETPARAM_KEYWORD,
        DELEGATE_KEYWORD,
        LATEINIT_KEYWORD,
        DATA_KEYWORD, INLINE_KEYWORD, NOINLINE_KEYWORD, TAILREC_KEYWORD, EXTERNAL_KEYWORD,
        ANNOTATION_KEYWORD, CROSSINLINE_KEYWORD, CONST_KEYWORD, OPERATOR_KEYWORD, INFIX_KEYWORD,
        SUSPEND_KEYWORD, EXPECT_KEYWORD, ACTUAL_KEYWORD,
        VALUE_KEYWORD, CONTEXT_KEYWORD
    ).asSyntaxElementTypeSet()

    /*
        This is used in stub serialization:
        1. Do not change order.
        2. If you add an entry or change order, increase stub version.
     */
    val MODIFIER_KEYWORDS: SyntaxElementTypeSet =
        listOf(
            ABSTRACT_KEYWORD, ENUM_KEYWORD, CONTRACT_KEYWORD, OPEN_KEYWORD, INNER_KEYWORD, OVERRIDE_KEYWORD, PRIVATE_KEYWORD,
            PUBLIC_KEYWORD, INTERNAL_KEYWORD, PROTECTED_KEYWORD, OUT_KEYWORD, IN_KEYWORD, FINAL_KEYWORD, VARARG_KEYWORD,
            REIFIED_KEYWORD, COMPANION_KEYWORD, SEALED_KEYWORD, LATEINIT_KEYWORD,
            DATA_KEYWORD, INLINE_KEYWORD, NOINLINE_KEYWORD, TAILREC_KEYWORD, EXTERNAL_KEYWORD, ANNOTATION_KEYWORD, CROSSINLINE_KEYWORD,
            CONST_KEYWORD, OPERATOR_KEYWORD, INFIX_KEYWORD, SUSPEND_KEYWORD,
            EXPECT_KEYWORD, ACTUAL_KEYWORD, FUN_KEYWORD, VALUE_KEYWORD
        ).asSyntaxElementTypeSet()

    val TYPE_MODIFIER_KEYWORDS: SyntaxElementTypeSet = listOf(SUSPEND_KEYWORD).asSyntaxElementTypeSet()
    val TYPE_ARGUMENT_MODIFIER_KEYWORDS: SyntaxElementTypeSet = listOf(IN_KEYWORD, OUT_KEYWORD).asSyntaxElementTypeSet()
    val RESERVED_VALUE_PARAMETER_MODIFIER_KEYWORDS: SyntaxElementTypeSet =
        listOf(OUT_KEYWORD, VARARG_KEYWORD).asSyntaxElementTypeSet()

    val VISIBILITY_MODIFIERS: SyntaxElementTypeSet =
        listOf(PRIVATE_KEYWORD, PUBLIC_KEYWORD, INTERNAL_KEYWORD, PROTECTED_KEYWORD).asSyntaxElementTypeSet()
    val MODALITY_MODIFIERS: SyntaxElementTypeSet =
        listOf(ABSTRACT_KEYWORD, FINAL_KEYWORD, SEALED_KEYWORD, OPEN_KEYWORD).asSyntaxElementTypeSet()

    val WHITESPACES: SyntaxElementTypeSet = listOf(SyntaxTokenTypes.WHITE_SPACE).asSyntaxElementTypeSet()

    /**
     * Don't add KDocTokens to COMMENTS SyntaxElementTypeSet, because it is used in KotlinParserDefinition.getCommentTokens(),
     * and therefor all COMMENTS tokens will be ignored by PsiBuilder.
     *
     * @see KtPsiUtil.isInComment
     */
    val COMMENTS: SyntaxElementTypeSet =
        listOf(EOL_COMMENT, BLOCK_COMMENT, DOC_COMMENT, SHEBANG_COMMENT).asSyntaxElementTypeSet()
    val WHITE_SPACE_OR_COMMENT_BIT_SET: SyntaxElementTypeSet = COMMENTS + WHITESPACES

    val STRINGS: SyntaxElementTypeSet = listOf(CHARACTER_LITERAL, REGULAR_STRING_PART).asSyntaxElementTypeSet()
    val OPERATIONS: SyntaxElementTypeSet = listOf(
        AS_KEYWORD, `AS_SAFE`, IS_KEYWORD, IN_KEYWORD, DOT, PLUSPLUS, MINUSMINUS, EXCLEXCL, MUL, PLUS,
        MINUS, EXCL, DIV, PERC, LT, GT, LTEQ, GTEQ, EQEQEQ, EXCLEQEQEQ, EQEQ, EXCLEQ, ANDAND, OROR,
        SAFE_ACCESS, ELVIS,
        RANGE, RANGE_UNTIL, EQ, MULTEQ, DIVEQ, PERCEQ, PLUSEQ, MINUSEQ,
        NOT_IN, NOT_IS,
        IDENTIFIER
    ).asSyntaxElementTypeSet()

    val AUGMENTED_ASSIGNMENTS: SyntaxElementTypeSet =
        listOf(PLUSEQ, MINUSEQ, MULTEQ, PERCEQ, DIVEQ).asSyntaxElementTypeSet()
    val ALL_ASSIGNMENTS: SyntaxElementTypeSet =
        listOf(EQ, PLUSEQ, MINUSEQ, MULTEQ, PERCEQ, DIVEQ).asSyntaxElementTypeSet()
    val INCREMENT_AND_DECREMENT: SyntaxElementTypeSet = listOf(PLUSPLUS, MINUSMINUS).asSyntaxElementTypeSet()
}