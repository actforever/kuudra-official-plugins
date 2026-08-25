package io.github.actforever.kuudra.defaultplugin;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import java.util.List;
@io.github.actforever.kuudra.plugin.annotation.Egress("plain-egress")
@ComponentDoc(purpose="Exports an event from the session domain while preserving event data and lineage.")
public final class PlainEgress implements io.github.actforever.kuudra.api.component.Egress {
    @Override public List<KuudraEvent> export(KuudraEvent event, EventContext context) { return List.of(event); }
}
