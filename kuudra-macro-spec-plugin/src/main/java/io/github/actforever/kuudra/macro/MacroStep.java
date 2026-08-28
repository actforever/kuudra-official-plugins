package io.github.actforever.kuudra.macro;

import io.github.actforever.kuudra.interaction.*;
import java.util.*;

public sealed interface MacroStep permits MacroStep.KeyPress, MacroStep.KeyRelease, MacroStep.KeyTap, MacroStep.TypeKeys, MacroStep.MousePress, MacroStep.MouseRelease, MacroStep.MouseClick, MacroStep.MouseMove, MacroStep.MouseWheel, MacroStep.Sleep, MacroStep.If, MacroStep.Loop, MacroStep.Break, MacroStep.Return, MacroStep.CancelSession, MacroStep.Emit {
    record KeyPress(KeySpec key) implements MacroStep { }
    record KeyRelease(KeySpec key) implements MacroStep { }
    record KeyTap(KeySpec key,long holdMillis) implements MacroStep { }
    record TypeKeys(List<KeySpec> keys,List<ModifierKey> modifiers,long holdMillis,long intervalMillis) implements MacroStep { public TypeKeys {keys=List.copyOf(keys);modifiers=List.copyOf(modifiers);} }
    record MousePress(MouseButtonSpec button) implements MacroStep { }
    record MouseRelease(MouseButtonSpec button) implements MacroStep { }
    record MouseClick(MouseButtonSpec button,long holdMillis) implements MacroStep { }
    record MouseMove(ScreenPosition position) implements MacroStep { }
    record MouseWheel(MouseWheelSpec wheel) implements MacroStep { }
    record Sleep(long durationMillis) implements MacroStep { }
    record If(MacroCondition condition,List<MacroStep> thenSteps,List<MacroStep> elseSteps) implements MacroStep { public If {thenSteps=List.copyOf(thenSteps);elseSteps=List.copyOf(elseSteps);} }
    record Loop(Long count,long maxIterations,MacroCondition condition,List<MacroStep> steps) implements MacroStep { public Loop {steps=List.copyOf(steps);} }
    record Break(MacroCondition condition) implements MacroStep { }
    record Return(MacroCondition condition) implements MacroStep { }
    record CancelSession(String reason,MacroCondition condition) implements MacroStep { }
    record Emit(String eventType,boolean copyInputData,Map<String,Object> data) implements MacroStep { public Emit {data=deepCopy(data);} private static Map<String,Object> deepCopy(Map<String,Object> value){return Map.copyOf(value);} }
}
