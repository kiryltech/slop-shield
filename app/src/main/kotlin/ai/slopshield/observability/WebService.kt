package ai.slopshield.observability

import ai.slopshield.core.SlopService
import ai.slopshield.core.SlopServiceLifecycle
import ai.slopshield.core.StoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title

private val logger = KotlinLogging.logger {}

/**
 * A minimalist Web UI to view the contents of the StoryRepository.
 * This is the V1 interface that generates server-side HTML.
 *
 * @property repository The underlying repository containing processed stories.
 * @property port The port number on which the Web UI will run. Defaults to 8080.
 */
@SlopService
class WebService(
    private val repository: StoryRepository,
    private val port: Int = System.getProperty("slopshield.web.port", "8080").toInt()
) : SlopServiceLifecycle {
    private var server: NettyApplicationEngine? = null

    /**
     * Starts the embedded Netty server to serve the HTML Dashboard.
     */
    override fun start() {
        logger.info { "WebService: Starting Web UI on port $port..." }
        
        server = embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    val stories = repository.getAll().sortedByDescending { it.id }.toList()
                    call.respondHtml {
                        head {
                            title("SlopShield Dashboard")
                            style {
                                +"""
                                    body { font-family: sans-serif; line-height: 1.6; max-width: 1000px; margin: 0 auto; padding: 20px; background: #f4f4f9; }
                                    h1 { color: #333; border-bottom: 2px solid #ccc; padding-bottom: 10px; }
                                    .story-card { background: white; border-radius: 8px; padding: 15px; margin-bottom: 20px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); border-left: 5px solid #ccc; }
                                    .story-card.writing { border-left-color: #2ecc71; }
                                    .story-card.demo { border-left-color: #9b59b6; }
                                    .story-card.product { border-left-color: #e67e22; }
                                    .story-card.video { border-left-color: #e74c3c; }
                                    .story-title { font-size: 1.2em; font-weight: bold; color: #0066cc; text-decoration: none; }
                                    .story-title:hover { text-decoration: underline; }
                                    .meta { font-size: 0.9em; color: #666; margin-top: 5px; }
                                    .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.8em; font-weight: bold; text-transform: uppercase; margin-right: 5px; }
                                    .badge.writing { background: #e3f2fd; color: #0d47a1; }
                                    .badge.product { background: #f1f8e9; color: #1b5e20; }
                                    .badge.demo { background: #fff3e0; color: #e65100; }
                                    .badge.source { background: #f3e5f5; color: #4a148c; }
                                    .badge.video { background: #ffebee; color: #c62828; }
                                    .badge.unknown { background: #eeeeee; color: #424242; }
                                    
                                    .analysis-box { margin-top: 15px; padding: 10px; background: #fafafa; border: 1px solid #eee; border-radius: 4px; }
                                    .scores { display: flex; gap: 15px; margin-bottom: 8px; flex-wrap: wrap; }
                                    .score-item { text-align: center; }
                                    .score-val { font-weight: bold; display: block; font-size: 1.1em; }
                                    .score-lbl { font-size: 0.7em; color: #888; text-transform: uppercase; }
                                    .total-signal { color: #d35400; font-size: 1.3em; }
                                    
                                    .alignment { padding: 2px 8px; border-radius: 10px; font-size: 0.75em; font-weight: bold; }
                                    .alignment.echo_chamber { background: #fff9c4; color: #f57f17; }
                                    .alignment.opposite_view { background: #c8e6c9; color: #2e7d32; }
                                    .alignment.complementary { background: #e1f5fe; color: #0277bd; }
                                    
                                    .sparring { margin-top: 10px; padding: 8px; background: #fff; border-left: 3px solid #3498db; font-size: 0.9em; line-height: 1.4; color: #444; }
                                """
                            }
                        }
                        body {
                            h1 { +"🛡️ SlopShield Dashboard" }
                            p { +"Total stories: ${stories.size}" }
                            
                            stories.forEach { story ->
                                div("story-card ${story.category?.name?.lowercase() ?: "unknown"}") {
                                    a(href = story.url, target = "_blank", classes = "story-title") {
                                        +story.title
                                    }
                                    div("meta") {
                                        span("badge ${story.category?.name?.lowercase() ?: "unknown"}") {
                                            +(story.category?.name ?: "UNCATEGORIZED")
                                        }
                                        +" | ID: ${story.id}"
                                    }
                                    
                                    story.analysis?.let { analysis ->
                                        div("analysis-box") {
                                            div("scores") {
                                                div("score-item") {
                                                    span("score-val total-signal") { +"%.1f".format(analysis.totalScore) }
                                                    span("score-lbl") { +"Signal" }
                                                }
                                                listOf("MMS" to analysis.mms, "SA" to analysis.sa, "SD" to analysis.sd, "D" to analysis.d).forEach { (label, value) ->
                                                    div("score-item") {
                                                        span("score-val") { +value.toString() }
                                                        span("score-lbl") { +label }
                                                    }
                                                }
                                                span("alignment ${analysis.alignment.name.lowercase()}") {
                                                    +analysis.alignment.name.replace("_", " ")
                                                }
                                            }
                                            div("sparring") {
                                                +analysis.sparringNote
                                            }
                                        }
                                    } ?: story.categoryReasoning?.let {
                                        div("analysis-box") {
                                            div("sparring") { +it }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    /**
     * Gracefully stops the web server.
     */
    override fun stop() {
        logger.info { "WebService: Stopping Web UI..." }
        server?.stop(1000, 5000)
    }
}
