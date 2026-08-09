package com.pipboywatch.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "pipboy_settings")
private val LAST_INV_RESET_EPOCH_DAY = longPreferencesKey("last_inv_reset_epoch_day")

/** Small key-value settings that don't warrant a Room table. */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    suspend fun getLastInvResetEpochDay(): Long? =
        appContext.settingsDataStore.data.first()[LAST_INV_RESET_EPOCH_DAY]

    suspend fun setLastInvResetEpochDay(epochDay: Long) {
        appContext.settingsDataStore.edit { it[LAST_INV_RESET_EPOCH_DAY] = epochDay }
    }
}
