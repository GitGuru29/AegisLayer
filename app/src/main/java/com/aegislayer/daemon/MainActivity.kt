package com.aegislayer.daemon

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aegislayer.daemon.service.SystemControlService
import com.aegislayer.daemon.trace.TraceEngine
import com.aegislayer.daemon.trace.TraceLevel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvTraceLog: TextView
    private lateinit var tvLogCount: TextView
    private lateinit var scrollLog: ScrollView
    private var logEntryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart    = findViewById<MaterialButton>(R.id.btnStart)
        val btnStop     = findViewById<MaterialButton>(R.id.btnStop)
        val statusDot   = findViewById<View>(R.id.statusDot)
        val tvStatus    = findViewById<TextView>(R.id.tvStatusText)
        tvTraceLog      = findViewById(R.id.tvTraceLog)
        tvLogCount      = findViewById(R.id.tvLogCount)
        scrollLog       = findViewById(R.id.scrollLog)

        // Load persisted trace history on open
        TraceEngine.init(this)
        val history = TraceEngine.readRecent(80)
        if (history.isNotEmpty()) {
            history.forEach { appendEntry(it.level, it.tag, it.message) }
        }

        btnStart.setOnClickListener {
            startForegroundService(Intent(this, SystemControlService::class.java))
            updateStatusUI(true)
            checkAllPermissions()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, SystemControlService::class.java))
            updateStatusUI(false)
        }

        // Collect live trace entries from TraceEngine
        lifecycleScope.launch {
            TraceEngine.liveEntries.collect { entry ->
                appendEntry(entry.level, entry.tag, entry.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI(isServiceRunning(SystemControlService::class.java))
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateStatusUI(active: Boolean) {
        val statusDot = findViewById<View>(R.id.statusDot)
        val tvStatus = findViewById<TextView>(R.id.tvStatusText)
        if (active) {
            statusDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_active)
            )
            tvStatus.text = "Status: Active"
        } else {
            statusDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.status_inactive)
            )
            tvStatus.text = "Status: Offline"
        }
    }

    private fun checkAllPermissions() {
        if (!com.aegislayer.daemon.utils.PermissionUtils.hasUsageStatsPermission(this)) {
            com.aegislayer.daemon.utils.PermissionUtils.requestUsageStatsPermission(this)
            return
        }
        if (!com.aegislayer.daemon.utils.PermissionUtils.hasNotificationListenerPermission(this)) {
            com.aegislayer.daemon.utils.PermissionUtils.requestNotificationListenerPermission(this)
            return
        }
        if (!com.aegislayer.daemon.utils.PermissionUtils.hasDndPermission(this)) {
            com.aegislayer.daemon.utils.PermissionUtils.requestDndPermission(this)
            return
        }
        if (!com.aegislayer.daemon.utils.PermissionUtils.isIgnoringBatteryOptimizations(this)) {
            com.aegislayer.daemon.utils.PermissionUtils.requestIgnoreBatteryOptimizations(this)
        }
    }

    private fun appendEntry(level: TraceLevel, tag: String, message: String) {
        logEntryCount++
        tvLogCount.text = "$logEntryCount entries"

        val color = when (level) {
            TraceLevel.INFO   -> "#94A3B8"   // slate grey
            TraceLevel.RULE   -> "#FBBF24"   // amber
            TraceLevel.ACTION -> "#34D399"   // emerald green
        }
        val prefix = when (level) {
            TraceLevel.INFO   -> "[INFO]  "
            TraceLevel.RULE   -> "[RULE]  "
            TraceLevel.ACTION -> "[ACT]   "
        }

        val line = "$prefix[$tag] $message\n"

        // Use SpannableString for color per line
        val spannable = android.text.SpannableString(line)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(Color.parseColor(color)),
            0, line.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvTraceLog.append(spannable)

        // Auto-scroll to bottom
        scrollLog.post { scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
