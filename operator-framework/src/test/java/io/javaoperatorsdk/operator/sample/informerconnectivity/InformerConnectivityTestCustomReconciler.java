package io.javaoperatorsdk.operator.sample.informerconnectivity;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class InformerConnectivityTestCustomReconciler
    implements Reconciler<InformerConnectivityTestCustomResource>,
    EventSourceInitializer<InformerConnectivityTestCustomResource> {

  @Override
  public UpdateControl<InformerConnectivityTestCustomResource> reconcile(
      InformerConnectivityTestCustomResource resource,
      Context<InformerConnectivityTestCustomResource> context) {

    return UpdateControl.noUpdate();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<InformerConnectivityTestCustomResource> context) {
    return EventSourceInitializer.nameEventSources(
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context).build(),
            context));
  }
}
