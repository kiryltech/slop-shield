package ai.slopshield.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A mock implementation of [AIService] to avoid running real external processes during tests.
 *
 * @property mockResult The predefined [AIResult] to return upon any process call.
 */
class MockAIService(val mockResult: AIResult) : AIService() {
    override suspend fun process(prompt: String, input: String, timeoutSeconds: Long): AIResult {
        return mockResult
    }
}

/**
 * Tests for the [AIService] wrapper.
 */
class AIServiceTest {

    /**
     * Verifies that the service can read configuration from system properties without crashing.
     */
    @Test
    fun `test service configuration from system properties`() {
        System.setProperty("slopshield.ai.parallelism", "5")
        val service = AIService()
        // No public getter for parallelism, but we can verify it doesn't crash
        service.shutdown()
    }

    /**
     * Verifies the structural correctness of the data container.
     * Note: Testing real process execution of 'gemini' CLI is environment-dependent.
     * We would typically mock the ProcessBuilder if we wanted to test the deadlock logic itself,
     * but since we are using AIService as a wrapper, we test its structural behavior.
     */
    @Test
    fun `test result data structure`() {
        val result = AIResult("out", "err", 0)
        assertEquals("out", result.stdout)
        assertEquals("err", result.stderr)
        assertEquals(0, result.exitCode)
    }
}
