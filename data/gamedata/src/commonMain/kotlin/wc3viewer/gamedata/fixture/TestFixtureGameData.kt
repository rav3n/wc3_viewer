package wc3viewer.gamedata.fixture

import wc3viewer.domain.gamedata.Cost
import wc3viewer.domain.gamedata.GameObject
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Patch
import wc3viewer.domain.model.Race
import wc3viewer.gamedata.StaticGameData
import wc3viewer.gamedata.StaticGameDataRepository

/**
 * TEST FIXTURE ONLY. Not real balance data: costs, food and times are synthetic placeholders
 * chosen for test arithmetic. Never use in production code.
 */
object TestFixtureGameData {
    val PATCH_3_00 = Patch(3, 0, 0)

    val data = StaticGameData(
        PATCH_3_00,
        listOf(
            GameObject.Unit(ObjectId.of("hpea"), "Peasant", Race.HUMAN, Cost(10, 0), food = 1, buildTimeSec = 10, isWorker = true),
            GameObject.Unit(ObjectId.of("hfoo"), "Footman", Race.HUMAN, Cost(20, 0), food = 2, buildTimeSec = 20, isWorker = false),
            GameObject.Unit(ObjectId.of("opeo"), "Peon", Race.ORC, Cost(10, 0), food = 1, buildTimeSec = 10, isWorker = true),
            GameObject.Hero(ObjectId.of("Hamg"), "Archmage", Race.HUMAN, listOf(ObjectId.of("AHbz")), Cost(400, 100), Cost(0, 0), food = 5),
            GameObject.Hero(ObjectId.of("Hmkg"), "Mountain King", Race.HUMAN, emptyList(), Cost(400, 100), Cost(0, 0), food = 5),
            GameObject.Ability(ObjectId.of("AHbz"), "Blizzard", Race.HUMAN, hero = ObjectId.of("Hamg"), isUltimate = false),
            GameObject.Building(ObjectId.of("hhou"), "Farm", Race.HUMAN, Cost(30, 10), buildTimeSec = 30, foodProvided = 6, tier = null),
            GameObject.Building(ObjectId.of("htow"), "Town Hall", Race.HUMAN, Cost(300, 200), buildTimeSec = 100, foodProvided = 12, tier = 1),
            GameObject.Building(
                ObjectId.of("hkee"), "Keep", Race.HUMAN, Cost(600, 400), buildTimeSec = 100, foodProvided = 14, tier = 2,
                upgradeCost = Cost(300, 200), upgradesFrom = ObjectId.of("htow"),
            ),
            GameObject.Upgrade(ObjectId.of("Rhme"), "Melee Weapons", Race.HUMAN, listOf(Cost(50, 50)), listOf(40)),
            GameObject.Item(ObjectId.of("phea"), "Potion of Healing", null, Cost(100, 0)),
        ),
    )

    val repository = StaticGameDataRepository(listOf(data))
}
