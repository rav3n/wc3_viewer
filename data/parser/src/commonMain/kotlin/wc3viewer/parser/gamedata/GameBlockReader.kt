package wc3viewer.parser.gamedata

import kotlinx.coroutines.yield
import wc3viewer.binary.BinaryFormatException
import wc3viewer.binary.ByteReader
import wc3viewer.domain.model.GameTime
import wc3viewer.domain.model.ParseDiagnostic
import wc3viewer.parser.action.ActionDialect
import wc3viewer.parser.raw.CommandBlock
import wc3viewer.parser.raw.ParseStats
import wc3viewer.parser.raw.RawAction
import wc3viewer.parser.raw.RawChat
import wc3viewer.parser.raw.RawLeave

internal class GameBlocks(
    val commands: List<CommandBlock>,
    val leaves: List<RawLeave>,
    val chat: List<RawChat>,
    val stats: ParseStats,
)

/** Game block stream after metadata (W3G_FORMAT §6). */
internal class GameBlockReader(
    private val dialect: ActionDialect,
    private val diagnostics: MutableList<ParseDiagnostic>,
) {
    private val commands = ArrayList<CommandBlock>()
    private val leaves = ArrayList<RawLeave>()
    private val chat = ArrayList<RawChat>()
    private var time = 0L
    private var timeSlots = 0
    private var commandBlocks = 0
    private var exact = 0

    suspend fun read(data: ByteArray, offset: Int): GameBlocks {
        val r = ByteReader(data, offset)
        loop@ while (r.hasRemaining) {
            val at = r.pos
            val id = r.u8()
            try {
                when (id) {
                    0x00 -> break@loop // padding: end of data
                    0x17 -> {
                        val reason = r.i32(); val pid = r.u8(); val result = r.i32(); r.skip(4)
                        leaves += RawLeave(time, reason, pid, result)
                    }
                    0x1A, 0x1B, 0x1C -> r.skip(4)
                    0x1E, 0x1F -> {
                        timeSlot(r.sub(r.u16()))
                        // Keep the UI responsive on single-threaded JS.
                        if (++timeSlots % 2000 == 0) yield()
                    }
                    0x20 -> chat(readChat(r))
                    0x22 -> r.skip(r.u8())
                    0x23 -> r.skip(10)
                    0x2F -> r.skip(8)
                    else -> {
                        diagnostics += ParseDiagnostic(GameTime(time), at, "Unknown game block 0x${hex(id)}; the rest of the replay is skipped")
                        break@loop
                    }
                }
            } catch (e: BinaryFormatException) {
                diagnostics += ParseDiagnostic(GameTime(time), at, "Game data ends inside block 0x${hex(id)}: ${e.message}")
                break@loop
            }
        }
        return GameBlocks(commands, leaves, chat, ParseStats(timeSlots, commandBlocks, exact, time))
    }

    private fun chat(c: RawChat?) {
        if (c != null) chat += c
    }

    private fun readChat(r: ByteReader): RawChat? {
        val pid = r.u8()
        val s = r.sub(r.u16())
        val flags = s.u8()
        if (flags == 0x20) s.skip(4)
        val text = runCatching { s.cstr() }.getOrNull() ?: return null
        return RawChat(time, pid, text)
    }

    private fun timeSlot(r: ByteReader) {
        time += r.u16()
        while (r.hasRemaining) {
            val pid = r.u8()
            val block = r.sub(r.u16())
            commandBlocks++
            val actions = ArrayList<RawAction>(4)
            var clean = true
            while (block.hasRemaining) {
                val at = block.pos
                val id = block.u8()
                val action = try {
                    dialect.read(id, block)
                } catch (e: BinaryFormatException) {
                    diagnostics += ParseDiagnostic(GameTime(time), at, "Action 0x${hex(id)} of player $pid overruns its command block")
                    clean = false
                    break
                }
                if (action == null) {
                    diagnostics += ParseDiagnostic(GameTime(time), at, "Unknown action 0x${hex(id)} of player $pid; rest of the command block skipped")
                    clean = false
                    break
                }
                actions += action
            }
            if (clean) exact++
            commands += CommandBlock(time, pid, actions)
        }
    }

    private fun hex(v: Int) = v.toString(16).padStart(2, '0')
}
