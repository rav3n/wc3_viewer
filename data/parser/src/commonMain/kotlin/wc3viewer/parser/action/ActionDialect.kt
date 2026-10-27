package wc3viewer.parser.action

import wc3viewer.binary.ByteReader
import wc3viewer.domain.model.Target
import wc3viewer.domain.model.UnitRef
import wc3viewer.parser.raw.RawAction

/** Table `action id → codec`. Differences between patches live here (ARCHITECTURE §5.2). */
interface ActionDialect {
    /** Reads one action payload. null — unknown ID: the rest of the CommandBlock is skipped. */
    fun read(id: Int, r: ByteReader): RawAction?
}

/**
 * Patch 3.0 numbering (W3G_FORMAT §7.1, §7.3): IDs from 0x77 on are shifted down by one
 * compared to the classic table (0x77 = old 0x78, 0x7A = old 0x7B).
 * Payload lengths are verified on all sample replays.
 */
object Patch3Dialect : ActionDialect {
    /** Fixed payload sizes of actions we do not decode. */
    private val FIXED: Map<Int, Int> = mapOf(
        0x01 to 0, 0x02 to 0, 0x03 to 1, 0x04 to 0, 0x05 to 0, 0x07 to 4,
        0x1A to 0, 0x1B to 9, 0x1C to 9, 0x1D to 8,
        0x50 to 5, 0x51 to 9, 0x61 to 0, 0x62 to 12, 0x63 to 8, 0x64 to 8, 0x65 to 8,
        0x66 to 0, 0x67 to 0, 0x68 to 12, 0x69 to 16, 0x6A to 16, 0x75 to 1, 0x76 to 10,
        0x7B to 16,
    )

    override fun read(id: Int, r: ByteReader): RawAction? = when (id) {
        0x10 -> order(id, r, withPoint = false)
        0x11 -> order(id, r, withPoint = true)
        0x12 -> {
            val flags = r.u16(); val order = r.i32(); r.skip(8)
            val x = r.f32(); val y = r.f32()
            RawAction.Order(id, flags, order, Target.Unit(x, y, tag(r)), null)
        }
        0x13 -> {
            val flags = r.u16(); val order = r.i32(); r.skip(8)
            val x = r.f32(); val y = r.f32()
            val unit = tag(r); val item = tag(r)
            RawAction.Order(id, flags, order, Target.Unit(x, y, unit), item)
        }
        0x14, 0x15 -> {
            val flags = r.u16(); val order = r.i32(); r.skip(8)
            val x = r.f32(); val y = r.f32()
            r.skip(4 + 4 + 4 + 1 + 8) // order2, flags, category, owner, point2
            if (id == 0x15) r.skip(8)
            RawAction.Order(id, flags, order, Target.Point(x, y), null)
        }
        0x16 -> RawAction.ChangeSelection(r.u8(), tags(r))
        0x17 -> RawAction.AssignGroup(r.u8(), tags(r))
        0x18 -> RawAction.SelectGroup(r.u8()).also { r.skip(1) }
        0x19 -> RawAction.SelectSubgroup(r.i32(), tag(r))
        0x1E, 0x1F -> RawAction.RemoveFromQueue(id, r.u8(), r.i32())
        0x06 -> { r.cstr(); RawAction.Other(id) } // save game: file name
        0x60 -> { r.skip(8); r.cstr(); RawAction.Other(id) } // map trigger chat
        0x77 -> { r.cstr(); r.cstr(); r.skip(4); RawAction.Other(id) } // sync data (old 0x78)
        0x7A -> RawAction.AbilityContext(tag(r), r.i32(), r.i32())
        else -> FIXED[id]?.let { r.skip(it); RawAction.Other(id) }
    }

    private fun order(id: Int, r: ByteReader, withPoint: Boolean): RawAction.Order {
        val flags = r.u16()
        val order = r.i32()
        r.skip(8) // AgentTag
        val target = if (withPoint) Target.Point(r.f32(), r.f32()) else Target.None
        return RawAction.Order(id, flags, order, target, null)
    }

    private fun tag(r: ByteReader) = UnitRef(r.i32(), r.i32())

    private fun tags(r: ByteReader): List<UnitRef> {
        val n = r.u16()
        if (n * 8 > r.remaining) throw wc3viewer.binary.BinaryFormatException(r.pos, "unit list of $n does not fit")
        return List(n) { tag(r) }
    }
}
