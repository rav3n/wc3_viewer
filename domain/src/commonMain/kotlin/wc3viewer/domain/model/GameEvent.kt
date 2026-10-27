package wc3viewer.domain.model

/** Order target. */
sealed interface Target {
    data object None : Target
    data class Point(val x: Float, val y: Float) : Target
    data class Unit(val x: Float, val y: Float, val unit: UnitRef) : Target
}

enum class SelectMode { ADD, REMOVE }

enum class MiscKind { ESC, HERO_SKILL_MENU, BUILD_MENU, SELECT_GROUND_ITEM, CANCEL_HERO_REVIVAL, TRACK_OBJECT, OTHER }

/**
 * Domain event. Train/Build/Research are **orders**, not the fact that an object appeared.
 * [countsForApm] — whether the event is an APM action (w3gjs rules, ARCHITECTURE §4.4).
 */
sealed interface GameEvent {
    val time: GameTime
    val player: PlayerId
    val countsForApm: Boolean

    /** Unit training or hero hire (including the tavern). */
    data class Train(
        override val time: GameTime, override val player: PlayerId,
        val unit: ObjectId, val queued: Boolean,
    ) : GameEvent { override val countsForApm get() = true }

    data class Build(
        override val time: GameTime, override val player: PlayerId,
        val building: ObjectId, val x: Float, val y: Float, val queued: Boolean,
    ) : GameEvent { override val countsForApm get() = true }

    /** Building upgrade: Town Hall → Keep, towers, ziggurats. */
    data class UpgradeBuilding(
        override val time: GameTime, override val player: PlayerId,
        val building: ObjectId,
    ) : GameEvent { override val countsForApm get() = true }

    data class Research(
        override val time: GameTime, override val player: PlayerId,
        val upgrade: ObjectId,
    ) : GameEvent { override val countsForApm get() = true }

    data class LearnSkill(
        override val time: GameTime, override val player: PlayerId,
        val hero: UnitRef?,
        /** Hero type from the active selection subgroup (0x19); null if unknown. */
        val heroType: ObjectId?,
        val ability: ObjectId,
    ) : GameEvent { override val countsForApm get() = true }

    data class BuyItem(
        override val time: GameTime, override val player: PlayerId,
        val item: ObjectId, val shop: UnitRef?,
    ) : GameEvent { override val countsForApm get() = true }

    data class CancelQueue(
        override val time: GameTime, override val player: PlayerId,
        val slot: Int, val obj: ObjectId,
    ) : GameEvent { override val countsForApm get() = true }

    /**
     * Any other order (move, attack, smart, ability).
     * [order] — raw u32 from the file; [ability] — ability from the preceding 0x7A, if any.
     */
    data class Order(
        override val time: GameTime, override val player: PlayerId,
        val order: Int, val ability: ObjectId?, val target: Target, val queued: Boolean,
    ) : GameEvent { override val countsForApm get() = true }

    /** Selection change (0x16). [countsForApm] = false for a select right after a deselect. */
    data class Select(
        override val time: GameTime, override val player: PlayerId,
        val mode: SelectMode, val units: List<UnitRef>,
        override val countsForApm: Boolean,
    ) : GameEvent

    /** Hotkey group assignment. [group] — raw number 0..9 from the file. */
    data class HotkeyAssign(
        override val time: GameTime, override val player: PlayerId,
        val group: Int, val units: List<UnitRef>,
    ) : GameEvent { override val countsForApm get() = true }

    data class HotkeySelect(
        override val time: GameTime, override val player: PlayerId,
        val group: Int,
    ) : GameEvent { override val countsForApm get() = true }

    data class Misc(
        override val time: GameTime, override val player: PlayerId,
        val kind: MiscKind,
        override val countsForApm: Boolean,
    ) : GameEvent
}

/** Hotkey group key: w3gjs uses `(group + 1) % 10` (ARCHITECTURE §10 item 8: to verify). */
fun hotkeyKey(group: Int): Int = (group + 1) % 10
