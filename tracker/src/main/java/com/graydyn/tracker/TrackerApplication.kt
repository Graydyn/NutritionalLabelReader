package com.graydyn.tracker

import android.app.Application
import android.util.Log
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.preferences.userPreferencesDataStore
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import com.graydyn.tracker.data.seed.CsvSeeder
import com.graydyn.tracker.data.seed.SeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrackerApplication : Application() {
    val database by lazy { TrackerDatabase.getInstance(this) }
    val userPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext.userPreferencesDataStore)
    }

    private val _seedState = MutableStateFlow(SeedState.Seeding)

    /**
     * Whether the one-time first-launch food database seed is still running.
     * The search UI observes this so it can explain the wait instead of showing
     * a misleading "no results" while the import is in flight.
     */
    val seedState: StateFlow<SeedState> = _seedState.asStateFlow()

    companion object {
        private const val TAG = "TrackerApplication"
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            val dao = database.foodDao()
            if (dao.count() == 0) {
                try {
                    CsvSeeder.seed(this@TrackerApplication, dao)
                } catch (t: Throwable) {
                    // Don't leave the UI stuck on "setting up..." forever if the
                    // seed fails; surface it and let search show its normal state.
                    Log.e(TAG, "Food database seed failed", t)
                }
            }
            _seedState.value = SeedState.Ready
        }
    }
}
