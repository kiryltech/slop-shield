package ai.slopshield.core

interface AiService {
    /**
     * Processes the given prompt and context content using an AI model.
     *
     * @param prompt The prompt to guide the AI model's generation.
     * @param context The supporting content or data for the AI model to process.
     * @param timeoutSeconds How long to wait for the AI processing to complete.
     * @return The result of the AI processing containing stdout, stderr, and exit code.
     */
    suspend fun process(
        prompt: String,
        context: String,
        timeoutSeconds: Long = 120
    ): AiResult

    /**
     * Gracefully shuts down the AI service and releases any resources.
     */
    fun shutdown()
}