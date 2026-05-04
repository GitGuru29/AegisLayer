package com.aegislayer.daemon

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aegislayer.daemon.service.SystemControlService
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<MaterialButton>(R.id.btnStart)
        val btnStop = findViewById<MaterialButton>(R.id.btnStop)
        val statusDot = findViewById<View>(R.id.statusDot)
        val tvStatusText = findViewById<TextView>(R.id.tvStatusText)

        btnStart.setOnClickListener {
            val serviceIntent = Intent(this, SystemControlService::class.java)
            startForegroundService(serviceIntent)
            
            // Update UI status to Active
            statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_active))
            tvStatusText.text = "Status: Active"
        }

        btnStop.setOnClickListener {
            val serviceIntent = Intent(this, SystemControlService::class.java)
            stopService(serviceIntent)
            
            // Update UI status to Offline
            statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_inactive))
            tvStatusText.text = "Status: Offline"
        }
    }
}
