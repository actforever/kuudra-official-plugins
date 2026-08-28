package io.github.actforever.kuudra.macro;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MacroFrontendRegistry {
    private static final Map<String,MacroFrontend> FRONTENDS=new ConcurrentHashMap<>();
    private MacroFrontendRegistry(){ }
    public static AutoCloseable register(MacroFrontend frontend){String ext=normalize(frontend.extension());if(FRONTENDS.putIfAbsent(ext,frontend)!=null)throw new IllegalStateException("Macro frontend already registered for "+ext);return ()->FRONTENDS.remove(ext,frontend);}
    public static Optional<MacroFrontend> find(String extension){return Optional.ofNullable(FRONTENDS.get(normalize(extension)));}
    public static Set<String> extensions(){return Set.copyOf(FRONTENDS.keySet());}
    private static String normalize(String value){String ext=Objects.requireNonNull(value).trim().toLowerCase(Locale.ROOT);return ext.startsWith(".")?ext:"."+ext;}
}
