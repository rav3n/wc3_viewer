package wc3viewer.gamedata

import wc3viewer.domain.gamedata.GameData
import wc3viewer.domain.gamedata.GameDataRepository
import wc3viewer.domain.gamedata.GameObject
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Patch
import wc3viewer.gamedata.generated.GameData_3_0

/** In-memory game data for one patch, filled by codegen from JSON (ARCHITECTURE §6.3). */
class StaticGameData(
    override val patch: Patch,
    objects: List<GameObject>,
) : GameData {
    private val byId: Map<ObjectId, GameObject> = objects.associateBy { it.id }

    val size: Int get() = byId.size

    override fun find(id: ObjectId): GameObject? = byId[id]
}

/**
 * Picks the closest data set with patch ≤ the replay patch, otherwise the newest one.
 * With no data sets returns an empty set for the requested patch.
 */
class StaticGameDataRepository(sets: List<GameData>) : GameDataRepository {
    private val sorted = sets.sortedBy { it.patch }

    override fun forPatch(patch: Patch): GameData =
        sorted.lastOrNull { it.patch <= patch } ?: sorted.lastOrNull() ?: StaticGameData(patch, emptyList())
}

/**
 * Production game data generated from `gamedata/<patch>/` JSON files (Liquipedia, CC BY-SA 3.0; attribution in
 * [GameData_3_0.ATTRIBUTION]). Injected by the composition root; built on first use.
 */
object ProductionGameData {
    val repository: GameDataRepository by lazy {
        StaticGameDataRepository(listOf(StaticGameData(GameData_3_0.PATCH, GameData_3_0.objects())))
    }
}
