package com.graydyn.tracker

import android.app.Application
import com.graydyn.tracker.data.db.TrackerDatabase
import com.graydyn.tracker.data.preferences.userPreferencesDataStore
import com.graydyn.tracker.data.repository.UserPreferencesRepository
import com.graydyn.tracker.data.seed.CsvSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackerApplication : Application() {
    val database by lazy { TrackerDatabase.getInstance(this) }
    val userPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext.userPreferencesDataStore)
    }

    companion object {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            val dao = database.foodDao()
            if (dao.count() == 0) {
                CsvSeeder.seed(this@TrackerApplication, dao)
            }
        }
    }
}
