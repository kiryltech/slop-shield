package ai.slopshield.strategist

import ai.slopshield.core.AiService
import ai.slopshield.core.Alignment
import ai.slopshield.core.AnalysisComplete
import ai.slopshield.core.ContextRequest
import ai.slopshield.core.ContextResponse
import ai.slopshield.core.HypeRisk
import ai.slopshield.core.ReasoningBullet
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryCategorized
import ai.slopshield.core.StoryCategory
import ai.slopshield.core.StoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * The structured result of a deep AI analysis.
 *
 * @property mms Mental Model Shift score (0-10).
 * @property sa Strategic Actionability score (0-10).
 * @property sd Signal Density score (0-10).
 * @property d Durability score (0-10).
 * @property alignment The story's alignment with the personal context.
 * @property hypeRisk The calculated hype risk level.
 * @property sparringNote A cynical/pragmatic note on why the story is relevant.
 * @property reasoningBullets The detailed breakdown of why the scores were given.
 */
@Serializable
data class DeepAnalysisResult(
    val mms: Int,
    val sa: Int,
    val sd: Int,
    val d: Int,
    val alignment: String,
    val hypeRisk: String,
    val sparringNote: String,
    val reasoningBullets: List<ReasoningBullet> = emptyList()
)

/**
 * The Strategist (The Curator).
 * Performs deep SECV analysis on stories that have been categorized as high-signal.
 * It uses the personal context from MemoryService to calibrate its judgment.
 *
 * @property scope The coroutine scope to launch background analysis tasks.
 * @property eventStream The shared domain event stream to collect responses and triggers.
 * @property collector The flow collector to emit analysis completion events.
 * @property aiService The AI execution service for running the deep analysis prompts.
 * @property repository The repository to look up story content.
 */
