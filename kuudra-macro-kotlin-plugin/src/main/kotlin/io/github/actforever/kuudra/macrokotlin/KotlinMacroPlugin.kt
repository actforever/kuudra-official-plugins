package io.github.actforever.kuudra.macrokotlin

import io.github.actforever.kuudra.macro.*
import io.github.actforever.kuudra.plugin.KuudraPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

fun macro(body: MacroBuilder.() -> Unit): MacroProgramDefinition = MacroBuilder().apply(body).build()

class KotlinMacroPlugin : KuudraPlugin {
    private var registration: AutoCloseable? = null
    override fun id() = "macro-kotlin"
    override fun start(): CompletionStage<Void> {
        registration = MacroFrontendRegistry.register(KotlinFrontend())
        return CompletableFuture.completedFuture(null)
    }
    override fun stop(): CompletionStage<Void> {
        registration?.close(); registration = null
        return CompletableFuture.completedFuture(null)
    }
}

internal class KotlinFrontend : MacroFrontend {
    override fun extension() = ".kt"
    override fun compile(source: Path, options: MacroCompileOptions): MacroProgramDefinition {
        val text = Files.readString(source)
        val configuration = ScriptCompilationConfiguration {
            jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
            defaultImports(
                "io.github.actforever.kuudra.macrokotlin.macro",
                "io.github.actforever.kuudra.macro.MacroConditions.ref",
                "io.github.actforever.kuudra.interaction.KeyCode.*",
                "io.github.actforever.kuudra.interaction.MouseButton.*",
                "java.util.concurrent.TimeUnit"
            )
        }
        val result = BasicJvmScriptingHost().eval(StringScriptSource(text, source.fileName.toString() + ".kts"), configuration, null)
        val failures = result.reports.filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
        if (failures.isNotEmpty()) throw IllegalArgumentException(failures.joinToString("; ") { report ->
            val location = report.location?.start?.let { ":${it.line}:${it.col}" } ?: ""
            "${source}$location: ${report.message}"
        })
        val evaluation = result.valueOrThrow()
        val value = (evaluation.returnValue as? ResultValue.Value)?.value
        val program = value as? MacroProgramDefinition ?: throw IllegalArgumentException("$source: Kotlin macro must return macro { ... }")
        return MacroProgramDefinition(program.steps(), options.maxTotalSteps(), options.syntheticMarkerLifetimeMillis())
    }
}
