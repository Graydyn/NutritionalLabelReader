package com.graydyn.tracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.graydyn.tracker.data.preferences.PreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val proteinAndCaloriesOnly: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[PreferenceKeys.PROTEIN_AND_CALORIES_ONLY] ?: false
        }

    suspend fun setProteinAndCaloriesOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PROTEIN_AND_CALORIES_ONLY] = enabled
        }
    }
}
