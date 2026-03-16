package ai.slopshield.core

interface AiService {
    /**
     * Executes a Gemini CLI command with the given prompt and input content.
     *
     * @param prompt The prompt to pass as the first argument to gemini.
     * @param input The content to pipe into gemini's stdin.
     * @param timeoutSeconds How long to wait for the process to complete.
     * @return The result of the AI processing containing stdout, stderr, and exit code.
     */
    suspend fun process(
        prompt: String,
        input: String,
        timeoutSeconds: Long = 120
    ): AiResult
}