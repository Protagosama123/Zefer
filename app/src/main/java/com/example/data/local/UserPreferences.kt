package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zefer_user_prefs")

class UserPreferences(private val context: Context) {
    private val isLoggedInKey = booleanPreferencesKey("is_logged_in")
    private val userEmailKey = stringPreferencesKey("user_email")

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[isLoggedInKey] ?: false
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[userEmailKey]
    }

    suspend fun setLoggedIn(loggedIn: Boolean, email: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[isLoggedInKey] = loggedIn
            if (email != null) {
                prefs[userEmailKey] = email
            } else if (!loggedIn) {
                prefs.remove(userEmailKey)
            }
        }
    }
}
