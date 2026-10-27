package wc3viewer.parser

import java.io.File

/** Paths to test replays and golden files (TEST_STRATEGY §2). */
object Fixtures {
    val root = File(System.getProperty("wc3.root") ?: error("wc3.root system property is not set"))
    val replays: List<File> = File(root, "testdata/replays/3.00").listFiles { f -> f.extension == "w3g" }.orEmpty().sortedBy { it.name }
    val broken: List<File> = File(root, "testdata/replays/broken").listFiles { f -> f.extension == "w3g" }.orEmpty().sortedBy { it.name }
    fun expected(replay: File) = File(root, "testdata/expected/${replay.name}.json")
}
