package wc3viewer.parser.mapping

import wc3viewer.domain.model.ChatMessage
import wc3viewer.domain.model.GameEvent
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.LeaveEvent
import wc3viewer.domain.model.MapInfo
import wc3viewer.domain.model.MatchResult
import wc3viewer.domain.model.MiscKind
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.ParseDiagnostic
import wc3viewer.domain.model.Player
import wc3viewer.domain.model.PlayerId
import wc3viewer.domain.model.Race
import wc3viewer.domain.model.Replay
import wc3viewer.domain.model.SelectMode
import wc3viewer.domain.model.Target
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.domain.port.ReplayFormatException.Kind
import wc3viewer.parser.container.HeaderReader
import wc3viewer.parser.raw.RawAction
import wc3viewer.parser.raw.RawReplay
import wc3viewer.parser.raw.SlotRecord

/** Raw replay → domain [Replay]: players, observer filter, order classification, race detection, winner. */
internal object RawToDomainMapper {
    const val LEAVE_RESULT_WIN = 0x09
    private const val OBSERVER_TEAM = 24
    private const val SLOT_OCCUPIED = 2

    private val ABILITY_LEARN = ObjectId.of("AHer")
    private val ABILITY_QUEUE = ObjectId.of("Aque")
    private val ABILITY_UPGRADE = ObjectId.of("Aupg")
    private val ABILITY_SHOP = setOf(ObjectId.of("Amai"), ObjectId.of("Asei"))
    private val ABILITY_HIRE = ObjectId.of("Asel")

    fun map(raw: RawReplay): Replay {
        val diagnostics = raw.diagnostics.toMutableList()
        val meta = raw.metadata
        val names = meta.players.associate { it.playerId to it.name }

        val occupied = meta.slots.filter { it.status == SLOT_OCCUPIED && it.computer == 0 }
        val (observerSlots, playerSlots) = occupied.partition { isObserver(it, meta.slots.size) }
        if (playerSlots.size != 2 || playerSlots[0].team == playerSlots[1].team) {
            throw ReplayFormatException(
                Kind.UNSUPPORTED_GAME_MODE,
                "Only 1v1 replays are supported (found ${playerSlots.size} players in ${playerSlots.map { it.team }.distinct().size} teams).",
            )
        }
        val playerIds = playerSlots.map { it.playerId }.toSet()
        val observerIds = observerSlots.map { it.playerId }.toSet()

        val events = ArrayList<GameEvent>(raw.commands.size * 2)
        val heroState = HashMap<Int, HeroTracker>()
        for (cb in raw.commands) {
            if (cb.playerId !in playerIds) {
                if (cb.playerId !in observerIds && cb.actions.isNotEmpty()) {
                    diagnostics += ParseDiagnostic(GameTime(cb.timeMs), -1, "Command block of unknown player ${cb.playerId} skipped")
                }
                continue
            }
            mapBlock(cb.timeMs, PlayerId(cb.playerId), cb.actions, heroState.getOrPut(cb.playerId) { HeroTracker() }, events)
        }
        // Command blocks come in time order, so events are already sorted; keep it explicit and stable.
        events.sortBy { it.time.ms }

        val lastCommand = HashMap<Int, Long>()
        for (cb in raw.commands) lastCommand[cb.playerId] = cb.timeMs
        val players = playerSlots.map { s ->
            val race = raceOf(s.raceFlag)
            val pid = PlayerId(s.playerId)
            Player(
                id = pid,
                name = names[s.playerId] ?: "Player ${s.playerId}",
                race = race,
                detectedRace = if (race == Race.RANDOM || race == Race.UNKNOWN) detectRace(pid, events) else null,
                team = s.team,
                color = s.color,
                isObserver = false,
                lastCommandTime = GameTime(lastCommand[s.playerId] ?: 0),
            )
        }

        val leaves = raw.leaves.map { LeaveEvent(GameTime(it.timeMs), PlayerId(it.playerId), it.reason, it.result) }
        val playerLeaves = leaves.filter { it.player.value in playerIds }
        // Winner only among players: FLO may leave with 0x09 too (Replay_2026_09_16_0103).
        val winners = playerLeaves.filter { it.result == LEAVE_RESULT_WIN }.map { it.player }.distinct()
        val result = when (winners.size) {
            1 -> MatchResult.Winner(winners.single())
            0 -> MatchResult.Unknown
            else -> {
                diagnostics += ParseDiagnostic(null, -1, "Several players have leave result 0x09; winner is unknown")
                MatchResult.Unknown
            }
        }
        val headerDuration = GameTime(raw.header.durationMs)
        val duration = playerLeaves.maxOfOrNull { it.time } ?: headerDuration

        return Replay(
            patch = HeaderReader.patchOf(raw.header.version, raw.header.build),
            duration = duration,
            headerDuration = headerDuration,
            map = MapInfo(meta.mapPath, meta.mapCreator),
            gameName = meta.gameName,
            players = players,
            events = events,
            chat = raw.chat.map { ChatMessage(GameTime(it.timeMs), PlayerId(it.playerId), it.text) },
            leaves = leaves,
            result = result,
            diagnostics = diagnostics,
        )
    }

    private fun isObserver(slot: SlotRecord, slotCount: Int): Boolean =
        slot.team >= OBSERVER_TEAM || slot.team >= slotCount

    fun raceOf(flag: Int): Race = when (flag and 0x3F) {
        0x01 -> Race.HUMAN
        0x02 -> Race.ORC
        0x04 -> Race.NIGHT_ELF
        0x08 -> Race.UNDEAD
        0x20 -> Race.RANDOM
        else -> Race.UNKNOWN
    }