@SlopService
class Strategist(
    private val scope: CoroutineScope,
    private val eventStream: SharedFlow<SlopEvent>,
    private val collector: FlowCollector<SlopEvent>,
    private val aiService: AiService,
    private val repository: StoryRepository
) : SlopServiceLifecycle {

    private var currentContext: String = ""
    private var analysisJob: kotlinx.coroutines.Job? = null

    /**
     * Generates the system instructions for the AI curator.
     */
    private fun generateInstructions(): String = """
        You are "The Curator", a highly opinionated and experienced Software Engineer.
        Your goal is to evaluate the technical story provided in the input through the lens of my PERSONAL CONTEXT (also provided in the input).

        Evaluate this story using the SECV Scoring Rubric (0-10):
        - MMS (Mental Model Shift): Does this change how I think, or just confirm what I know?
        - SA (Strategic Actionability): Can I make a concrete decision or take action based on this?
        - SD (Signal Density): Is it high-information "meat" or low-signal "fluff"?
        - D (Durability): Will this still be valuable in 2 years?

        Identify Alignment relative to my PERSONAL CONTEXT:
        - ECHO_CHAMBER: Restates or heavily validates my existing views/writings.
        - OPPOSITE_VIEW: Substantively challenges my core philosophies or conclusions.
        - COMPLEMENTARY: Directly relates to my interests and adds new, relevant information or a useful bridge.
        - ORTHOGONAL: Technical and high-quality, but completely unrelated to the themes in my personal context.
        - IRRELEVANT: Low quality, non-technical, or pure noise that should be ignored.

        Identify HypeRisk:
        - LOW: Evidence-based, technical substance, peer-reviewed or verifiable data, no marketing fluff.
        - MEDIUM: Interesting ideas but contains "future-speak", corporate bias, or lacks deep technical implementation details.
        - HIGH: Pure buzzword-driven content, visionary-only manifestos without substance, or clearly AI-generated "slop" designed for SEO.

        Provide a "Sparring Note": A concise, direct, and slightly cynical engineering perspective on why I should (or should not) read this, specifically contrasting it with my PERSONAL CONTEXT.
        
        Provide "Reasoning Bullets": 3 concise bullet points explaining the core reasons behind your scores and alignment. Each bullet should have a short title (e.g., 'Semantic Alignment') and a description.

        CRITICAL: Output ONLY a raw JSON object.
        Format: {"mms": 0, "sa": 0, "sd": 0, "d": 0, "alignment": "ECHO_CHAMBER", "hypeRisk": "LOW", "sparringNote": "...", "reasoningBullets": [{"title": "...", "description": "..."}]}
    """.trimIndent()

    /**
     * Prepares the data bundle with personal context and the target story text.
     */
    private fun generateData(context: String, title: String, url: String, content: String): String = """
        --- PERSONAL CONTEXT (My Source of Truth) ---
        $context
        --- END PERSONAL CONTEXT ---

        --- STORY CONTENT ---
        Title: $title
        URL: $url
        Content:
        $content
        --- END STORY CONTENT ---
    """.trimIndent()

    /**
     * Starts the Strategist's analysis loop, listening for personal context updates and categorized stories.
     */
    override fun start() {
        logger.info { "Strategist: Starting Curator analysis engine..." }
        
        analysisJob = scope.launch {
            eventStream.collect { event ->
                when (event) {
                    is ContextResponse -> {
                        currentContext = event.content
                        logger.info { "Strategist: Personal context updated (${currentContext.length} chars)" }
                    }
                    is StoryCategorized -> {
                        if (shouldAnalyze(event)) {
                            launch { analyze(event) }
                        }
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        }

        // Request initial context
        scope.launch {
            collector.emit(ContextRequest())
        }
    }

    /**
     * Cancels the active analysis loop.
     */
    override fun stop() {
        logger.info { "Strategist: Stopping Curator analysis engine..." }
        analysisJob?.cancel()
    }

    /**
     * Determines whether a story requires deep analysis based on its assigned category.
     *
     * @param event The categorization event.
     * @return True if the story should be analyzed (e.g., WRITING, DEMO, VIDEO).
     */
    private fun shouldAnalyze(event: StoryCategorized): Boolean {
        return event.category == StoryCategory.WRITING || event.category == StoryCategory.DEMO || event.category == StoryCategory.VIDEO
    }

    /**
     * Executes the deep analysis using the configured AI service.
     * Emits an [AnalysisComplete] event upon successful evaluation.
     *
     * @param event The categorization event that triggered this analysis.
     */
    private suspend fun analyze(event: StoryCategorized) {
        val story = repository.get(event.storyId) ?: return
        if (story.cleanText.isNullOrBlank()) {
            logger.warn { "Strategist: Cannot analyze story ${event.storyId} - no content available" }
            return
        }

        logger.info { "Strategist: Performing deep analysis on story: ${story.title}" }

        val instructions = generateInstructions()
        val data = generateData(
            context = currentContext,
            title = story.title,
            url = story.url,
            content = story.cleanText
        )

        val result = aiService.process(instructions, data)
        if (result.exitCode == 0) {
            val jsonContent = sanitizeJson(result.stdout)
            val parsed = Json.decodeFromString<DeepAnalysisResult>(jsonContent)
            
            collector.emit(
                AnalysisComplete(
                    storyId = event.storyId,
                    mms = parsed.mms,
                    sa = parsed.sa,
                    sd = parsed.sd,
                    d = parsed.d,
                    alignment = try { Alignment.valueOf(parsed.alignment.uppercase()) } catch (e: Exception) { Alignment.COMPLEMENTARY },
                    hypeRisk = try { HypeRisk.valueOf(parsed.hypeRisk.uppercase()) } catch (e: Exception) { HypeRisk.MEDIUM },
                    sparringNote = parsed.sparringNote,
                    reasoningBullets = parsed.reasoningBullets
                )
            )
            logger.info { "Strategist: Analysis complete for ${story.title}. Score: ${(parsed.mms + parsed.sa + parsed.sd + parsed.d) / 4.0}/10" }
        } else {
            logger.warn { "Strategist: AI analysis failed for story ${event.storyId} with exit code ${result.exitCode}" }
            throw IllegalStateException("AI analysis failed with exit code ${result.exitCode}")
        }
    }

    /**
     * Cleans up the AI response to extract only the valid JSON payload.
     *
     * @param input The raw output from the AI.
     * @return A sanitized JSON string.
     */
    private fun sanitizeJson(input: String): String {
        val jsonRegex = """(?s)\{.*\}""".toRegex()
        return jsonRegex.find(input)?.value ?: input
    }
}
