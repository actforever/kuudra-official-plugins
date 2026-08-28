package io.github.actforever.kuudra.macrokotlin

import io.github.actforever.kuudra.macro.MacroCompileOptions
import io.github.actforever.kuudra.macro.MacroStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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
        assertEquals(5,program.steps().size)
        assertEquals(MacroStep.KeyPress::class.java,program.steps()[0].javaClass)
        assertEquals(1234,program.maxTotalSteps())
    }
}
