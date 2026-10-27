package wc3viewer.gamedata

import wc3viewer.domain.gamedata.GameObject
import wc3viewer.domain.model.ObjectId
import wc3viewer.domain.model.Patch
import wc3viewer.gamedata.fixture.TestFixtureGameData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StaticGameDataRepositoryTest {
    private val a = StaticGameData(Patch(2, 0, 0), emptyList())
    private val b = StaticGameData(Patch(3, 0, 0), emptyList())
    private val repo = StaticGameDataRepository(listOf(b, a))

    @Test
    fun picksClosestNotNewer() {
        assertEquals(b.patch, repo.forPatch(Patch(3, 1, 7100)).patch)
        assertEquals(a.patch, repo.forPatch(Patch(2, 5, 0)).patch)
    }

    @Test
    fun fallsBackToNewestForOlderPatch() {
        assertEquals(b.patch, repo.forPatch(Patch(1, 30, 0)).patch)
    }

    @Test
    fun emptyRepositoryReturnsEmptyData() {
        val d = StaticGameDataRepository(emptyList()).forPatch(Patch(3, 0, 7003))
        assertNull(d.find(ObjectId.of("hpea")))
    }

    @Test
    fun fixtureFindsObjects() {
        assertIs<GameObject.Unit>(TestFixtureGameData.data.find(ObjectId.of("hpea")))
        assertNull(TestFixtureGameData.data.find(ObjectId.of("zzzz")))
    }
}
