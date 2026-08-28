# Kuudra Macro Kotlin Plugin

`actforever/macro-kotlin` registers the `.kt` authoring frontend for `actforever/macro-spec`. It embeds Kotlin 2.4.10 so deployment needs one plugin JAR, and declares an empty component index because it contributes a frontend rather than a Flow component.

Scripts are trusted local Kotlin builder programs. They are compiled at component initialization (and when an executor explicitly reloads a changed file), return `MacroProgramDefinition`, and are not executed for every Event. Dynamic decisions must use `ref("session#value")` and related macro conditions so actual context lookup remains inside the executor.

Compilation resolves its classpath from the dependency-aware plugin ClassLoader rather than the App thread context ClassLoader. Evaluation uses that same loader as its parent, preserving the shared `macro-spec` type identity across `macro-kotlin` and executor plugins. The exposed `KotlinMacroBuilder` is a real receiver DSL; nested condition and loop lambdas build nested IR nodes instead of accidentally appending operations to the outer program.
