package com.wrxhud.obd

object ObdCommands {
    val INIT_SEQUENCE = listOf(
        "ATZ",
        "ATE0",
        "ATL0",
        "ATS0",
        "ATH0",
        "ATAT2",
        "ATSP6",
        "STPTO 32"
    )

    fun mode01(pid: Int): String = "01%02X".format(pid)

    fun mode22(did: Int): String = "22%04X".format(did)

    fun setHeader(header: Int): String = "ATSH%03X".format(header)
}
