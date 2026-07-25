package com.arflix.tv.shared.util

object AuthConstants {
    const val CLERK_USER_PROFILE_URL = ""
    const val CLERK_SECRET_KEY = ""
    const val GOOGLE_WEB_CLIENT_ID = ""
}

object AuthValidator {
    fun isValidEmail(email: String): Boolean = email.contains("@")
}

fun hashString(input: String): String = input // stub for now
fun sanitizeEmailString(email: String): String = email.trim().lowercase()
