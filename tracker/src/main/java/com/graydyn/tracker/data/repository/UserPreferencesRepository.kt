package com.graydyn.tracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.graydyn.tracker.data.preferences.PreferenceKeys
import com.graydyn.tracker.data.preferences.userPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository(private val context: Context) {

    val proteinAndCaloriesOnly: Flow<Boolean> =
        context.userPreferencesDataStore.data.map { prefs ->
            prefs[PreferenceKeys.PROTEIN_AND_CALORIES_ONLY] ?: false
        }

    suspend fun setProteinAndCaloriesOnly(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[PreferenceKeys.PROTEIN_AND_CALORIES_ONLY] = enabled
        }
    }
}
