package com.aegislayer.daemon

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.aegislayer.daemon.service.SystemControlService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            val serviceIntent = Intent(this, SystemControlService::class.java)
            startForegroundService(serviceIntent)
        }

        btnStop.setOnClickListener {
            val serviceIntent = Intent(this, SystemControlService::class.java)
            stopService(serviceIntent)
        }
    }
}
