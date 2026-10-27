package wc3viewer.presentation

import wc3viewer.analytics.apm.ActionRateResult
import wc3viewer.analytics.apm.ApmMetric
import wc3viewer.analytics.apm.ApmResult
import wc3viewer.analytics.apm.EpmMetric
import wc3viewer.analytics.apm.EpmResult
import wc3viewer.analytics.buildorder.BuildOrderKind
import wc3viewer.analytics.buildorder.BuildOrderMetric
import wc3viewer.analytics.buildorder.BuildOrderResult
import wc3viewer.analytics.economy.EconomyEstimateMetric
import wc3viewer.analytics.economy.EconomyResult
import wc3viewer.analytics.economy.PlayerEconomy
import wc3viewer.analytics.economy.SpendCategory
import wc3viewer.analytics.heroes.HeroSkillsMetric
import wc3viewer.analytics.heroes.HeroSkillsResult
import wc3viewer.analytics.hotkeys.HotkeyMetric
import wc3viewer.analytics.hotkeys.HotkeyResult
import wc3viewer.domain.gamedata.GameData
import wc3viewer.domain.metric.AnalysisReport
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.hotkeyKey
import wc3viewer.domain.model.MatchResult
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Race
import wc3viewer.domain.port.ReplayFormatException

/** AnalysisReport → UI models. Pure Kotlin, tested on JVM. */
object ReportMapper {
    fun overview(report: AnalysisReport): MatchOverview {
        val r = report.replay
        val winnerId = (r.result as? MatchResult.Winner)?.player
        return MatchOverview(
            players = r.players.mapIndexed { i, p ->
                val race = if (p.race == Race.RANDOM) "Random → ${raceName(p.detectedRace ?: Race.UNKNOWN)}" else raceName(p.race)
                PlayerUi(p.id.value, p.name, race, p.id == winnerId, i, raceKey(p.effectiveRace), p.race == Race.RANDOM)
            },
            map = r.map.name,
            duration = r.duration.format(),
            patch = "${r.patch.major}.${r.patch.minor.toString().padStart(2, '0')} (build ${r.patch.build})",
            winner = winnerId?.let { id -> r.player(id)?.name } ?: "Unknown",
            warnings = r.diagnostics.map { it.message },
            durationSec = r.duration.ms / 1000f,
        )
    }

    /** [names] — game data for display names (the set the analysis used). */
    fun sections(report: AnalysisReport, names: GameData): ReplaySections {
        val nameOfId = { id: ObjectId -> names.find(id)?.name }
        val apm = report.get<ApmResult>(ApmMetric.id)
        val epm = report.get<EpmResult>(EpmMetric.id)
        val bo = report.get<BuildOrderResult>(BuildOrderMetric.id)
        val heroes = report.get<HeroSkillsResult>(HeroSkillsMetric.id)
        val hotkeys = report.get<HotkeyResult>(HotkeyMetric.id)
        val players = report.replay.players
        val nameOf = { id: Int -> players.firstOrNull { it.id.value == id }?.name ?: "Player $id" }
        val boRows = bo?.players.orEmpty().entries.associate { (pid, list) ->
            pid.value to list.map {
                BuildOrderRow(
                    time = it.time.format(), name = it.name ?: it.obj.code, kind = kindName(it.kind),
                    timeSec = it.time.sec, code = it.obj.code, kindKey = kindKey(it.kind), hasName = it.name != null,
                )
            }
        }
        val hireOrders = heroes?.players.orEmpty().entries.associate { (pid, h) ->
            pid.value to h.orders.map { HeroOrderUi(it.time.format(), it.time.sec, it.hero.code, nameOfId(it.hero)) }
        }
        return ReplaySections(
            rates = players.map { p ->
                val a = apm?.of(p.id)
                val e = epm?.of(p.id)
                val avgApm = a?.average ?: 0
                val avgEpm = e?.average ?: 0
                val spam = if (avgApm > 0) ((avgApm - avgEpm) * 100 / avgApm).coerceAtLeast(0) else 0
                RateSummary(p.id.value, avgApm, avgEpm, a?.actions ?: 0, spam)
            },
            apmSeries = series(apm, nameOf),
            epmSeries = series(epm, nameOf),
            chartMaxX = report.replay.duration.ms / 1000f,
            buildOrders = boRows,
            heroes = heroes?.players.orEmpty().entries.associate { (pid, h) ->
                val orders = hireOrders[pid.value].orEmpty()
                pid.value to h.heroes.map { hs ->
                    val code = hs.heroType?.code
                    val heroName = hs.heroType?.let(nameOfId)
                    HeroUi(
                        hero = heroName ?: code ?: "Unknown hero",
                        hasName = heroName != null,
                        firstSkill = hs.firstSkill.format(),
                        levelEstimate = hs.levelEstimate,
                        retrainedAt = hs.retrainedAt?.format(),
                        heroCode = code,
                        firstSkillSec = hs.firstSkill.sec,
                        skills = hs.skills.mapIndexed { i, s -> SkillPickUi(s.time.format(), s.time.sec, s.ability.code, nameOfId(s.ability), i + 1) },
                        // Hire order of the same hero type that precedes the first skill.
                        hiredAt = orders.lastOrNull { it.code == code && it.timeSec <= hs.firstSkill.sec }?.time,
                    )
                }
            },
            heroOrders = hireOrders,
            hotkeys = hotkeys?.players.orEmpty().entries.associate { (pid, groups) ->
                pid.value to groups.map { HotkeyRow(it.key, it.assigned, it.selected, it.lastAssignedSize, it.firstAssigned?.format()) }
            },
            timings = players.associate { p -> p.id.value to timings(boRows[p.id.value].orEmpty()) },
            hotkeyHeat = hotkeyHeat(report),
            economy = report.get<EconomyResult>(EconomyEstimateMetric.id)?.players.orEmpty().entries.associate { (pid, e) ->
                pid.value to economy(pid.value, nameOf(pid.value), e, report.replay.duration.sec)
            },
        )
    }