    /** Race by the prefix of the first train/build order: h/o/e/u (W3G_FORMAT §8). */
    fun detectRace(player: PlayerId, events: List<GameEvent>): Race? {
        for (e in events) {
            if (e.player != player) continue
            val id = when (e) {
                is GameEvent.Train -> e.unit
                is GameEvent.Build -> e.building
                else -> continue
            }
            when (id.code[0].lowercaseChar()) {
                'h' -> return Race.HUMAN
                'o' -> return Race.ORC
                'e' -> return Race.NIGHT_ELF
                'u' -> return Race.UNDEAD
            }
        }
        return null
    }

    /**
     * Tracks which own hero is active. The 0x7A subject does not identify the hero, but 0x19 (select subgroup)
     * carries the unit type. Only heroes the player has hired count: the player may select an enemy hero.
     */
    private class HeroTracker {
        val hired = HashSet<ObjectId>()
        var activeHero: ObjectId? = null
    }

    private fun mapBlock(
        timeMs: Long,
        player: PlayerId,
        actions: List<RawAction>,
        heroes: HeroTracker,
        out: MutableList<GameEvent>,
    ) {
        val time = GameTime(timeMs)
        var context: RawAction.AbilityContext? = null
        // w3gjs: the deselect flag is reset at the start of every command block.
        var lastWasDeselect = false
        for (a in actions) {
            when (a) {
                is RawAction.AbilityContext -> context = a
                is RawAction.Order -> {
                    val e = mapOrder(time, player, a, context, heroes.activeHero)
                    if (e is GameEvent.Train && isHeroId(e.unit)) heroes.hired += e.unit
                    out += e
                }
                is RawAction.SelectSubgroup -> {
                    val type = ObjectId(a.obj)
                    if (type in heroes.hired) heroes.activeHero = type
                }
                is RawAction.ChangeSelection -> {
                    val mode = if (a.mode == 0x02) SelectMode.REMOVE else SelectMode.ADD
                    val counts = if (mode == SelectMode.REMOVE) true else !lastWasDeselect
                    lastWasDeselect = mode == SelectMode.REMOVE
                    out += GameEvent.Select(time, player, mode, a.units, counts)
                }
                is RawAction.AssignGroup -> out += GameEvent.HotkeyAssign(time, player, a.group, a.units)
                is RawAction.SelectGroup -> out += GameEvent.HotkeySelect(time, player, a.group)
                is RawAction.RemoveFromQueue -> out += GameEvent.CancelQueue(time, player, a.slot, ObjectId(a.obj))
                is RawAction.Other -> miscKind(a.id)?.let { (kind, apm) -> out += GameEvent.Misc(time, player, kind, apm) }
            }
        }
    }

    /** APM-relevant misc actions (TEST_STRATEGY §4: 0x65 is not counted by w3gjs). */
    private fun miscKind(id: Int): Pair<MiscKind, Boolean>? = when (id) {
        0x61 -> MiscKind.ESC to true
        0x66 -> MiscKind.HERO_SKILL_MENU to true
        0x67 -> MiscKind.BUILD_MENU to true
        0x1C -> MiscKind.SELECT_GROUND_ITEM to true
        0x1D -> MiscKind.CANCEL_HERO_REVIVAL to true
        0x65 -> MiscKind.TRACK_OBJECT to false
        else -> null
    }

    /** Hero IDs start with an upper-case letter; upgrades ('R…') and abilities ('A…') do not count. */
    fun isHeroId(id: ObjectId): Boolean = ObjectId.isFourCC(id.raw) && id.code[0] in "HOEUN"

    private fun mapOrder(
        time: GameTime,
        player: PlayerId,
        a: RawAction.Order,
        ctx: RawAction.AbilityContext?,
        activeHero: ObjectId?,
    ): GameEvent {
        val queued = a.flags and 0x0001 != 0
        if (!ObjectId.isFourCC(a.order) || a.id == 0x13) {
            val ability = ctx?.ability?.takeIf { ObjectId.isFourCC(it) }?.let(::ObjectId)
            return GameEvent.Order(time, player, a.order, ability, a.target, queued)
        }
        val obj = ObjectId(a.order)
        if (a.id != 0x10) {
            // Order with a point and an object fourcc = place a building (0x12 for haunted gold mine).
            val (x, y) = when (val t = a.target) {
                is Target.Point -> t.x to t.y
                is Target.Unit -> t.x to t.y
                Target.None -> 0f to 0f
            }
            return GameEvent.Build(time, player, obj, x, y, queued)
        }
        val ability = ctx?.ability?.let(::ObjectId)
        val code = obj.code
        return when {
            ability == ABILITY_LEARN -> GameEvent.LearnSkill(time, player, ctx.subject, activeHero, obj)
            ability == ABILITY_UPGRADE -> GameEvent.UpgradeBuilding(time, player, obj)
            ability in ABILITY_SHOP -> GameEvent.BuyItem(time, player, obj, ctx?.subject)
            ability == ABILITY_HIRE -> GameEvent.Train(time, player, obj, queued)
            ability == ABILITY_QUEUE && code[0] == 'R' -> GameEvent.Research(time, player, obj)
            ability == ABILITY_QUEUE -> GameEvent.Train(time, player, obj, queued)
            // No 0x7A context: fall back to the ID prefix.
            code[0] == 'R' -> GameEvent.Research(time, player, obj)
            code[0] == 'A' -> GameEvent.LearnSkill(time, player, null, activeHero, obj)
            else -> GameEvent.Train(time, player, obj, queued)
        }
    }
}
