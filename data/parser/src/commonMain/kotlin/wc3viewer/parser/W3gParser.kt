package wc3viewer.parser

import wc3viewer.binary.BinaryFormatException
import wc3viewer.domain.model.ParseDiagnostic
import wc3viewer.domain.model.Replay
import wc3viewer.domain.port.ReplayFormatException
import wc3viewer.domain.port.ReplayFormatException.Kind
import wc3viewer.domain.port.ReplayParser
import wc3viewer.parser.action.ActionDialect
import wc3viewer.parser.action.Patch3Dialect
import wc3viewer.parser.container.BlockReader
import wc3viewer.parser.container.HeaderReader
import wc3viewer.parser.gamedata.GameBlockReader
import wc3viewer.parser.mapping.RawToDomainMapper
import wc3viewer.parser.metadata.MetadataReader
import wc3viewer.parser.raw.RawReplay
import wc3viewer.parser.raw.ReplayHeader

/** .w3g parser (patch 3.0+). Pipeline: ARCHITECTURE §5.1. */
class W3gParser(
    private val dialect: ActionDialect = Patch3Dialect,
) : ReplayParser {

    override suspend fun parse(bytes: ByteArray): Replay = RawToDomainMapper.map(parseRaw(bytes))

    /** Raw structure with parse statistics (used by golden tests). */
    suspend fun parseRaw(bytes: ByteArray): RawReplay = guard {
        val diagnostics = mutableListOf<ParseDiagnostic>()
        val (header, blocksAt) = HeaderReader.read(bytes)
        val data = BlockReader.read(bytes, blocksAt, header, diagnostics)
        parseDecompressed(header, data, diagnostics)
    }

    /** Entry point after inflate: lets tests corrupt the decompressed stream directly. */
    suspend fun parseDecompressed(
        header: ReplayHeader,
        data: ByteArray,
        diagnostics: MutableList<ParseDiagnostic> = mutableListOf(),
    ): RawReplay = guard {
        val meta = MetadataReader.read(data)
        val blocks = GameBlockReader(dialect, diagnostics).read(data, meta.gameDataOffset)
        RawReplay(header, meta, blocks.commands, blocks.leaves, blocks.chat, blocks.stats, diagnostics)
    }

    /** Only [ReplayFormatException] may leave the parser. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: ReplayFormatException) {
        throw e
    } catch (e: BinaryFormatException) {
        throw ReplayFormatException(Kind.CORRUPTED, "The replay is corrupted: ${e.message}", e)
    } catch (e: IndexOutOfBoundsException) {
        throw ReplayFormatException(Kind.CORRUPTED, "The replay is corrupted.", e)
    } catch (e: IllegalArgumentException) {
        throw ReplayFormatException(Kind.CORRUPTED, "The replay is corrupted: ${e.message}", e)
    }
}
