package wc3viewer.domain.gamedata

import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Patch
import wc3viewer.domain.model.Race

data class Cost(val gold: Int, val lumber: Int)

sealed interface GameObject {
    val id: ObjectId
    val name: String
    val race: Race?

    data class Unit(
        override val id: ObjectId, override val name: String, override val race: Race?,
        val cost: Cost, val food: Int, val buildTimeSec: Int, val isWorker: Boolean,
    ) : GameObject

    data class Hero(
        override val id: ObjectId, override val name: String, override val race: Race?,
        val abilities: List<ObjectId>,
        val cost: Cost = Cost(0, 0),
        /** Cost of the player's first hero (free on the current patches). */
        val firstHeroCost: Cost = Cost(0, 0),
        val food: Int = 0,
    ) : GameObject

    data class Building(
        override val id: ObjectId, override val name: String, override val race: Race?,
        val cost: Cost, val buildTimeSec: Int, val foodProvided: Int, val tier: Int?,
        /**
         * Price of the upgrade order for buildings made by upgrading another one (Keep, towers).
         * [cost] is cumulative there (Keep = Town Hall + upgrade). Null for regular buildings.
         */
        val upgradeCost: Cost? = null,
        /** The building this one is upgraded from (Town Hall for Keep); null for regular buildings. */
        val upgradesFrom: ObjectId? = null,
    ) : GameObject

    data class Upgrade(
        override val id: ObjectId, override val name: String, override val race: Race?,
        val levels: List<Cost>, val researchTimeSec: List<Int>,
    ) : GameObject

    data class Item(
        override val id: ObjectId, override val name: String, override val race: Race?,
        val cost: Cost,
    ) : GameObject

    data class Ability(
        override val id: ObjectId, override val name: String, override val race: Race?,
        val hero: ObjectId?, val isUltimate: Boolean,
    ) : GameObject
}

interface GameData {
    val patch: Patch
    fun find(id: ObjectId): GameObject?
}

interface GameDataRepository {
    /** Closest data set with patch ≤ [patch], otherwise the newest one. */
    fun forPatch(patch: Patch): GameData
}
