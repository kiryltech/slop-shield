package ai.slopshield.observability

import ai.slopshield.core.DomainEventStream
import ai.slopshield.core.HarvestComplete
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ProcessHandle

private val logger = KotlinLogging.logger {}

/**
 * A debug component that dumps harvested story content to disk.
 */
class HarvestDumper(
    private val scope: CoroutineScope,
    private val eventStream: DomainEventStream
) {
    private val pid = ProcessHandle.current().pid()
    private val defaultPath = "/tmp/slop-shield-$pid"
    private val dumpDir = File(System.getProperty("slopshield.dump.path", defaultPath))

    fun start() {
        if (!dumpDir.exists()) {
            dumpDir.mkdirs()
        }
        logger.info { "HarvestDumper: Debug dumper started. Target directory: ${dumpDir.absolutePath}" }

        scope.launch {
            eventStream.events
                .filterIsInstance<HarvestComplete>()
                .collect { event ->
                    dump(event)
                }
        }
    }

    private fun dump(event: HarvestComplete) {
        try {
            val baseFile = File(dumpDir, event.storyId)
            File("${baseFile.absolutePath}.stdout.md").writeText(event.cleanText)
            if (event.errorText.isNotBlank()) {
                File("${baseFile.absolutePath}.stderr.log").writeText(event.errorText)
            }
            logger.debug { "HarvestDumper: Dumped story ${event.storyId} (exit: ${event.exitCode}) to ${dumpDir.absolutePath}" }
        } catch (e: Exception) {
            logger.error(e) { "HarvestDumper: Failed to dump story ${event.storyId}" }
        }
    }
}
