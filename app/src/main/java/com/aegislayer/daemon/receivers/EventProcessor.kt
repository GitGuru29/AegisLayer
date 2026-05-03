package com.aegislayer.daemon.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class EventProcessor : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.d("AegisLayer", "EventProcessor received: $action")
        
        // Forward event to ContextBuilder/RuleEngine
    }
}
