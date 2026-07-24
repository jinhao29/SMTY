package com.shangmentiyu.sportscoach.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        private val KEY_COACH = stringPreferencesKey("coach")
    }

    val coach: Flow<String> = context.dataStore.data.map { it[KEY_COACH] ?: "" }

    suspend fun setCoach(value: String) {
        context.dataStore.edit { it[KEY_COACH] = value }
    }
}
