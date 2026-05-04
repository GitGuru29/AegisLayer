package com.aegislayer.daemon.engine

import com.aegislayer.daemon.models.SystemEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object EventDispatcher {
    private val _events = MutableSharedFlow<SystemEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun dispatch(event: SystemEvent) {
        _events.tryEmit(event)
    }
}
