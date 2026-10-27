package wc3viewer.parser.raw

import wc3viewer.domain.model.ParseDiagnostic
import wc3viewer.domain.model.Target
import wc3viewer.domain.model.UnitRef

/** Header + SubHeader (W3G_FORMAT §2–3). */
data class ReplayHeader(
    val headerSize: Int,
    val compressedSize: Long,
    val headerVersion: Int,
    val decompressedSize: Long,
    val blockCount: Int,
    val product: String,
    val version: Int,
    val build: Int,
    val flags: Int,
    val durationMs: Long,
)

data class PlayerRecord(val playerId: Int, val name: String)

/** GameStartRecord slot (W3G_FORMAT §5.4). */
data class SlotRecord(
    val index: Int,
    val playerId: Int,
    val status: Int,
    val computer: Int,
    val team: Int,
    val color: Int,
    val raceFlag: Int,
    val handicap: Int,
)

data class GameMetadata(
    val host: PlayerRecord,
    val gameName: String,
    val mapPath: String,
    val mapCreator: String,
    val players: List<PlayerRecord>,
    val slots: List<SlotRecord>,
    val reforgedChunks: Int,
    /** Offset of the first game block in the decompressed buffer. */
    val gameDataOffset: Int,
)

/** Decoded action (payload without the ID byte). */
sealed interface RawAction {
    val id: Int

    /** 0x10–0x15: order. [target] already decoded; [item] only for 0x13. */
    data class Order(
        override val id: Int,
        val flags: Int,
        val order: Int,
        val target: Target,
        val item: UnitRef?,
    ) : RawAction

    /** 0x16 */
    data class ChangeSelection(val mode: Int, val units: List<UnitRef>) : RawAction {
        override val id: Int get() = 0x16
    }

    /** 0x17 */
    data class AssignGroup(val group: Int, val units: List<UnitRef>) : RawAction {
        override val id: Int get() = 0x17
    }

    /** 0x18 */
    data class SelectGroup(val group: Int) : RawAction {
        override val id: Int get() = 0x18
    }

    /** 0x19: active subgroup of the selection; [obj] is the unit type (e.g. 'Edem'). */
    data class SelectSubgroup(val obj: Int, val unit: UnitRef) : RawAction {
        override val id: Int get() = 0x19
    }

    /** 0x1E / 0x1F */
    data class RemoveFromQueue(override val id: Int, val slot: Int, val obj: Int) : RawAction

    /** 0x7A in patch 3.0 numbering (old 0x7B): subject + ability fourcc + order, precedes most orders. */
    data class AbilityContext(val subject: UnitRef, val ability: Int, val order: Int) : RawAction {
        override val id: Int get() = 0x7A
    }

    /** Any other known action whose content we do not need. */
    data class Other(override val id: Int) : RawAction
}

data class CommandBlock(val timeMs: Long, val playerId: Int, val actions: List<RawAction>)

data class RawLeave(val timeMs: Long, val reason: Int, val playerId: Int, val result: Int)

data class RawChat(val timeMs: Long, val playerId: Int, val text: String)

data class ParseStats(
    val timeSlots: Int,
    val commandBlocks: Int,
    /** Command blocks decoded exactly up to their boundary. */
    val commandBlocksExact: Int,
    /** Sum of TimeSlot time increments. */
    val totalTimeMs: Long,
)

data class RawReplay(
    val header: ReplayHeader,
    val metadata: GameMetadata,
    val commands: List<CommandBlock>,
    val leaves: List<RawLeave>,
    val chat: List<RawChat>,
    val stats: ParseStats,
    val diagnostics: List<ParseDiagnostic>,
)
