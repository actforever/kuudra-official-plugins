# Kuudra Macro Kotlin Plugin

`actforever/macro-kotlin` registers the `.kt` authoring frontend for `actforever/macro-spec`. It embeds Kotlin 2.4.10 so deployment needs one plugin JAR, and declares an empty component index because it contributes a frontend rather than a Flow component.

Scripts are trusted local Kotlin builder programs. They are compiled at component initialization (and when an executor explicitly reloads a changed file), return `MacroProgramDefinition`, and are not executed for every Event. Dynamic decisions must use `ref("session#value")` and related macro conditions so actual context lookup remains inside the executor.
