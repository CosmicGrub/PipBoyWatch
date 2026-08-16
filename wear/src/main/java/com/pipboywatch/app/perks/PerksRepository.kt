package com.pipboywatch.app.perks

import android.content.Context
import com.pipboywatch.app.health.HealthConnectManager
import com.pipboywatch.app.health.PhoneStatRelay
import com.pipboywatch.app.health.PhoneStatResult
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

        val stepStreak = if (healthManager.isAvailable) {
            val unlocked = healthManager.hasStepStreak(days = 7, thresholdSteps = 5000)
            Perk(
                name = "Step Streak",
                unlocked = unlocked,
                detail = if (unlocked) "7 days of 5,000+ steps" else "Keep going — 7-day streak not yet hit"
            )
        } else {
            // On-watch Health Connect is unavailable on this hardware (see
            // HealthConnectManager) — fall back on whatever the STAT tab's
            // last phone relay found, if any. Opportunistic only: this
            // doesn't itself trigger a phone request, so it's stale/empty
            // until STAT has been opened at least once this session.
            val relayed = (PhoneStatRelay.result.value as? PhoneStatResult.Success)?.snapshot?.hasStepStreak
            when (relayed) {
                null -> Perk(
                    name = "Step Streak",
                    unlocked = false,
                    detail = "Needs Health Connect — open STAT once to sync via paired phone"
                )
                else -> Perk(
                    name = "Step Streak",
                    unlocked = relayed,
                    detail = if (relayed) "7 days of 5,000+ steps (via phone)" else "Keep going — 7-day streak not yet hit (via phone)"
                )
            }
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
