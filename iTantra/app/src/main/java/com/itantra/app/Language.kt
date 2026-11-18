package com.itantra.app

/**
 * The languages the app can speak/recognize.
 *
 * `code` is a short tag sent over the network so the other phone knows which language a
 * message is in (and reads it with that language's voice). `display` is the English name,
 * `native` is the name in its own script, shown in pickers.
 */
enum class Language(val code: String, val display: String, val native: String) {
    EN("en", "English", "English"),
    HI("hi", "Hindi", "हिन्दी");

    companion object {
        fun fromCode(code: String?): Language = entries.firstOrNull { it.code == code } ?: EN

        /** Guess the language of typed text from its script (Devanagari means Hindi). */
        fun detect(text: String): Language = if (text.any { it in 'ऀ'..'ॿ' }) HI else EN
    }
}
