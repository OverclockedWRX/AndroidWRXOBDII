package com.wrxhud.obd

enum class PollTier {
    /** Every cycle (RPM, speed, throttle, pedal). */
    FAST,
    /** Every 2nd cycle (MAP / boost). */
    MEDIUM,
    /** About once per second (temps, voltage, fuel, AFR, baro, oil). */
    SLOW,
    /** About every 5 s (MIL / monitor status). */
    MIL
}

/** One signal to read: Mode 01 PID, or Mode 22 DID on [header]. */
class PollItem(
    val key: String,
    val pid: Int,
    val tier: PollTier,
    val mode: Int = 0x01,
    val header: Int? = null,
    val onData: (ByteArray) -> Unit
)

class Mode22Group(val header: Int, val items: List<PollItem>)

/** What to send this cycle: Mode 01 requests (each one command), then Mode 22 groups. */
class CyclePlan(
    val mode01: List<List<PollItem>>,
    val mode22: List<Mode22Group>
)

/** Per-PID memory of what the adapter/ECU tolerates (lives as long as one poller session). */
class PollHealth {
    /** PIDs that failed inside a batch but worked alone: always requested alone. */
    val noBatch = mutableSetOf<Int>()
    /** PIDs seen answered by exactly one ECU: safe for the response-count hint. */
    val singleEcu = mutableSetOf<Int>()
    /** PIDs where the hint truncated the reply or several ECUs answer: never hinted. */
    val noHint = mutableSetOf<Int>()
    private val strikes = mutableMapOf<Int, Int>()

    fun batchSucceeded(pid: Int) { strikes.remove(pid) }

    /** [weight] = [FAIL_LIMIT] degrades at once. */
    fun batchFailed(pid: Int, weight: Int = 1) {
        val n = (strikes[pid] ?: 0) + weight
        strikes[pid] = n
        if (n >= FAIL_LIMIT) noBatch += pid
    }

    fun canHint(pids: List<Int>): Boolean = pids.all { it in singleEcu && it !in noHint }

    companion object {
        const val FAIL_LIMIT = 2
    }
}

/**
 * Pure scheduler for the Fast polling mode. Decides, per cycle, which items are due and
 * how to pack them into requests. No I/O; [nowMs] is passed in so tests are deterministic.
 *
 * Tiers: FAST every cycle; MEDIUM every 2nd cycle (joins the fast request); SLOW items
 * are packed into evenly sized batches, one batch per (1000 / batches) ms so each PID is
 * refreshed about once a second; MIL every [milPeriodMs]. Only PIDs in the supported
 * bitmask with a known data length are batched; the rest go out alone. Mode 22 items due
 * this cycle are grouped by header (one header switch per group).
 */
class PollPlanner(
    private val maxBatch: Int = ObdCommands.MAX_MODE01_BATCH,
    private val slowPeriodMs: Long = 1000L,
    private val milPeriodMs: Long = 5000L
) {
    val health = PollHealth()

    private var items: List<PollItem> = emptyList()
    private var supported: Set<Int> = emptySet()
    private var cycle = 0L
    private var slowIdx = 0
    private var lastSlowMs: Long? = null
    private var lastMilMs: Long? = null
    private var lastMode22SlowMs: Long? = null

    fun configure(items: List<PollItem>, supported: Set<Int>) {
        this.items = items
        this.supported = supported
        cycle = 0
        slowIdx = 0
        lastSlowMs = null
        lastMilMs = null
        lastMode22SlowMs = null
    }

    fun canBatch(item: PollItem): Boolean =
        item.mode == 0x01 &&
            item.pid in supported &&
            StnParser.pidDataLength(item.pid) != null &&
            item.pid !in health.noBatch

    fun nextCycle(nowMs: Long): CyclePlan {
        val c = cycle++
        val evenCycle = c % 2L == 0L

        val pool = ArrayList<PollItem>()
        items.filterTo(pool) { it.mode == 0x01 && it.tier == PollTier.FAST }
        if (evenCycle) items.filterTo(pool) { it.mode == 0x01 && it.tier == PollTier.MEDIUM }
        val mil = items.filter { it.mode == 0x01 && it.tier == PollTier.MIL }
        if (mil.isNotEmpty() && (lastMilMs?.let { nowMs - it >= milPeriodMs } != false)) {
            lastMilMs = nowMs
            pool += mil
        }
        val requests = ArrayList<List<PollItem>>(pack(pool, evenly = false))

        val slow = items.filter { it.mode == 0x01 && it.tier == PollTier.SLOW }
        if (slow.isNotEmpty()) {
            val units = pack(slow, evenly = true)
            val interval = slowPeriodMs / units.size
            if (lastSlowMs?.let { nowMs - it >= interval } != false) {
                lastSlowMs = nowMs
                requests += units[slowIdx % units.size]
                slowIdx++
            }
        }

        val m22 = ArrayList<PollItem>()
        if (evenCycle) items.filterTo(m22) { it.mode == 0x22 && it.tier != PollTier.SLOW }
        val slow22 = items.filter { it.mode == 0x22 && it.tier == PollTier.SLOW }
        if (slow22.isNotEmpty() && (lastMode22SlowMs?.let { nowMs - it >= slowPeriodMs } != false)) {
            lastMode22SlowMs = nowMs
            m22 += slow22
        }
        val groups = m22.groupBy { it.header ?: DEFAULT_HEADER }
            .map { (h, list) -> Mode22Group(h, list) }
        return CyclePlan(requests, groups)
    }

    /**
     * Splits [list] into requests: batchable PIDs in groups of up to [maxBatch] (balanced
     * when [evenly]), every non-batchable or duplicate PID alone.
     */
    fun pack(list: List<PollItem>, evenly: Boolean): List<List<PollItem>> {
        val batchable = ArrayList<PollItem>()
        val singles = ArrayList<List<PollItem>>()
        val seen = HashSet<Int>()
        for (item in list) {
            if (canBatch(item) && seen.add(item.pid)) batchable += item else singles += listOf(item)
        }
        val out = ArrayList<List<PollItem>>()
        if (batchable.isNotEmpty()) {
            val chunks = (batchable.size + maxBatch - 1) / maxBatch
            if (evenly) {
                val base = batchable.size / chunks
                var extra = batchable.size % chunks
                var at = 0
                repeat(chunks) {
                    val n = base + if (extra > 0) 1 else 0
                    if (extra > 0) extra--
                    out += batchable.subList(at, at + n)
                    at += n
                }
            } else {
                out += batchable.chunked(maxBatch)
            }
        }
        out += singles
        return out
    }

    companion object {
        const val DEFAULT_HEADER = 0x7E0
    }
}
