package wc3viewer.gamedata

import wc3viewer.domain.gamedata.Cost
import wc3viewer.domain.gamedata.GameObject
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Patch
import wc3viewer.domain.model.Race
import wc3viewer.gamedata.generated.GameData_3_0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Generated data from gamedata/3.0 (codegen, ARCHITECTURE §6.3). */
class ProductionGameDataTest {
    private val data = ProductionGameData.repository.forPatch(Patch(3, 0, 7003))
    private fun find(code: String) = data.find(ObjectId.of(code))

    @Test
    fun namesOfReplayIds() {
        assertEquals("Archmage", find("Hamg")?.name)
        assertEquals("Blizzard", find("AHbz")?.name)
        assertEquals("Peasant", find("hpea")?.name)
        assertEquals("Keep", find("hkee")?.name)
        assertNull(find("zzzz"))
    }

    @Test
    fun abilitiesKnowTheirHero() {
        val a = assertIs<GameObject.Ability>(find("AHbz"))
        assertEquals(ObjectId.of("Hamg"), a.hero)
        assertEquals(Race.HUMAN, a.race)
        // Not in the Liquipedia JSON: supplemented from w3gjs mappings.ts.
        val alias = assertIs<GameObject.Ability>(find("ANc1"))
        assertEquals("Cluster Rockets", alias.name)
        assertEquals(ObjectId.of("Ntin"), alias.hero)
    }

    @Test
    fun pricesForTheEconomyEstimate() {
        val keep = assertIs<GameObject.Building>(find("hkee"))
        assertEquals(Cost(320, 210), keep.upgradeCost)
        assertEquals(ObjectId.of("htow"), keep.upgradesFrom)
        val hero = assertIs<GameObject.Hero>(find("Hamg"))
        assertEquals(Cost(400, 100), hero.cost)
        assertEquals(Cost(0, 0), hero.firstHeroCost)
        assertEquals(5, hero.food)
        assertEquals(3, assertIs<GameObject.Upgrade>(find("Rhme")).levels.size)
    }

    @Test
    fun attributionIsKept() {
        assertTrue(GameData_3_0.LICENSE.startsWith("CC BY-SA"))
        assertTrue(GameData_3_0.SOURCE.contains("liquipedia.net"))
        assertTrue(GameData_3_0.ABILITY_NAMES_SUPPLEMENT.contains("MIT"))
        assertEquals(GameData_3_0.OBJECT_COUNT, (data as StaticGameData).size)
    }
}
