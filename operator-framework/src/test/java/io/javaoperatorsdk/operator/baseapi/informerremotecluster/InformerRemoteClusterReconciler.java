package io.javaoperatorsdk.operator.baseapi.informerremotecluster;

import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@ControllerConfiguration
public class InformerRemoteClusterReconciler
    implements Reconciler<InformerRemoteClusterCustomResource> {

  public static final String DATA_KEY = "key";

  private final KubernetesClient remoteClient;

  public InformerRemoteClusterReconciler(KubernetesClient remoteClient) {
    this.remoteClient = remoteClient;
  }

  @Override
  public UpdateControl<InformerRemoteClusterCustomResource> reconcile(
      InformerRemoteClusterCustomResource resource,
      Context<InformerRemoteClusterCustomResource> context)
      throws Exception {

    return context
        .getSecondaryResource(ConfigMap.class)
        .map(
            cm -> {
              var r = new InformerRemoteClusterCustomResource();
              r.setMetadata(
                  new ObjectMetaBuilder()
                      .withName(resource.getMetadata().getName())
                      .withNamespace(resource.getMetadata().getNamespace())
                      .build());
              r.setStatus(new InformerRemoteClusterStatus());
              r.getStatus().setRemoteConfigMapMessage(cm.getData().get(DATA_KEY));
              return UpdateControl.patchStatus(r);
            })
        .orElseGet(UpdateControl::noUpdate);
  }

  @Override
  public List<EventSource<?, InformerRemoteClusterCustomResource>> prepareEventSources(
      EventSourceContext<InformerRemoteClusterCustomResource> context) {

    var es =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, InformerRemoteClusterCustomResource.class)
                // owner references do not work cross cluster, using
                // annotations here to reference primary resource
                .withSecondaryToPrimaryMapper(
                    Mappers.fromDefaultAnnotations(InformerRemoteClusterCustomResource.class))
                // setting remote client for informer
                .withKubernetesClient(remoteClient)
                .withWatchAllNamespaces()
                .build(),
            context);

    return List.of(es);
  }
}
