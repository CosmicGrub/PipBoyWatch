package com.pipboywatch.app.holotape

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.pipboywatch.app.data.HolotapeEntity
import com.pipboywatch.app.data.PipBoyDatabase
import kotlinx.coroutines.flow.Flow

class HolotapeRepository(private val context: Context) {
    private val dao = PipBoyDatabase.getInstance(context.applicationContext).holotapeDao()

    fun observeRecent(): Flow<List<HolotapeEntity>> = dao.observeRecent()

    /** No runtime permission dialog exists for this — user grants it via Settings. */
    fun isAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
