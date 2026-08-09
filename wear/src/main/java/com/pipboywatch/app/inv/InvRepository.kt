package com.pipboywatch.app.inv

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.pipboywatch.app.data.InvItemDao
import com.pipboywatch.app.data.InvItemEntity
import com.pipboywatch.app.data.PipBoyDatabase
import com.pipboywatch.app.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

private const val LABEL_PHONE = "Phone"

/**
 * INV checklist logic: default items, the once-a-day reset (resolved as
 * "reset on first screen visit after midnight" — no background job needed),
 * tap-to-confirm for ordinary items, and auto-detection of the phone row via
 * the Wear Data Layer's connected-nodes state (reflects the real
 * watch<->phone companion link, not just raw Bluetooth pairing).
 */
class InvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao: InvItemDao = PipBoyDatabase.getInstance(appContext).invItemDao()
    private val settings = SettingsStore(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    fun observeItems(): Flow<List<InvItemEntity>> = dao.observeAll()

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.insertAll(
                listOf(
                    InvItemEntity(label = LABEL_PHONE, sortOrder = 0, isSystemLinked = true),
                    InvItemEntity(label = "Keys", sortOrder = 1),
                    InvItemEntity(label = "Wallet", sortOrder = 2),
                    InvItemEntity(label = "ID", sortOrder = 3),
                    InvItemEntity(label = "Watch", sortOrder = 4)
                )
            )
        }
    }

    suspend fun resetIfNewDay() {
        val today = LocalDate.now().toEpochDay()
        if (settings.getLastInvResetEpochDay() != today) {
            dao.uncheckAll()
            settings.setLastInvResetEpochDay(today)
        }
    }

    suspend fun toggleChecked(item: InvItemEntity) {
        if (item.isSystemLinked) return // auto-managed, ignore taps
        dao.setChecked(item.id, !item.isChecked)
    }

    /** Used by the Perks "Fully Loaded" rule. */
    suspend fun isFullyChecked(): Boolean {
        val items = dao.getAllOnce()
        return items.isNotEmpty() && items.all { it.isChecked }
    }

    suspend fun refreshPhoneConnection() {
        val phoneItem = dao.getSystemLinkedItem() ?: return
        val connected = try {
            nodeClient.connectedNodes.await().isNotEmpty()
        } catch (e: Exception) {
            false
        }
        if (phoneItem.isChecked != connected) {
            dao.setChecked(phoneItem.id, connected)
        }
    }
}
