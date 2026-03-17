package ai.slopshield.strategist

import ai.slopshield.core.AiInvolvement
import ai.slopshield.core.AiService
import ai.slopshield.core.HarvestComplete
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import ai.slopshield.core.StoryCategorized
import ai.slopshield.core.StoryCategory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * The expected JSON format returned from the AI categorization prompt.
 *
 * @property category The determined string category (e.g., "WRITING", "PRODUCT").
 * @property reasoning A brief explanation of why this category was chosen.
 * @property aiInvolvement The estimated level of AI involvement in the content.
 */
@Serializable
data class CategorizationResult(
    val category: String,
    val reasoning: String,
    val aiInvolvement: String
)

/**
 * The Categorizer domain service.
 * Listens for [HarvestComplete] events and uses the [AiService] to classify
 * stories into high-level categories (WRITING, PRODUCT, DEMO, etc.).
 *
 * @property collector The flow collector for emitting [StoryCategorized] events.
 * @property aiService The AI execution engine wrapper for running the categorization prompt.
 */
@SlopListener
class Categorizer(
    private val collector: FlowCollector<SlopEvent>,
    private val aiService: AiService
) : SlopHandler<HarvestComplete> {

    private val prompt = """
        Analyze the following web page content and categorize it into one of these categories:
        - WRITING: Blog posts, articles, essays, news stories.
        - PRODUCT: SaaS landing pages, commercial products, tools for sale, marketing materials.
        - DEMO: Technical demos, interactive experiments, "Show HN" style prototypes.
        - SOURCE: Source code repositories (e.g., GitHub, GitLab), library documentation, or code snippets.
        - VIDEO: Video content platforms like YouTube, Vimeo, or technical talks.
        - UNKNOWN: Anything that doesn't fit the above.

        Also analyze the level of AI involvement in the content generation. Choose from:
        - HAND_CRAFTED: Deeply personal, unique voice, highly specific technical details.
        - ASSISTED: Mostly human-written but likely used AI for outlines or polishing.
        - COLLABORATIVE: A mix of human insight and heavy AI structure.
        - HIGH_AI: Primarily AI-generated with minimal human oversight.
        - PURE_SLOP: Unedited, generic AI output designed for SEO/volume.
        - UNKNOWN: Default or undetermined level of involvement.

        CRITICAL: Output ONLY a raw JSON object. Do not wrap it in markdown code blocks.
        Format: {"category": "CATEGORY_NAME", "reasoning": "brief explanation", "aiInvolvement": "HAND_CRAFTED"}
    """.trimIndent()

    /**
     * Determines whether this handler can process the given event.
     * Only processes successfully harvested stories.
     *
     * @param event The [HarvestComplete] event.
     * @return True if the event should be processed.
     */
    override fun canHandle(event: HarvestComplete): Boolean {
        return event.success
    }

    /**
     * Triggers the categorization logic.
     *
     * @param event The successfully harvested story data.
     */
    override suspend fun onEvent(event: HarvestComplete) {
        categorize(event)
    }

    /**
     * Categorizes the scraped content using an AI prompt and emits a [StoryCategorized] event.
     *
     * @param event The [HarvestComplete] event containing the cleaned text.
     */
    private suspend fun categorize(event: HarvestComplete) {
        logger.info { "Categorizer: Analyzing story ${event.id}..." }
        
        val result = aiService.process(prompt, event.cleanText)
        if (result.exitCode == 0) {
            val jsonContent = sanitizeJson(result.stdout)
            val parsed = Json.decodeFromString<CategorizationResult>(jsonContent)
            val category = try {
                StoryCategory.valueOf(parsed.category.uppercase())
            } catch (e: Exception) {
                logger.warn { "Categorizer: Failed to parse category '${parsed.category}' for story ${event.id}. Defaulting to UNKNOWN." }
                StoryCategory.UNKNOWN
            }
            val involvement = try {
                AiInvolvement.valueOf(parsed.aiInvolvement.uppercase())
            } catch (e: Exception) {
                logger.warn { "Categorizer: Failed to parse AI involvement '${parsed.aiInvolvement}' for story ${event.id}. Defaulting to UNKNOWN." }
                AiInvolvement.UNKNOWN
            }

            logger.info { "Categorizer: Story ${event.id} categorized as $category with involvement $involvement. Reasoning: ${parsed.reasoning}" }
            
            collector.emit(
                StoryCategorized(
                    id = event.id,
                    category = category,
                    reasoning = parsed.reasoning,
                    aiInvolvement = involvement
                )
            )
        } else {
            logger.warn { "Categorizer: AI process failed for story ${event.id} with exit code ${result.exitCode}" }
            throw IllegalStateException("AI categorization failed with exit code ${result.exitCode}")
        }
    }

    /**
     * Sanitizes AI JSON output by removing markdown code blocks and leaving only the JSON portion.
     *
     * @param aiResponse The raw output from the AI.
     * @return The cleaned JSON string.
     */
    private fun sanitizeJson(aiResponse: String): String {
        val jsonRegex = """(?s)\{.*\}""".toRegex()
        return jsonRegex.find(aiResponse)?.value ?: aiResponse
    }
}