    /** Economy estimate → step series up to [endSec]. */
    internal fun economy(playerId: Int, label: String, e: PlayerEconomy, endSec: Float): EconomyUi {
        val pts = e.points
        fun steps(value: (Int) -> Int): ChartSeries {
            val xs = ArrayList<Float>(pts.size * 2 + 1)
            val ys = ArrayList<Float>(pts.size * 2 + 1)
            for (i in pts.indices) {
                val x = pts[i].time.sec
                if (i > 0) { xs += x; ys += value(i - 1).toFloat() }
                xs += x; ys += value(i).toFloat()
            }
            if (pts.isNotEmpty() && endSec > xs.last()) { xs += endSec; ys += ys.last() }
            return ChartSeries(playerId, label, xs.toFloatArray(), ys.toFloatArray())
        }
        return EconomyUi(
            playerId = playerId,
            gold = steps { pts[it].gold },
            lumber = steps { pts[it].lumber },
            total = steps { pts[it].gold + pts[it].lumber },
            foodUsed = steps { pts[it].foodUsed },
            foodCap = steps { pts[it].foodCap },
            categories = SpendCategory.entries.map { c ->
                val cost = e.byCategory[c]
                CategorySpendUi(categoryName(c), cost?.gold ?: 0, cost?.lumber ?: 0)
            },
            totalGold = e.total.gold,
            totalLumber = e.total.lumber,
            upkeep50 = e.upkeep50?.format(),
            upkeep80 = e.upkeep80?.format(),
            unpricedOrders = e.unpricedOrders,
            cancels = e.cancels,
        )
    }

    fun categoryName(c: SpendCategory) = when (c) {
        SpendCategory.ECONOMY -> "Economy"
        SpendCategory.ARMY -> "Army"
        SpendCategory.TECH -> "Tech"
        SpendCategory.HEROES -> "Heroes"
        SpendCategory.ITEMS -> "Items"
    }

    private val GameTime.sec: Float get() = ms / 1000f

    // Main hall upgrades (tier 2, tier 3) and main halls (an expansion is the first main hall ordered:
    // the starting one is pre-placed, never ordered).
    private val TIER2 = setOf("hkee", "ostr", "unp1", "etoa")
    private val TIER3 = setOf("hcas", "ofrt", "unp2", "etoe")
    private val MAIN_HALLS = setOf("htow", "ogre", "unpl", "etol")

    /** Key timings derived from build-order rows (order times). */
    internal fun timings(rows: List<BuildOrderRow>): PlayerTimings {
        fun first(codes: Set<String>, kind: ObjectKind) =
            rows.firstOrNull { it.kindKey == kind && it.code in codes }?.let(::timing)
        return PlayerTimings(
            heroes = rows.filter { it.kindKey == ObjectKind.HERO }.distinctBy { it.code }.take(3)
                .map(::timing),
            tier2 = first(TIER2, ObjectKind.BUILDING),
            tier3 = first(TIER3, ObjectKind.BUILDING),
            expansion = first(MAIN_HALLS, ObjectKind.BUILDING),
        )
    }

    private fun timing(r: BuildOrderRow) = TimingUi(r.time, r.timeSec, r.code, if (r.hasName) r.name else null)

    /** Hotkey group selections per key and minute, from the raw events. */
    private fun hotkeyHeat(report: AnalysisReport, bucketSec: Int = 60): Map<Int, HotkeyHeat> {
        val buckets = ((report.replay.duration.ms / 1000 + bucketSec - 1) / bucketSec).toInt().coerceAtLeast(1)
        val counts = report.replay.players.associate { it.id to Array(10) { IntArray(buckets) } }
        for (e in report.replay.events) {
            if (e !is GameEvent.HotkeySelect) continue
            val row = counts[e.player]?.getOrNull(e.group) ?: continue
            val b = (e.time.ms / 1000 / bucketSec).toInt().coerceIn(0, buckets - 1)
            row[b]++
        }
        return counts.entries.associate { (pid, groups) ->
            val used = groups.indices.filter { g -> groups[g].any { it > 0 } }
                .sortedBy { g -> hotkeyKey(g).let { if (it == 0) 10 else it } }
            pid.value to HotkeyHeat(used.map(::hotkeyKey), bucketSec, used.map { groups[it] })
        }
    }

