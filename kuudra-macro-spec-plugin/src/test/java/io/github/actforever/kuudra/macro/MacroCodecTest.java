package io.github.actforever.kuudra.macro;

import io.github.actforever.kuudra.interaction.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MacroCodecTest {
    @Test void roundTripsNestedBuilderProgram() {
        MacroProgramDefinition original=MacroBuilder.macro(macro->{
            macro.press(KeyCode.A);
            macro.whenCondition(MacroConditions.ref("session#enabled").eq(true),yes->yes.click(MouseButton.BUTTON_1));
            macro.repeat(2,loop->loop.sleep(10));
        });
        MacroProgramDefinition decoded=MacroCodec.decode(MacroCodec.encode(original));
        assertEquals(original,decoded);
    }
}
