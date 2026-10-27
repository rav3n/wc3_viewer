package wc3viewer.parser.metadata

import wc3viewer.binary.BinaryFormatException
import wc3viewer.binary.ByteReader
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.domain.port.ReplayFormatException.Kind
import wc3viewer.parser.raw.GameMetadata
import wc3viewer.parser.raw.PlayerRecord
import wc3viewer.parser.raw.SlotRecord

/** Game metadata at the start of the decompressed buffer (W3G_FORMAT §5). */
internal object MetadataReader {
    fun read(data: ByteArray): GameMetadata = try {
        readUnsafe(ByteReader(data))
    } catch (e: BinaryFormatException) {
        throw ReplayFormatException(Kind.CORRUPTED, "Replay metadata is corrupted: ${e.message}", e)
    }

    private fun readUnsafe(r: ByteReader): GameMetadata {
        r.skip(4) // unknown, 0x00000110
        val host = readPlayer(r)
        val gameName = r.cstr()
        r.cstr() // private string
        val settings = decodeSettings(r.cstrBytes())
        val sr = ByteReader(settings)
        var mapPath = ""
        var creator = ""
        if (settings.size > 13) {
            sr.skip(13)
            mapPath = sr.cstr()
            creator = if (sr.hasRemaining) sr.cstr() else ""
        }
        r.skip(4) // playerCount (slot count, not players)
        r.skip(4) // gameType
        r.skip(4) // localeHash
        val players = mutableListOf(host)
        while (r.peekU8() == 0x16) {
            players += readPlayer(r)
            r.skip(4)
        }
        var chunks = 0
        while (r.peekU8() == 0x38 || r.peekU8() == 0x39) {
            r.skip(2) // tag, subtype
            r.skip(r.u32AsSize()) // protobuf payload, not needed for MVP
            chunks++
        }
        val tag = r.u8()
        if (tag != 0x19) throw BinaryFormatException(r.pos - 1, "expected GameStartRecord 0x19, got 0x${tag.toString(16)}")
        val bytesRemaining = r.u16()
        val sub = r.sub(bytesRemaining)
        val slotCount = sub.u8()
        val slots = List(slotCount) { i ->
            val playerId = sub.u8()
            sub.skip(1) // download %
            val status = sub.u8()
            val computer = sub.u8()
            val team = sub.u8()
            val color = sub.u8()
            val race = sub.u8()
            sub.skip(1) // AI strength
            val handicap = sub.u8()
            SlotRecord(i, playerId, status, computer, team, color, race, handicap)
        }
        // randomSeed u32, selectMode u8, startSpotCount u8 — not needed.
        return GameMetadata(host, gameName, mapPath, creator, players, slots, chunks, r.pos)
    }

    private fun readPlayer(r: ByteReader): PlayerRecord {
        r.skip(1) // record id: 0x00 host, 0x16 others
        val id = r.u8()
        val name = r.cstr()
        r.skip(r.u8())
        return PlayerRecord(id, name)
    }

    /** w3gjs decodeGameMetaString (W3G_FORMAT §5.2). */
    fun decodeSettings(enc: ByteArray): ByteArray {
        val out = ByteArray(enc.size)
        var n = 0
        var mask = 0
        for (i in enc.indices) {
            val c = enc[i].toInt() and 0xFF
            if (i % 8 == 0) {
                mask = c
            } else {
                out[n++] = (if (mask and (1 shl (i % 8)) == 0) c - 1 else c).toByte()
            }
        }
        return out.copyOf(n)
    }
}
