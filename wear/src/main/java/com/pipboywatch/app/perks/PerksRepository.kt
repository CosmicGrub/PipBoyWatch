package com.pipboywatch.app.perks

import android.content.Context
import com.pipboywatch.app.health.HealthConnectManager
import com.pipboywatch.app.inv.InvRepository

data class Perk(
    val name: String,
    val unlocked: Boolean,
    val detail: String
)

/**
 * Perks are computed on-the-fly from existing data at read time rather
 * than persisted in their own table (resolves the spec's open question) —
 * both current rules are simple, cheap boolean checks against data we
 * already store elsewhere, so a separate synced table would just be a
 * second source of truth to keep correct for no real benefit.
 */
class PerksRepository(context: Context) {
    private val invRepository = InvRepository(context)
    private val healthManager = HealthConnectManager(context)

    suspend fun computePerks(): List<Perk> {
        val fullyLoaded = invRepository.isFullyChecked()

        val stepStreak = if (!healthManager.isAvailable) {
            Perk(
                name = "Step Streak",
                unlocked = false,
                detail = "Needs Health Connect — unavailable on this device (see spec)"
            )
        } else {
            val unlocked = healthManager.hasStepStreak(days = 7, thresholdSteps = 5000)
            Perk(
                name = "Step Streak",
                unlocked = unlocked,
                detail = if (unlocked) "7 days of 5,000+ steps" else "Keep going — 7-day streak not yet hit"
            )
        }

        return listOf(
            Perk(
                name = "Fully Loaded",
                unlocked = fullyLoaded,
                detail = if (fullyLoaded) "Every INV item checked today" else "Check off everything in INV to unlock"
            ),
            stepStreak
        )
    }
}
