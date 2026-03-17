package ai.slopshield.core

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeRequest(
    val model: String,
    val max_tokens: Int,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeResponse(
    val id: String? = null,
    val content: List<ClaudeContent>? = null,
    val error: ClaudeError? = null
)

@Serializable
private data class ClaudeContent(
    val type: String,
    val text: String
)

@Serializable
private data class ClaudeError(
    val type: String,
    val message: String
)

/**
 * AI execution engine based on the Anthropic Claude REST API.
 *
 * @property httpClient The Ktor client used for REST calls.
 * @property apiKey The Anthropic API key. Defaults to 'slopshield.claude.api_key' system property.
 * @property model The Claude model to use. Defaults to 'slopshield.claude.model' system property or 'claude-haiku-4-5-20251001'.
 * @property baseUrl The base URL for the Claude API. Defaults to 'slopshield.claude.base_url' or 'https://api.anthropic.com'.
 * @property maxTokens The maximum tokens for the AI response. Defaults to 'slopshield.claude.max_tokens' or 4096.
 * @property maxParallelTasks The maximum number of concurrent API calls allowed.
 */
class ClaudeAiService(
    private val httpClient: HttpClient,
    private val apiKey: String = System.getProperty("slopshield.claude.api_key", ""),
    private val model: String = System.getProperty("slopshield.claude.model", "claude-haiku-4-5-20251001"),
    private val baseUrl: String = System.getProperty("slopshield.claude.base_url", "https://api.anthropic.com"),
    private val maxTokens: Int = System.getProperty("slopshield.claude.max_tokens", "4096").toInt(),
    maxParallelTasks: Int = System.getProperty("slopshield.ai.parallelism", "3").toInt()
) : AiService {

    private val formattedBaseUrl = baseUrl.removeSuffix("/")
    private val endpoint = if (formattedBaseUrl.endsWith("/v1/messages")) {
        formattedBaseUrl
    } else {
        "$formattedBaseUrl/v1/messages"
    }
    
    private val executor = Executors.newFixedThreadPool(maxParallelTasks)
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    override suspend fun process(
        prompt: String,
        context: String,
        timeoutSeconds: Long
    ): AiResult = withContext(dispatcher) {
        if (apiKey.isBlank()) {
            return@withContext AiResult("", "Claude API key is missing. Set 'slopshield.claude.api_key' system property.", -1)
        }

        logger.debug { "ClaudeAiService: Sending request to $endpoint [model=$model, promptLen=${prompt.length}, contextLen=${context.length}]" }

        try {
            val result = withTimeoutOrNull(timeoutSeconds * 1000) {
                val response = httpClient.post(endpoint) {
                    timeout {
                        requestTimeoutMillis = timeoutSeconds * 1000
                    }
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    contentType(ContentType.Application.Json)
                    setBody(
                        ClaudeRequest(
                            model = model,
                            max_tokens = maxTokens,
                            system = prompt,
                            messages = listOf(
                                ClaudeMessage(role = "user", content = context)
                            )
                        )
                    )
                }

                if (response.status.isSuccess()) {
                    val claudeResponse = response.body<ClaudeResponse>()
                    val text = claudeResponse.content?.firstOrNull { it.type == "text" }?.text ?: ""
                    AiResult(text, "", 0)
                } else {
                    val rawErrorBody = response.bodyAsText()
                    val errorDetail = try {
                        val errorBody = json.decodeFromString<ClaudeResponse>(rawErrorBody)
                        errorBody.error?.message ?: "No error message in body"
                    } catch (e: Exception) {
                        "Failed to parse error body: ${e.message}. Raw body: $rawErrorBody"
                    }
                    
                    val fullErrorMessage = "Claude API call failed with ${response.status}. Detail: $errorDetail"
                    logger.error { "ClaudeAiService: $fullErrorMessage" }
                    AiResult("", fullErrorMessage, response.status.value)
                }
            }

            result ?: AiResult("", "Claude API request timed out after $timeoutSeconds seconds", -1)
        } catch (e: Exception) {
            logger.error(e) { "ClaudeAiService: Exception during API call" }
            AiResult("", e.message ?: "Unknown error", -1)
        }
    }

    /**
     * Gracefully shuts down the thread pool.
     */
    override fun shutdown() {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }
}
