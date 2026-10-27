package wc3viewer.parser

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import wc3viewer.analytics.MetricRegistry
import wc3viewer.analytics.apm.ApmMetric
import wc3viewer.analytics.apm.ApmResult
import wc3viewer.analytics.apm.EpmMetric
import wc3viewer.analytics.apm.EpmResult
import wc3viewer.analytics.buildorder.BuildOrderKind
import wc3viewer.analytics.buildorder.BuildOrderMetric
import wc3viewer.analytics.buildorder.BuildOrderResult
import wc3viewer.analytics.heroes.HeroSkillsMetric
import wc3viewer.analytics.heroes.HeroSkillsResult
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.MatchResult
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.usecase.AnalyzeReplay
import wc3viewer.gamedata.ProductionGameData
import wc3viewer.parser.raw.RawAction
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Golden tests against testdata/expected (independent Python generator). Tolerances: TEST_STRATEGY §2. */
class GoldenReplayTest {
    private val parser = W3gParser()
    private val analyze = AnalyzeReplay(parser, ProductionGameData.repository, MetricRegistry.default)

    @Test
    fun fixturesArePresent() {
        assertTrue(Fixtures.replays.size >= 10, "replays in testdata/replays/3.00: ${Fixtures.replays.size}")
        for (f in Fixtures.replays) assertTrue(Fixtures.expected(f).exists(), "missing golden for ${f.name}")
    }

