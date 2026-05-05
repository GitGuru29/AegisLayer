package com.aegislayer.daemon.engine

import com.aegislayer.daemon.models.SystemEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The central nervous system of the daemon.
 *
 * This is an event bus. All the different monitors (AppUsageMonitor, EventProcessor, etc.)
 * send their events here. The SystemControlService listens to this bus and processes
 * the events as they arrive.
 *
 * This keeps the monitors completely decoupled from the service — they don't need
 * to know about the service, they just throw events onto the bus.
 */
object EventDispatcher {
    // A SharedFlow is like a radio broadcast. The monitors are the DJs sending out events,
    // and the SystemControlService is the listener tuned in to the station.
    private val _events = MutableSharedFlow<SystemEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    /**
     * Called by monitors to broadcast an event to anyone who is listening.
     */
    fun dispatch(event: SystemEvent) {
        _events.tryEmit(event)
    }
}
