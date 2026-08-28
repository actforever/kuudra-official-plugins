package io.github.actforever.kuudra.macrokotlin

import io.github.actforever.kuudra.macro.MacroCompileOptions
import io.github.actforever.kuudra.macro.MacroStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.net.URLClassLoader

class KotlinFrontendTest {
    @Test fun `compiles Kotlin builder into neutral IR`(@TempDir directory: Path) {
        val source=directory.resolve("hello.kt")
        Files.writeString(source,"""
            macro {
                press(A)
                sleep(50)
                release(A)
                whenCondition(ref("session#enabled").eq(true)) { click(BUTTON_1) }
            }
        """.trimIndent())
        val program=KotlinFrontend().compile(source,MacroCompileOptions(1234,250))
        assertEquals(4,program.steps().size)
        assertEquals(MacroStep.KeyPress::class.java,program.steps()[0].javaClass)
        val branch=program.steps()[3] as MacroStep.If
        assertEquals(1,branch.thenSteps().size)
        assertEquals(0,branch.elseSteps().size)
        assertEquals(1234,program.maxTotalSteps())
    }

    @Test fun `does not depend on kernel thread context classloader`(@TempDir directory: Path) {
        val source=directory.resolve("isolated.kt")
        Files.writeString(source,"macro { emit(\"isolated.completed\", \"ok\") }")
        val thread=Thread.currentThread()
        val original=thread.contextClassLoader
        URLClassLoader(arrayOf(), ClassLoader.getPlatformClassLoader()).use { isolated ->
            thread.contextClassLoader=isolated
            try {
                val program=KotlinFrontend().compile(source,MacroCompileOptions(100,500))
                assertEquals(MacroStep.Emit::class.java,program.steps().single().javaClass)
            } finally {
                thread.contextClassLoader=original
            }
        }
    }
}
