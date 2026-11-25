package com.wrxhud.obd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PollPlannerTest {
    private fun item(key: String, pid: Int, tier: PollTier, mode: Int = 1, header: Int? = null) =
        PollItem(key, pid, tier, mode, header) { }

    private val rpm = item("rpm", 0x0C, PollTier.FAST)
    private val speed = item("speed", 0x0D, PollTier.FAST)
    private val throttle = item("throttle", 0x11, PollTier.FAST)
    private val pedal = item("pedal", 0x49, PollTier.FAST)
    private val map = item("map", 0x0B, PollTier.MEDIUM)
    private val coolant = item("coolant", 0x05, PollTier.SLOW)
    private val iat = item("iat", 0x0F, PollTier.SLOW)
    private val baro = item("baro", 0x33, PollTier.SLOW)
    private val volt = item("volt", 0x42, PollTier.SLOW)
    private val fuel = item("fuel", 0x2F, PollTier.SLOW)
    private val afr = item("afr", 0x34, PollTier.SLOW)
    private val oil = item("oil", 0x5C, PollTier.SLOW)
    private val mil = item("mil", 0x01, PollTier.MIL)

    private val all = listOf(rpm, speed, throttle, pedal, map, coolant, iat, baro, volt, fuel, afr, oil, mil)
    private val allSupported = all.map { it.pid }.toSet()

    private fun planner(items: List<PollItem> = all, supported: Set<Int> = allSupported) =
        PollPlanner().also { it.configure(items, supported) }

    private fun CyclePlan.keys() = mode01.map { req -> req.map { it.key } }

    @Test
    fun firstCycleBatchesFastMapAndMilTogetherAndOneSlowChunk() {
        val plan = planner().nextCycle(0)
        // fast(4) + map + mil = 6 in one request, then the first of 2 slow chunks.
        assertThat(plan.keys()[0]).containsExactly("rpm", "speed", "throttle", "pedal", "map", "mil").inOrder()
        assertThat(plan.keys()).hasSize(2)
        assertThat(plan.mode01[1].size).isEqualTo(4) // 7 slow PIDs -> chunks of 4 and 3
    }

    @Test
    fun fastTierEveryCycleMapEverySecondMilEveryFiveSeconds() {
        val p = planner()
        val fastKeys = listOf("rpm", "speed", "throttle", "pedal")
        var mapCycles = 0
        var milCycles = 0
        var t = 0L
        repeat(100) { c ->
            val pool = p.nextCycle(t).keys().first()
            assertThat(pool.take(4)).containsExactlyElementsIn(fastKeys).inOrder()
            if ("map" in pool) { mapCycles++; assertThat(c % 2).isEqualTo(0) }
            if ("mil" in pool) milCycles++
            t += 50 // 20 Hz
        }
        assertThat(mapCycles).isEqualTo(50)
        assertThat(milCycles).isEqualTo(1 + 99 * 50 / 5000) // t=0 plus every 5 s
    }

    @Test
    fun neverMoreThanSixPidsPerRequest() {
        val many = (1..14).map { item("p$it", 0x04 + it, PollTier.FAST) }
        val sup = many.map { it.pid }.toSet()
        val plan = planner(many, sup).nextCycle(0)
        assertThat(plan.mode01.all { it.size <= ObdCommands.MAX_MODE01_BATCH }).isTrue()
        assertThat(plan.mode01.sumOf { it.size }).isEqualTo(14)
    }

    @Test
    fun slowPidsEachRefreshedAboutOnceASecond() {
        val p = planner()
        val seen = mutableMapOf<String, Int>()
        var t = 0L
        repeat(200) { // 10 s at 50 ms cycles
            p.nextCycle(t).mode01.drop(1).flatten().forEach { seen.merge(it.key, 1, Int::plus) }
            t += 50
        }
        listOf("coolant", "iat", "baro", "volt", "fuel", "afr", "oil").forEach {
            assertThat(seen[it]!!).isIn(8..12)
        }
    }

    @Test
    fun onlyPidsInSupportedBitmaskAreBatched() {
        val supported = allSupported - setOf(0x49, 0x0D)
        val plan = planner(supported = supported).nextCycle(0)
        val firstBatch = plan.mode01[0].map { it.key }
        assertThat(firstBatch).containsAtLeast("rpm", "throttle", "map", "mil")
        assertThat(firstBatch).doesNotContain("speed")
        assertThat(firstBatch).doesNotContain("pedal")
        // unsupported ones still get polled, but alone
        assertThat(plan.mode01.filter { it.size == 1 }.map { it[0].key }).containsAtLeast("speed", "pedal")
    }

    @Test
    fun emptySupportedSetMeansEverythingGoesOutAlone() {
        val plan = planner(supported = emptySet()).nextCycle(0)
        assertThat(plan.mode01.all { it.size == 1 }).isTrue()
    }

    @Test
    fun unknownLengthPidIsNeverBatched() {
        val map87 = item("map", 0x87, PollTier.MEDIUM)
        val plan = planner(listOf(rpm, speed, map87), setOf(0x0C, 0x0D, 0x87)).nextCycle(0)
        assertThat(plan.keys()).containsExactly(listOf("rpm", "speed"), listOf("map")).inOrder()
    }

    @Test
    fun duplicatePidInOneBatchGoesAlone() {
        val dup = item("rpm2", 0x0C, PollTier.FAST)
        val plan = planner(listOf(rpm, dup, speed), setOf(0x0C, 0x0D)).nextCycle(0)
        assertThat(plan.keys()).containsExactly(listOf("rpm", "speed"), listOf("rpm2")).inOrder()
    }

    @Test
    fun degradeToSinglesAfterBatchFailures() {
        val p = planner(listOf(rpm, speed, throttle), setOf(0x0C, 0x0D, 0x11))
        assertThat(p.nextCycle(0).mode01).hasSize(1)
        p.health.batchFailed(0x0D) // strike 1: still batched
        assertThat(p.nextCycle(50).keys()[0]).contains("speed")
        p.health.batchFailed(0x0D) // strike 2: degraded
        val plan = p.nextCycle(100)
        assertThat(plan.keys()).containsExactly(listOf("rpm", "throttle"), listOf("speed")).inOrder()
        assertThat(p.health.noBatch).containsExactly(0x0D)
    }

    @Test
    fun successResetsStrikes() {
        val h = PollHealth()
        h.batchFailed(0x0D)
        h.batchSucceeded(0x0D)
        h.batchFailed(0x0D)
        assertThat(h.noBatch).isEmpty()
        h.batchFailed(0x0D, PollHealth.FAIL_LIMIT)
        assertThat(h.noBatch).containsExactly(0x0D)
    }

    @Test
    fun hintOnlyWhenAllPidsKnownSingleEcu() {
        val h = PollHealth()
        h.singleEcu += listOf(0x0C, 0x0D)
        assertThat(h.canHint(listOf(0x0C, 0x0D))).isTrue()
        assertThat(h.canHint(listOf(0x0C, 0x11))).isFalse()
        h.noHint += 0x0D
        assertThat(h.canHint(listOf(0x0C, 0x0D))).isFalse()
    }

    @Test
    fun mode22ItemsGroupedByHeaderOncePerSlowPeriod() {
        val a = item("a", 0x0113, PollTier.SLOW, 0x22, 0x7E0)
        val b = item("b", 0x0201, PollTier.SLOW, 0x22, null)
        val c = item("c", 0x0301, PollTier.SLOW, 0x22, 0x7E2)
        val p = planner(listOf(rpm, a, b, c), setOf(0x0C))
        val first = p.nextCycle(0).mode22
        assertThat(first.map { it.header }).containsExactly(0x7E0, 0x7E2).inOrder()
        assertThat(first[0].items.map { it.key }).containsExactly("a", "b").inOrder()
        assertThat(first[1].items.map { it.key }).containsExactly("c")
        assertThat(p.nextCycle(50).mode22).isEmpty()
        assertThat(p.nextCycle(990).mode22).isEmpty()
        assertThat(p.nextCycle(1000).mode22).hasSize(2)
    }

    @Test
    fun mediumMode22RunsEverySecondCycle() {
        val m = item("map", 0x000B, PollTier.MEDIUM, 0x22, 0x7E0)
        val p = planner(listOf(rpm, m), setOf(0x0C))
        val hits = (0 until 10).map { p.nextCycle(it * 50L).mode22.isNotEmpty() }
        assertThat(hits).containsExactly(true, false, true, false, true, false, true, false, true, false).inOrder()
    }
}
