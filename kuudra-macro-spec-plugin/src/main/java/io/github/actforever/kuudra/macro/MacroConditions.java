package io.github.actforever.kuudra.macro;

public final class MacroConditions {
    private MacroConditions(){ }
    public static Reference ref(String path){return new Reference(path);}
    public record Reference(String path){
        public MacroCondition eq(Object value){return MacroCondition.ref(path,MacroCondition.Operator.EQUALS,value);}
        public MacroCondition notEq(Object value){return MacroCondition.ref(path,MacroCondition.Operator.NOT_EQUALS,value);}
        public MacroCondition truthy(){return MacroCondition.ref(path,MacroCondition.Operator.TRUTHY,null);}
        public MacroCondition falsy(){return MacroCondition.ref(path,MacroCondition.Operator.FALSY,null);}
        public MacroCondition exists(){return MacroCondition.ref(path,MacroCondition.Operator.EXISTS,null);}
    }
}
