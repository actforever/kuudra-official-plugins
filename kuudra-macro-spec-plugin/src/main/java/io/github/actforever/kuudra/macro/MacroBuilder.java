package io.github.actforever.kuudra.macro;

import io.github.actforever.kuudra.interaction.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MacroBuilder {
    private final List<MacroStep> steps=new ArrayList<>();
    private long maxTotalSteps=10_000, markerLifetimeMillis=500;
    public static MacroProgramDefinition macro(Consumer<MacroBuilder> body){MacroBuilder builder=new MacroBuilder();body.accept(builder);return builder.build();}
    public MacroBuilder limits(long steps,long markerMillis){this.maxTotalSteps=steps;this.markerLifetimeMillis=markerMillis;return this;}
    public void press(KeySpec key){steps.add(new MacroStep.KeyPress(key));} public void release(KeySpec key){steps.add(new MacroStep.KeyRelease(key));}
    public void press(KeyCode key){press(new KeySpec(key,KeyLocation.STANDARD));} public void release(KeyCode key){release(new KeySpec(key,KeyLocation.STANDARD));}
    public void tap(KeySpec key){tap(key,50);} public void tap(KeySpec key,long holdMillis){steps.add(new MacroStep.KeyTap(key,holdMillis));}
    public void click(MouseButtonSpec button){click(button,50);} public void click(MouseButtonSpec button,long holdMillis){steps.add(new MacroStep.MouseClick(button,holdMillis));}
    public void click(MouseButton button){click(new MouseButtonSpec(button));}
    public void mousePress(MouseButtonSpec button){steps.add(new MacroStep.MousePress(button));} public void mouseRelease(MouseButtonSpec button){steps.add(new MacroStep.MouseRelease(button));}
    public void move(ScreenPosition position){steps.add(new MacroStep.MouseMove(position));} public void wheel(MouseWheelSpec wheel){steps.add(new MacroStep.MouseWheel(wheel));}
    public void sleep(long millis){steps.add(new MacroStep.Sleep(millis));} public void sleep(long duration,TimeUnit unit){sleep(unit.toMillis(duration));}
    public IfClause whenCondition(MacroCondition condition,Consumer<MacroBuilder> thenBody){MacroBuilder nested=new MacroBuilder();thenBody.accept(nested);MacroStep.If step=new MacroStep.If(condition,nested.steps,List.of());steps.add(step);return new IfClause(steps.size()-1,step);}
    public void whileCondition(MacroCondition condition,long maxIterations,Consumer<MacroBuilder> body){loop(null,maxIterations,condition,body);} public void whileTrue(long maxIterations,Consumer<MacroBuilder> body){loop(null,maxIterations,null,body);}
    public void repeat(long count,Consumer<MacroBuilder> body){loop(count,count,null,body);} private void loop(Long count,long max,MacroCondition condition,Consumer<MacroBuilder> body){MacroBuilder nested=new MacroBuilder();body.accept(nested);steps.add(new MacroStep.Loop(count,max,condition,nested.steps));}
    public void breakLoop(){steps.add(new MacroStep.Break(null));} public void returnMacro(){steps.add(new MacroStep.Return(null));}
    public void cancelSession(String reason){steps.add(new MacroStep.CancelSession(reason,null));}
    public void emit(String type,String value){steps.add(new MacroStep.Emit(type,false,Map.of("macro",Map.of("value",value))));}
    public void emit(String type,Map<String,Object> data){steps.add(new MacroStep.Emit(type,false,data));}
    public MacroProgramDefinition build(){return new MacroProgramDefinition(steps,maxTotalSteps,markerLifetimeMillis);}
    public final class IfClause {private final int index;private final MacroStep.If original;private IfClause(int index,MacroStep.If original){this.index=index;this.original=original;}public void otherwise(Consumer<MacroBuilder> body){MacroBuilder nested=new MacroBuilder();body.accept(nested);steps.set(index,new MacroStep.If(original.condition(),original.thenSteps(),nested.steps));}}
}