    @Test
    fun allReplaysMatchGolden() {
        val failures = mutableListOf<String>()
        for (f in Fixtures.replays) {
            try {
                check(f)
            } catch (e: AssertionError) {
                failures += "${f.name}: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    /** IDs listed in w3gjs mappings.ts (MIT), the classifier of the golden generator. */
    private val knownIds: Set<String> by lazy {
        // Object keys (`rnec: "..."`) plus hero IDs, which appear only as values of abilityToHero.
        val key = Regex("""^\s*"?([A-Za-z0-9]{4})"?\s*:""")
        val value = Regex(""":\s*"([A-Za-z0-9]{4})"""")
        java.io.File(Fixtures.root, "gamedata/sources/w3gjs-mappings.ts").readLines().flatMap { line ->
            listOfNotNull(key.find(line)?.groupValues?.get(1), value.find(line)?.groupValues?.get(1))
        }.toSet()
    }

    /** Tome of Retraining case (TEST_STRATEGY §6): Archmage of KuaMax#1136 has level 5 per W3C API. */
    @Test
    fun retrainingResetsArchmageSkills() = runBlocking {
        val f = Fixtures.replays.first { it.name == "6ae0bcf801d5ffcb2ad75e54.w3g" }
        val report = analyze(f.readBytes())
        val pid = report.replay.players.first { it.name == "KuaMax#1136" }.id
        val hamg = report.get<HeroSkillsResult>(HeroSkillsMetric.id)!!.players.getValue(pid).heroes
            .first { it.heroType?.code == "Hamg" }
        // Tome bought at ~1 863 900 ms; the re-learn burst follows within a minute.
        assertTrue(hamg.retrainedAt!!.ms in 1_863_900L..1_923_900L, "retrainedAt=${hamg.retrainedAt}")
        assertEquals(5, hamg.levelEstimate)
    }

    private val JsonElement.i get() = jsonPrimitive.int
    private val JsonElement.l get() = jsonPrimitive.long
    private val JsonElement.s get() = jsonPrimitive.content

    private fun check(file: File) = runBlocking {
        val exp = Json.parseToJsonElement(Fixtures.expected(file).readText()).jsonObject
        val bytes = file.readBytes()
        val raw = parser.parseRaw(bytes)
        val report = analyze(bytes)
        val replay = report.replay
        val name = file.name

        assertEquals(exp["version"]!!.i, raw.header.version, "$name version")
        assertEquals(exp["build"]!!.i, raw.header.build, "$name build")
        assertEquals(exp["patch"]!!.s, "${replay.patch.major}.${replay.patch.minor.toString().padStart(2, '0')}", "$name patch")
        assertEquals(exp["durationMs"]!!.l, replay.headerDuration.ms, "$name durationMs")
        assertEquals(exp["gameTimeSumMs"]!!.l, raw.stats.totalTimeMs, "$name gameTimeSumMs")
        assertEquals(exp["matchEndMs"]!!.l, replay.duration.ms, "$name matchEndMs")
        assertEquals(exp["gameName"]!!.s, replay.gameName, "$name gameName")
        val map = exp["map"]!!.jsonObject
        assertEquals(map["path"]!!.s, replay.map.path, "$name map.path")
        assertEquals(map["name"]!!.s, replay.map.name, "$name map.name")
        assertEquals(exp["reforgedChunks"]!!.i, raw.metadata.reforgedChunks, "$name reforgedChunks")

        // Parse invariants.
        val parse = exp["parse"]!!.jsonObject
        assertEquals(parse["gameDataOffset"]!!.i, raw.metadata.gameDataOffset, "$name gameDataOffset")
        assertEquals(parse["commandBlocksExact"]!!.i, raw.stats.commandBlocksExact, "$name commandBlocksExact")
        assertEquals(raw.stats.commandBlocks, raw.stats.commandBlocksExact, "$name: all command blocks parsed to the boundary")
        assertTrue(replay.diagnostics.isEmpty(), "$name diagnostics: ${replay.diagnostics.take(3)}")
        assertTrue(replay.events.zipWithNext().all { (a, b) -> a.time <= b.time }, "$name events sorted")
        assertEquals(exp["chatMessages"]!!.i, raw.chat.size, "$name chat")

        // Leaves and winner (winner only among players).
        val leaves = exp["leaves"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            leaves.map { listOf(it["timeMs"]!!.l, it["pid"]!!.l, it["reason"]!!.s.removePrefix("0x").toLong(16), it["result"]!!.s.removePrefix("0x").toLong(16)) },
            replay.leaves.map { listOf(it.time.ms, it.player.value.toLong(), it.reason.toLong(), it.result.toLong()) },
            "$name leaves",
        )
        val winner = exp["winnerPid"]!!
        val expectedResult = if (winner is JsonNull) MatchResult.Unknown else MatchResult.Winner(PlayerId(winner.i))
        assertEquals(expectedResult, replay.result, "$name winner")

        val apm = report.get<ApmResult>(ApmMetric.id)!!
        val epm = report.get<EpmResult>(EpmMetric.id)!!
        val bo = report.get<BuildOrderResult>(BuildOrderMetric.id)!!
        val heroes = report.get<HeroSkillsResult>(HeroSkillsMetric.id)!!

        val expPlayers = exp["players"]!!.jsonArray.map { it.jsonObject }
        assertEquals(expPlayers.map { it["pid"]!!.i }, replay.players.map { it.id.value }, "$name player ids (observers excluded)")
        for (ep in expPlayers) {
            val pid = PlayerId(ep["pid"]!!.i)
            val p = replay.player(pid)!!
            val who = "$name p${pid.value}"
            assertEquals(ep["name"]!!.s, p.name, "$who name")
            assertEquals(ep["team"]!!.i, p.team, "$who team")
            assertEquals(ep["color"]!!.i, p.color, "$who color")
            assertEquals(ep["race"]!!.s, p.race.name, "$who race")
            assertEquals(ep["raceResolved"]!!.s, p.effectiveRace.name, "$who raceResolved")

            val blocks = raw.commands.filter { it.playerId == pid.value }
            assertEquals(ep["commandBlocks"]!!.i, blocks.size, "$who commandBlocks")
            val counts = blocks.flatMap { it.actions }.groupingBy { "0x" + it.id.toString(16).padStart(2, '0') }.eachCount()
            val expCounts = ep["actionCounts"]!!.jsonObject.mapValues { it.value.i }
            assertEquals(expCounts.toSortedMap(), counts.toSortedMap(), "$who actionCounts")

            val ea = ep["apmW3gjs"]!!.jsonObject
            val rate = apm.of(pid)!!
            assertEquals(ea["countedActions"]!!.i, rate.actions, "$who APM counted actions")
            assertEquals(ea["lastCommandBlockMs"]!!.l, p.lastCommandTime.ms, "$who lastCommandBlockMs")
            assertEquals(ea["apm"]!!.i, rate.average, "$who APM")
            assertTrue(epm.of(pid)!!.actions <= rate.actions, "$who EPM <= APM")

            // Build order: first 15 orders of the golden without skills (skills live in the hero metric).
            val expBo = ep["buildOrderFirst15"]!!.jsonArray.map { it.jsonObject }.filter { it["type"]!!.s != "skill" }
            // The generator classifies IDs via w3gjs mappings.ts and drops unknown ones (e.g. 'ritd', Ritual Dagger).
            // We classify by the 0x7A ability context, so compare only IDs the generator knows.
            val ours = bo.players.getValue(pid)
                .filter { it.kind != BuildOrderKind.CANCEL && it.obj.code in knownIds }
                .take(expBo.size)
            assertEquals(
                expBo.map { Triple(it["timeMs"]!!.l, it["id"]!!.s, it["type"]!!.s) },
                ours.map { Triple(it.time.ms, it.obj.code, it.kind.name.lowercase()) },
                "$who buildOrderFirst15",
            )

            // Heroes: number of heroes with skills, skills learned (deduplicated) and first skill times.
            val expHeroes = ep["heroes"]!!.jsonArray.map { it.jsonObject }.filter { it["firstSkillMs"] !is JsonNull }
            val ourHeroes = heroes.players.getValue(pid).heroes
            // The generator does not model Tome of Retraining (6ae0bcf8, TEST_STRATEGY §6):
            // for a retrained hero only check that we do not count more than the generator.
            for (h in ourHeroes.filter { it.retrainedAt != null }) {
                val e = expHeroes.first { it["id"]!!.s == h.heroType?.code }
                assertTrue(h.skills.size <= e["skillOrders"]!!.i, "$who retrained ${h.heroType}")
            }
            assertEquals(
                expHeroes.map { Triple(it["id"]!!.s, it["firstSkillMs"]!!.l, it["skillsLearned"]!!.i) },
                ourHeroes.map { h ->
                    val e = expHeroes.firstOrNull { it["id"]!!.s == h.heroType?.code }
                    val learned = if (h.retrainedAt != null && e != null) e["skillsLearned"]!!.i else h.skills.size
                    Triple(h.heroType?.code, h.firstSkill.ms, learned)
                },
                "$who heroes (first skill, skills learned)",
            )
            val expHeroOrders = ep["heroes"]!!.jsonArray.map { it.jsonObject }.filter { it["firstOrderMs"] !is JsonNull }
                .map { it["id"]!!.s to it["firstOrderMs"]!!.l }.toSet()
            val ourHeroOrders = heroes.players.getValue(pid).orders.groupBy { it.hero }
                .map { (id, list) -> id.code to list.first().time.ms }.toSet()
            assertEquals(expHeroOrders, ourHeroOrders, "$who hero hire orders")
        }

        // Header duration matches the sum of time increments only without pauses (TEST_STRATEGY §12).
        val paused = raw.commands.any { cb -> cb.actions.any { it is RawAction.Other && it.id == 0x01 } }
        if (!paused) {
            assertTrue(abs(raw.header.durationMs - raw.stats.totalTimeMs) <= 50, "$name header vs time sum")
        }
    }
}
