package io.github.actforever.kuudra.macro;

import java.nio.file.Path;

public interface MacroFrontend {
    String extension();
    MacroProgramDefinition compile(Path source, MacroCompileOptions options);
}
