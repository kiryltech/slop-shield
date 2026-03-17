package ai.slopshield.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeAiServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test successful processing with custom base url`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("http://my-proxy:3000/v1/messages", request.url.toString())
            respond(
                content = """
                    {
                        "id": "msg_123",
                        "content": [
                            {
                                "type": "text",
                                "text": "Response from proxy."
                            }
                        ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout)
        }

        val service = ClaudeAiService(httpClient, apiKey = "test-key", baseUrl = "http://my-proxy:3000/")
        val result = service.process("system prompt", "user context", 5)

        assertEquals(0, result.exitCode)
        assertEquals("Response from proxy.", result.stdout)
        
        service.shutdown()
    }

    @Test
    fun `test error handling`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    {
                        "error": {
                            "type": "invalid_request_error",
                            "message": "Invalid API key"
                        }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout)
        }

        val service = ClaudeAiService(httpClient, apiKey = "bad-key")
        val result = service.process("system prompt", "user context", 5)

        assertEquals(401, result.exitCode)
        assertTrue(result.stderr.contains("Invalid API key"))
        assertTrue(result.stderr.contains("401"))
        
        service.shutdown()
    }

    @Test
    fun `test missing api key`() = runTest {
        val httpClient = HttpClient(MockEngine { respond("") })
        val service = ClaudeAiService(httpClient, apiKey = "")
        val result = service.process("prompt", "context", 5)

        assertEquals(-1, result.exitCode)
        assertEquals("Claude API key is missing. Set 'slopshield.claude.api_key' system property.", result.stderr)
        
        service.shutdown()
    }
}
