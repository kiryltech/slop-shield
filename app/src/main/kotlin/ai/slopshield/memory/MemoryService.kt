package ai.slopshield.memory

import ai.slopshield.core.ContextRequest
import ai.slopshield.core.ContextResponse
import ai.slopshield.core.SlopEvent
import ai.slopshield.core.SlopHandler
import ai.slopshield.core.SlopListener
import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.FlowCollector
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * The Memory domain service.
 * Ingests personal context from a local directory and provides it as a pre-prompt bundle.
 */
@SlopListener
@SlopService
class MemoryService(
    private val collector: FlowCollector<SlopEvent>,
    private val contextPath: String = System.getProperty("slopshield.context.path", "context")
) : SlopHandler<ContextRequest>, SlopServiceLifecycle {

    private val allowedExtensions = setOf("md", "txt", "xml")
    private var cachedContext: String = ""

    override fun start() {
        logger.info { "MemoryService: Initializing personal context from $contextPath..." }
        cachedContext = loadContext()
    }

    override suspend fun onEvent(event: ContextRequest) {
        collector.emit(ContextResponse(cachedContext))
    }

    /**
     * Loads and aggregates context from the configured directory.
     * CONTEXT.md is prioritized and included first.
     */
    fun loadContext(): String {
        val dir = File(contextPath)
        if (!dir.exists() || !dir.isDirectory) {
            logger.warn { "MemoryService: Context directory not found at ${dir.absolutePath}. Using empty context." }
            return ""
        }

        val builder = StringBuilder()
        builder.append("### PERSONAL CONTEXT AND INSTRUCTIONS ###\n")

        // 1. Load CONTEXT.md first if it exists
        val primaryContext = File(dir, "CONTEXT.md")
        if (primaryContext.exists()) {
            builder.append("#### INSTRUCTIONS (from CONTEXT.md) ####\n")
            builder.append(primaryContext.readText())
            builder.append("\n\n")
        }

        // 2. Load other supported files
        builder.append("#### BACKGROUND KNOWLEDGE AND WRITINGS ####\n")
        dir.listFiles()?.filter { 
            it.isFile && it.name != "CONTEXT.md" && it.extension.lowercase() in allowedExtensions 
        }?.forEach { file ->
            builder.append("--- Source: ${file.name} ---\n")
            builder.append(file.readText())
            builder.append("\n\n")
        }

        val result = builder.toString()
        logger.info { "MemoryService: Loaded context bundle (${result.length} chars) from ${dir.absolutePath}" }
        return result
    }
}
