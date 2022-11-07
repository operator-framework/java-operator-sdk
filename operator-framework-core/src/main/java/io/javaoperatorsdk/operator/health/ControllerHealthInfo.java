package io.javaoperatorsdk.operator.health;

import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("rawtypes")
public class ControllerHealthInfo {

    private EventSourceManager<?> eventSourceManager;

    public ControllerHealthInfo(EventSourceManager eventSourceManager) {
        this.eventSourceManager = eventSourceManager;
    }

    public Map<String,EventSourceHealthIndicator> eventSourceHealthIndicators() {
        return eventSourceManager.allEventSources().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String,EventSourceHealthIndicator> unhealthyEventSources() {
        return eventSourceManager.allEventSources().entrySet().stream()
                .filter(e->e.getValue().getStatus() == Status.UNHEALTHY)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String,InformerEventSourceHealthIndicator> informerEventSourceHealthIndicators() {
        return eventSourceManager.allEventSources().entrySet().stream()
                .filter(e-> e.getValue() instanceof InformerEventSourceHealthIndicator)
                .collect(Collectors.toMap(Map.Entry::getKey, e->(InformerEventSourceHealthIndicator)e.getValue()));

    }
    public Map<String,InformerEventSourceHealthIndicator> unhealthyInformerEventSourceHealthIndicators() {
        return eventSourceManager.allEventSources().entrySet().stream()
                .filter(e-> e.getValue().getStatus() == Status.UNHEALTHY)
                .filter(e-> e.getValue() instanceof InformerEventSourceHealthIndicator)
                .collect(Collectors.toMap(Map.Entry::getKey, e->(InformerEventSourceHealthIndicator)e.getValue()));
    }

}
