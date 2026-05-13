package com.graydyn.tracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val USER_PREFS_NAME = "user_prefs"

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFS_NAME
)

object PreferenceKeys {
    val PROTEIN_AND_CALORIES_ONLY = booleanPreferencesKey("protein_and_calories_only")
}
