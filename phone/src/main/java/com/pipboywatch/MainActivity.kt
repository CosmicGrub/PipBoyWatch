package com.pipboywatch

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.pipboywatch.shared.health.HEALTH_PERMISSIONS
import com.pipboywatch.shared.health.HealthConnectManager
import com.pipboywatch.shared.sync.STAT_RESPONSE_PATH
import com.pipboywatch.shared.sync.encodeStatSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Launcher-visible companion screen — this is what makes "connect to
 * Health Connect" a two-tap, discoverable action instead of something
 * buried behind a Share-sheet-only app. One-time setup: grant Health
 * Connect access here, then the watch's STAT tab pulls a fresh snapshot
 * automatically via StatRequestListenerService every time it's opened.
 * "Sync now" is a manual push for immediate confirmation it's working.
 */
class MainActivity : ComponentActivity() {
    private val healthManager by lazy { HealthConnectManager(applicationContext) }

    private lateinit var statusText: TextView
    private lateinit var grantButton: Button
    private lateinit var syncButton: Button

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        statusText = TextView(this).apply { textSize = 16f }
        grantButton = Button(this).apply {
            text = "Grant Health Connect Access"
            setOnClickListener { permissionLauncher.launch(HEALTH_PERMISSIONS) }
        }
        syncButton = Button(this).apply {
            text = "Sync to Watch Now"
            setOnClickListener { syncNow() }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(padding, padding, padding, padding)
                addView(TextView(this@MainActivity).apply {
                    text = "Pip-Boy Companion"
                    textSize = 22f
                    setPadding(0, 0, 0, padding)
                })
                addView(statusText)
                addView(grantButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = padding })
                addView(syncButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = padding / 2 })
            }
        )

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            when {
                !healthManager.isAvailable -> {
                    statusText.text = "Health Connect isn't available on this phone."
                    grantButton.isEnabled = false
                    syncButton.isEnabled = false
                }
                !healthManager.hasAllPermissions() -> {
                    statusText.text = "Health Connect access not yet granted.\nGrant it below so the watch can pull your stats."
                    grantButton.isEnabled = true
                    syncButton.isEnabled = false
                }
                else -> {
                    val snapshot = healthManager.readStatSnapshot()
                    statusText.text = "Connected.\nSteps today: ${snapshot.steps}\n" +
                        "Heart rate: ${snapshot.latestHeartRateBpm?.let { "$it bpm" } ?: "no recent reading"}\n" +
                        "The watch's STAT tab will pull this automatically."
                    grantButton.isEnabled = false
                    syncButton.isEnabled = true
                }
            }
        }
    }

    private fun syncNow() {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No Pip-Boy watch connected", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // Empty request id — this is an unsolicited push (the user
                // tapped "Sync to Watch Now"), not a reply to a watch-side
                // request, so there's no id to echo back. PhoneStatRelay
                // on the watch treats an empty id as always-accepted.
                val payload = ("|" + encodeStatSnapshot(healthManager.readStatSnapshot())).toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(this@MainActivity)
                nodes.forEach { node -> messageClient.sendMessage(node.id, STAT_RESPONSE_PATH, payload).await() }
                Toast.makeText(this@MainActivity, "Synced to watch", Toast.LENGTH_SHORT).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
