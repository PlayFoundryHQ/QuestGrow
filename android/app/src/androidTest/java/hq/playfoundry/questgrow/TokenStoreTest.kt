package hq.playfoundry.questgrow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hq.playfoundry.questgrow.data.local.DataStoreTokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenStoreTest {
    private lateinit var ts: DataStoreTokenStore

    @Before fun setUp() = runBlocking {
        ts = DataStoreTokenStore(ApplicationProvider.getApplicationContext())
        ts.clearAll()
    }

    @Test fun multiChild_addSwitchRemove() = runBlocking {
        ts.putChildToken("a", "نیکا", "tok_a")
        assertEquals("tok_a", ts.childTokenBlocking())
        assertEquals("a", ts.activeChildIdBlocking())
        assertEquals(listOf("a" to "نیکا"), ts.deviceChildrenBlocking())

        ts.putChildToken("b", "آرش", "tok_b")
        assertEquals(setOf("a", "b"), ts.deviceChildrenBlocking().map { it.first }.toSet())
        assertEquals("tok_b", ts.childTokenBlocking())   // newest is active

        ts.setActiveChild("a")
        assertEquals("tok_a", ts.childTokenBlocking())

        ts.removeChildToken("a")
        assertEquals(listOf("b" to "آرش"), ts.deviceChildrenBlocking())
        assertEquals("tok_b", ts.childTokenBlocking())
    }

    @Test fun pairedDevice_singleTokenPath_stillWorks() = runBlocking {
        ts.setChildToken("c_paired")
        assertEquals("c_paired", ts.childTokenBlocking())
        assertNull(ts.activeChildIdBlocking())
    }

    @Test fun fresh_instance_readsPersistedMap() = runBlocking {
        ts.putChildToken("a", "نیکا", "tok_a")
        ts.putChildToken("b", "آرش", "tok_b")
        val fresh = DataStoreTokenStore(ApplicationProvider.getApplicationContext())
        assertEquals(setOf("a", "b"), fresh.deviceChildrenBlocking().map { it.first }.toSet())
        assertEquals("tok_b", fresh.childTokenBlocking())
    }
}
