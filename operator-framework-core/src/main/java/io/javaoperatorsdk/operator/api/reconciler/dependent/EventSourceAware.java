package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;

public interface EventSourceAware<P extends HasMetadata> {

  void selectEventSources(EventSourceRetriever<P> eventSourceRetriever);

}
