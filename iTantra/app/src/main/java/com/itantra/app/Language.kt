package com.itantra.app

/**
 * The languages the app can speak/recognize.
 *
 * `code` is a short tag sent over the network so the other phone knows which language a
 * message is in. `display` is what the user sees in the picker.
 */
enum class Language(val code: String, val display: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी (Hindi)");

    companion object {
        fun fromCode(code: String): Language = entries.firstOrNull { it.code == code } ?: EN
    }
}
