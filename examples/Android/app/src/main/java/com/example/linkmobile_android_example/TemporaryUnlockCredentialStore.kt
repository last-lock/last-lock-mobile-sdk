package com.example.linkmobile_android_example

import android.content.Context
import java.util.UUID

/** Persists the passId that identifies the SDK-managed temporary credential. */
class TemporaryUnlockCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun existingPassId(): String? = preferences.getString(PASS_ID_KEY, null)

    fun getOrCreatePassId(): String = existingPassId() ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(PASS_ID_KEY, it).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "temporary_unlock_credential"
        private const val PASS_ID_KEY = "namespace"
    }
}
