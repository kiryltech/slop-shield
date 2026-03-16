package ai.slopshield.core

/**
 * A mock implementation of [AiService] to avoid running real external processes during tests.
 *
 * @property mockResult The predefined [AiResult] to return upon any process call.
 */
class MockAiService(val mockResult: AiResult) : AiService {
    override suspend fun process(prompt: String, input: String, timeoutSeconds: Long): AiResult {
        return mockResult
    }
}