package com.aegislayer.daemon.trace

enum class TraceLevel { INFO, RULE, ACTION }

data class TraceEntry(
    val timestamp: Long,
    val level: TraceLevel,
    val tag: String,
    val message: String
) {
    override fun toString(): String {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
        return "[$ts][${level.name}][$tag] $message"
    }
}