    private fun series(r: ActionRateResult?, nameOf: (Int) -> String): List<ChartSeries> =
        r?.players.orEmpty().map { p ->
            ChartSeries(
                p.player.value,
                nameOf(p.player.value),
                FloatArray(p.series.size) { p.series[it].time.ms / 1000f },
                FloatArray(p.series.size) { p.series[it].perMinute },
            )
        }

    fun raceName(r: Race) = when (r) {
        Race.HUMAN -> "Human"
        Race.ORC -> "Orc"
        Race.NIGHT_ELF -> "Night Elf"
        Race.UNDEAD -> "Undead"
        Race.RANDOM -> "Random"
        Race.UNKNOWN -> "Unknown"
    }

    private fun raceKey(r: Race) = when (r) {
        Race.HUMAN -> RaceUi.HUMAN
        Race.ORC -> RaceUi.ORC
        Race.NIGHT_ELF -> RaceUi.NIGHT_ELF
        Race.UNDEAD -> RaceUi.UNDEAD
        Race.RANDOM, Race.UNKNOWN -> RaceUi.UNKNOWN
    }

    private fun kindKey(k: BuildOrderKind) = when (k) {
        BuildOrderKind.TRAIN -> ObjectKind.UNIT
        BuildOrderKind.HERO -> ObjectKind.HERO
        BuildOrderKind.BUILD -> ObjectKind.BUILDING
        BuildOrderKind.RESEARCH -> ObjectKind.RESEARCH
        BuildOrderKind.ITEM -> ObjectKind.ITEM
        BuildOrderKind.CANCEL -> ObjectKind.CANCEL
    }

    private fun kindName(k: BuildOrderKind) = when (k) {
        BuildOrderKind.TRAIN -> "Unit"
        BuildOrderKind.HERO -> "Hero"
        BuildOrderKind.BUILD -> "Building"
        BuildOrderKind.RESEARCH -> "Research"
        BuildOrderKind.ITEM -> "Item"
        BuildOrderKind.CANCEL -> "Cancel"
    }

    /** User-facing error text per failure kind. */
    fun errorMessage(e: Throwable): String = when (e) {
        is ReplayFormatException -> when (e.kind) {
            ReplayFormatException.Kind.EMPTY_FILE -> "File is empty. The replay was probably not saved correctly."
            ReplayFormatException.Kind.NOT_A_REPLAY -> "Not a Warcraft III replay. ${e.message}"
            ReplayFormatException.Kind.UNSUPPORTED_VERSION -> e.message ?: "Unsupported game version."
            ReplayFormatException.Kind.UNSUPPORTED_GAME_MODE -> "Only 1v1 replays are supported."
            ReplayFormatException.Kind.CORRUPTED -> "The replay file is corrupted. ${e.message}"
        }
        else -> "Unexpected error while analyzing the replay: ${e.message ?: e::class.simpleName}"
    }

    /** Short title for the error screen. */
    fun errorTitle(e: Throwable): String = when ((e as? ReplayFormatException)?.kind) {
        ReplayFormatException.Kind.EMPTY_FILE -> "The file is empty"
        ReplayFormatException.Kind.NOT_A_REPLAY -> "Not a Warcraft III replay"
        ReplayFormatException.Kind.UNSUPPORTED_VERSION -> "Unsupported game version"
        ReplayFormatException.Kind.UNSUPPORTED_GAME_MODE -> "Not a 1v1 replay"
        ReplayFormatException.Kind.CORRUPTED -> "The replay is damaged"
        null -> "Can't analyze this replay"
    }

    /** What the user can do next, per failure kind. */
    fun errorHints(e: Throwable): List<String> = when ((e as? ReplayFormatException)?.kind) {
        ReplayFormatException.Kind.EMPTY_FILE, ReplayFormatException.Kind.CORRUPTED -> listOf(
            "Copy the replay again: the game may still have been writing it.",
            "Try another replay of the same match, e.g. from W3Champions.",
        )
        ReplayFormatException.Kind.NOT_A_REPLAY -> listOf(
            "Pick a .w3g file from the game's Replays folder.",
        )
        ReplayFormatException.Kind.UNSUPPORTED_VERSION -> listOf(
            "Replays from patch 3.0 (Reforged) and newer are supported.",
        )
        ReplayFormatException.Kind.UNSUPPORTED_GAME_MODE -> listOf(
            "Pick a 1v1 game. Team games and FFA are planned for a later version.",
        )
        null -> listOf("Try another replay. If it fails too, report the file so we can fix the parser.")
    }
}
