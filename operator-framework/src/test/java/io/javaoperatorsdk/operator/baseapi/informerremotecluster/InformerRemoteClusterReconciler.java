package io.javaoperatorsdk.operator.baseapi.informerremotecluster;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class InformerRemoteClusterReconciler
    implements Reconciler<InformerRemoteClusterCustomResource> {


  @Override
  public UpdateControl<InformerRemoteClusterCustomResource> reconcile(
      InformerRemoteClusterCustomResource resource,
      Context<InformerRemoteClusterCustomResource> context) throws Exception {



    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, InformerRemoteClusterCustomResource>> prepareEventSources(
      EventSourceContext<InformerRemoteClusterCustomResource> context) {
    return Reconciler.super.prepareEventSources(context);
  }
}
