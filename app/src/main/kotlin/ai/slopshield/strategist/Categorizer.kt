package ai.slopshield.strategist

import ai.slopshield.core.AIService
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopEmitter
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import ai.slopshield.core.StoryCategorized
import ai.slopshield.core.StoryCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

@Serializable
data class CategorizationResult(
    val category: String,
    val reasoning: String
)

/**
 * The Categorizer domain service.
 * Listens for [HarvestComplete] events and uses the [AIService] to classify
 * stories into high-level categories (WRITING, PRODUCT, DEMO, etc.).
 */
@SlopListener
class Categorizer(
    private val emit: SlopEmitter,
    private val aiService: AIService
) : SlopHandler<HarvestComplete> {

    private val prompt = """
        Analyze the following web page content and categorize it into one of these categories:
        - WRITING: Blog posts, articles, essays, news stories.
        - PRODUCT: SaaS landing pages, commercial products, tools for sale, marketing materials.
        - DEMO: Technical demos, interactive experiments, "Show HN" style prototypes.
        - SOURCE: Source code repositories (e.g., GitHub, GitLab), library documentation, or code snippets.
        - UNKNOWN: Anything that doesn't fit the above.

        CRITICAL: Output ONLY a raw JSON object. Do not wrap it in markdown code blocks.
        Format: {"category": "CATEGORY_NAME", "reasoning": "brief explanation"}
    """.trimIndent()

    override fun canHandle(event: HarvestComplete): Boolean {
        return event.exitCode == 0
    }

    override suspend fun onEvent(event: HarvestComplete) {
        categorize(event)
    }

    private suspend fun categorize(event: HarvestComplete) {
        logger.info { "Categorizer: Analyzing story ${event.storyId}..." }
        
        try {
            val result = aiService.process(prompt, event.cleanText)
            if (result.exitCode == 0) {
                val jsonContent = sanitizeJson(result.stdout)
                val parsed = Json.decodeFromString<CategorizationResult>(jsonContent)
                val category = try {
                    StoryCategory.valueOf(parsed.category.uppercase())
                } catch (e: Exception) {
                    StoryCategory.UNKNOWN
                }

                logger.info { "Categorizer: Story ${event.storyId} categorized as $category. Reasoning: ${parsed.reasoning}" }
                
                emit(
                    StoryCategorized(
                        storyId = event.storyId,
                        category = category,
                        reasoning = parsed.reasoning
                    )
                )
            } else {
                logger.warn { "Categorizer: AI process failed for story ${event.storyId} with exit code ${result.exitCode}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Categorizer: Error categorizing story ${event.storyId}" }
        }
    }

    private fun sanitizeJson(input: String): String {
        return input.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
