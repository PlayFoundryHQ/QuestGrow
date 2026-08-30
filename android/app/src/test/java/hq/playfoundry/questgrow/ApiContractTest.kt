package hq.playfoundry.questgrow

import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.net.ApiClientFactory
import hq.playfoundry.questgrow.data.net.LoginBody
import hq.playfoundry.questgrow.data.net.QuestGrowApi
import hq.playfoundry.questgrow.data.net.TokenProvider
import hq.playfoundry.questgrow.data.net.apiCall
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the Android client's assumptions against representative server
 * responses (grant §13): versioned paths, the bearer header, and the
 * status→`code` mapping in [apiCall].
 */
class ApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: QuestGrowApi
    private var token: String? = "tok-123"

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ApiClientFactory.create(server.url("/").toString(), TokenProvider { token })
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `calls hit the v1 surface and carry the bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        apiCall { api.listChildren() }
        val req = server.takeRequest()
        assertEquals("/v1/children", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
    }

    @Test fun `auth calls do not require a token but still work when one is set`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session_token":"s1"}"""))
        val r = apiCall { api.login(LoginBody("a@x.com", "pw")) }
        assertTrue(r is ApiResult.Ok)
        assertEquals("/v1/auth/login", server.takeRequest().path)
    }

    @Test fun `4xx maps to Failure with the server code`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"detail":"invalid or expired token","code":"not_authenticated"}"""),
        )
        val r = apiCall { api.listChildren() }
        assertTrue(r is ApiResult.Failure)
        r as ApiResult.Failure
        assertEquals(401, r.status)
        assertEquals("not_authenticated", r.code)
        assertTrue(r.isAuthExpired)
    }

    @Test fun `409 is a Failure, not a crash — client can branch on it`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"detail":"instance is verified, cannot submit","code":"contract_violation"}"""),
        )
        val r = apiCall { api.complete("teeth", hq.playfoundry.questgrow.data.net.NotYetBody("2026-08-03")) }
        assertTrue((r as ApiResult.Failure).isConflict)
        assertEquals("contract_violation", r.code)
    }

    @Test fun `no response is Offline, distinct from Failure`() = runTest {
        server.shutdown() // nothing listening
        val r = apiCall { api.listChildren() }
        assertTrue(r is ApiResult.Offline)
    }

    @Test fun `missing token means no Authorization header`() = runTest {
        token = null
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        apiCall { api.listChildren() }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
