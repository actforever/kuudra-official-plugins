package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.*;
import io.github.actforever.kuudra.interaction.KeySpec;
import io.github.actforever.kuudra.macro.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class MacroProgram {
    private final MacroProgramDefinition definition;
    MacroProgram(MacroProgramDefinition definition) { this.definition = definition; }
    static MacroProgram parse(Map<String,Object> configuration) { return new MacroProgram(MacroCodec.decode(configuration)); }

    void execute(KuudraEvent event, EventHandlerContext context, RobotDriver driver) {
        Frame frame = new Frame(event, context, new InputState(driver, definition.syntheticMarkerLifetimeMillis()), definition.maxTotalSteps());
        try { if (run(definition.steps(), frame) == Signal.BREAK) throw invalid("break may only be used inside a loop"); }
        catch (CancelSignal ignored) { }
        finally { frame.inputs.releaseAll(); }
    }

    private static Signal run(List<MacroStep> steps, Frame frame) {
        for (MacroStep step : steps) {
            frame.checkpoint(); frame.consumeStep();
            Signal signal = execute(step, frame);
            if (signal != Signal.CONTINUE) return signal;
            frame.checkpoint();
        }
        return Signal.CONTINUE;
    }

    private static Signal execute(MacroStep step, Frame frame) {
        if (step instanceof MacroStep.KeyPress v) frame.inputs.keyPress(v.key());
        else if (step instanceof MacroStep.KeyRelease v) frame.inputs.keyRelease(v.key());
        else if (step instanceof MacroStep.KeyTap v) tap(frame, v.key(), v.holdMillis());
        else if (step instanceof MacroStep.TypeKeys v) type(frame, v);
        else if (step instanceof MacroStep.MousePress v) frame.inputs.mousePress(v.button());
        else if (step instanceof MacroStep.MouseRelease v) frame.inputs.mouseRelease(v.button());
        else if (step instanceof MacroStep.MouseClick v) { frame.inputs.mousePress(v.button()); try { frame.sleep(v.holdMillis()); } finally { frame.inputs.mouseRelease(v.button()); } }
        else if (step instanceof MacroStep.MouseMove v) frame.inputs.mouseMove(v.position());
        else if (step instanceof MacroStep.MouseWheel v) frame.inputs.mouseWheel(v.wheel());
        else if (step instanceof MacroStep.Sleep v) frame.sleep(v.durationMillis());
        else if (step instanceof MacroStep.If v) return run(v.condition().matches(frame.event, frame.context) ? v.thenSteps() : v.elseSteps(), frame);
        else if (step instanceof MacroStep.Loop v) return loop(frame, v);
        else if (step instanceof MacroStep.Break v) return matches(v.condition(), frame) ? Signal.BREAK : Signal.CONTINUE;
        else if (step instanceof MacroStep.Return v) return matches(v.condition(), frame) ? Signal.RETURN : Signal.CONTINUE;
        else if (step instanceof MacroStep.CancelSession v) { if (matches(v.condition(), frame)) { frame.context.sessionControl().requestCancellation(v.reason()); return Signal.CANCEL; } }
        else if (step instanceof MacroStep.Emit v) emit(frame, v);
        else throw invalid("Unsupported macro IR step: " + step.getClass().getName());
        return Signal.CONTINUE;
    }

    private static void tap(Frame frame, KeySpec key, long holdMillis) { frame.inputs.keyPress(key); try { frame.sleep(holdMillis); } finally { frame.inputs.keyRelease(key); } }
    private static void type(Frame frame, MacroStep.TypeKeys value) {
        List<KeySpec> modifiers=value.modifiers().stream().map(KeyMapper::modifier).toList(); modifiers.forEach(frame.inputs::keyPress);
        try { for(KeySpec key:value.keys()){frame.checkpoint();tap(frame,key,value.holdMillis());if(value.intervalMillis()>0)frame.sleep(value.intervalMillis());} }
        finally { for(int i=modifiers.size()-1;i>=0;i--)frame.inputs.keyRelease(modifiers.get(i)); }
    }
    private static Signal loop(Frame frame, MacroStep.Loop value) {
        long count=value.count()==null?value.maxIterations():Math.min(value.count(),value.maxIterations());
        for(long i=0;i<count;i++){frame.checkpoint();if(value.condition()!=null&&!value.condition().matches(frame.event,frame.context))break;Signal signal=run(value.steps(),frame);if(signal==Signal.BREAK)break;if(signal!=Signal.CONTINUE)return signal;}return Signal.CONTINUE;
    }
    private static boolean matches(MacroCondition condition,Frame frame){return condition==null||condition.matches(frame.event,frame.context);}
    private static void emit(Frame frame,MacroStep.Emit value){EventData data=value.copyInputData()?frame.event.data():EventData.empty();for(var namespace:value.data().entrySet()){if(!(namespace.getValue() instanceof Map<?,?> values))throw invalid("emit data namespaces must be objects");for(var item:values.entrySet())data=data.with(namespace.getKey(),String.valueOf(item.getKey()),item.getValue());}frame.context.emit(KuudraEvent.of(value.eventType(),data));}

    private enum Signal { CONTINUE, BREAK, RETURN, CANCEL }
    private static final class Frame {
        final KuudraEvent event; final EventHandlerContext context; final InputState inputs; long remaining;
        Frame(KuudraEvent event,EventHandlerContext context,InputState inputs,long remaining){this.event=event;this.context=context;this.inputs=inputs;this.remaining=remaining;}
        void consumeStep(){if(--remaining<0)throw invalid("Macro exceeded maxTotalSteps");}
        void checkpoint(){ExecutionDecision decision=context.executionControl().poll();if(decision==ExecutionDecision.CANCEL)throw new CancelSignal();if(decision!=ExecutionDecision.PAUSE)return;inputs.suspend();decision=context.executionControl().checkpoint().toCompletableFuture().join();if(decision==ExecutionDecision.CANCEL)throw new CancelSignal();inputs.resume();}
        void sleep(long millis){if(millis<0)throw invalid("duration must be non-negative");long remaining=millis;while(remaining>0){checkpoint();long chunk=Math.min(remaining,25),before=System.nanoTime();try{Thread.sleep(chunk);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancelSignal();}remaining=Math.max(0,remaining-Math.max(1,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)));}}
    }
    private static KuudraException invalid(String message){return new KuudraException(message);}
    private static final class CancelSignal extends RuntimeException { }
}
