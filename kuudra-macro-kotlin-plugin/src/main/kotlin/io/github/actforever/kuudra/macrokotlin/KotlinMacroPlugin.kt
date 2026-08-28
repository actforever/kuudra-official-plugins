package io.github.actforever.kuudra.macrokotlin

import io.github.actforever.kuudra.macro.*
import io.github.actforever.kuudra.interaction.KeyCode
import io.github.actforever.kuudra.interaction.*
import io.github.actforever.kuudra.plugin.KuudraPlugin
import io.github.actforever.kuudra.api.action.ActionContext
import java.nio.file.Files
import java.nio.file.Path
import java.net.URLClassLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

fun macro(body: KotlinMacroBuilder.() -> Unit): MacroProgramDefinition =
    MacroBuilder().let { builder -> KotlinMacroBuilder(builder).apply(body); builder.build() }

class KotlinMacroBuilder internal constructor(private val delegate: MacroBuilder) {
    fun press(key: KeyCode) = delegate.press(key)
    fun press(key: KeySpec) = delegate.press(key)
    fun release(key: KeyCode) = delegate.release(key)
    fun release(key: KeySpec) = delegate.release(key)
    fun tap(key: KeySpec) = delegate.tap(key)
    fun tap(key: KeySpec, holdMillis: Long) = delegate.tap(key, holdMillis)
    fun click(button: MouseButton) = delegate.click(button)
    fun click(button: MouseButtonSpec) = delegate.click(button)
    fun click(button: MouseButtonSpec, holdMillis: Long) = delegate.click(button, holdMillis)
    fun mousePress(button: MouseButtonSpec) = delegate.mousePress(button)
    fun mouseRelease(button: MouseButtonSpec) = delegate.mouseRelease(button)
    fun move(position: ScreenPosition) = delegate.move(position)
    fun wheel(wheel: MouseWheelSpec) = delegate.wheel(wheel)
    fun sleep(millis: Long) = delegate.sleep(millis)
    fun sleep(duration: Long, unit: TimeUnit) = delegate.sleep(duration, unit)
    fun whenCondition(condition: MacroCondition, body: KotlinMacroBuilder.() -> Unit): KotlinIfClause {
        val clause=delegate.whenCondition(condition) { nested -> KotlinMacroBuilder(nested).body() }
        return KotlinIfClause(clause)
    }
    fun whileCondition(condition: MacroCondition, maxIterations: Long, body: KotlinMacroBuilder.() -> Unit) =
        delegate.whileCondition(condition, maxIterations) { nested -> KotlinMacroBuilder(nested).body() }
    fun whileTrue(maxIterations: Long, body: KotlinMacroBuilder.() -> Unit) =
        delegate.whileTrue(maxIterations) { nested -> KotlinMacroBuilder(nested).body() }
    fun repeat(count: Long, body: KotlinMacroBuilder.() -> Unit) =
        delegate.repeat(count) { nested -> KotlinMacroBuilder(nested).body() }
    fun breakLoop() = delegate.breakLoop()
    fun returnMacro() = delegate.returnMacro()
    fun cancelSession(reason: String) = delegate.cancelSession(reason)
    fun emit(type: String, value: String) = delegate.emit(type, value)
    fun emit(type: String, data: Map<String, Any?>) = delegate.emit(type, data)
    inner class KotlinIfClause(private val clause: MacroBuilder.IfClause) {
        fun otherwise(body: KotlinMacroBuilder.() -> Unit) = clause.otherwise { nested -> KotlinMacroBuilder(nested).body() }
    }
}

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
        val compilationLoader = URLClassLoader(arrayOf(
            location(KotlinFrontend::class.java),
            location(MacroProgramDefinition::class.java),
            location(KeyCode::class.java),
            location(KuudraPlugin::class.java),
            location(ActionContext::class.java)
        ).distinct().toTypedArray(), javaClass.classLoader)
        val configuration = ScriptCompilationConfiguration {
            // The App thread context ClassLoader belongs to the kernel. Resolve from this
            // plugin's dependency-aware ClassLoader so macro-spec and interaction-spec are
            // visible when the embedded compiler runs inside a real Kuudra deployment.
            jvm { dependenciesFromClassloader(classLoader = compilationLoader, wholeClasspath = true) }
            defaultImports(
                "io.github.actforever.kuudra.macrokotlin.macro",
                "io.github.actforever.kuudra.macro.MacroConditions.ref",
                "io.github.actforever.kuudra.interaction.KeyCode.*",
                "io.github.actforever.kuudra.interaction.MouseButton.*",
                "java.util.concurrent.TimeUnit"
            )
        }
        val evaluationConfiguration = ScriptEvaluationConfiguration {
            jvm { baseClassLoader(javaClass.classLoader) }
        }
        val result = try {
            BasicJvmScriptingHost().eval(StringScriptSource(text, source.fileName.toString() + ".kts"), configuration, evaluationConfiguration)
        } finally {
            compilationLoader.close()
        }
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

    private fun location(type: Class<*>) = type.protectionDomain.codeSource.location
}
