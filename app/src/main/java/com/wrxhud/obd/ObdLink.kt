package com.wrxhud.obd

/**
 * Minimal command channel the poller needs. Lets the poller run against a fake in unit
 * tests; production wraps [ObdConnection] unchanged via [ObdConnectionLink].
 */
interface ObdLink {
    val isOpen: Boolean
    fun sendCommand(
        command: String,
        waitForPrompt: Boolean = true,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): String

    companion object {
        /** Same value as the private read timeout in [ObdConnection]. */
        const val DEFAULT_TIMEOUT_MS = 1500L
    }
}

/** Forwards to the real Bluetooth connection (all guard + socket logic stays there). */
class ObdConnectionLink(private val connection: ObdConnection) : ObdLink {
    override val isOpen: Boolean get() = connection.isOpen
    override fun sendCommand(command: String, waitForPrompt: Boolean, timeoutMs: Long): String =
        connection.sendCommand(command, waitForPrompt, timeoutMs)
}
