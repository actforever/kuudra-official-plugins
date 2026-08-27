package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.NativeInputEvent;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.interaction.InteractionEvents;

import java.util.Map;

final class NativeKuudraEvents {
    private NativeKuudraEvents() { }

    static KuudraEvent event(String type, Map<String, Object> interaction, NativeInputEvent nativeEvent,
                             Map<String, Object> nativeDetails) {
        EventData data = EventData.of(InteractionEvents.DATA_NAMESPACE, interaction)
                .with(InteractionEvents.NATIVE_DATA_NAMESPACE, "eventId", nativeEvent.getID())
                .with(InteractionEvents.NATIVE_DATA_NAMESPACE, "when", nativeEvent.getWhen())
                .with(InteractionEvents.NATIVE_DATA_NAMESPACE, "modifiers", nativeEvent.getModifiers());
        for (Map.Entry<String, Object> entry : nativeDetails.entrySet()) {
            data = data.with(InteractionEvents.NATIVE_DATA_NAMESPACE, entry.getKey(), entry.getValue());
        }
        return KuudraEvent.of(type, data);
    }
}
