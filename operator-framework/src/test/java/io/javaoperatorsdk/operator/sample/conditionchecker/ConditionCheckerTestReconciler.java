package io.javaoperatorsdk.operator.sample.conditionchecker;

import java.util.List;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.waitfor.ConditionChecker;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class ConditionCheckerTestReconciler
    implements Reconciler<ConditionCheckerTestCustomResource>,
    EventSourceInitializer<ConditionCheckerTestCustomResource>,
    KubernetesClientAware {

  private KubernetesClient kubernetesClient;
  private final NginxDeploymentDependentResource nginxDeploymentDependentResource =
      new NginxDeploymentDependentResource();

  public ConditionCheckerTestReconciler() {}

  @Override
  public List<EventSource> prepareEventSources(
      EventSourceContext<ConditionCheckerTestCustomResource> context) {
    return List.of(nginxDeploymentDependentResource.eventSource(context));

  }

  @Override
  public UpdateControl<ConditionCheckerTestCustomResource> reconcile(
      ConditionCheckerTestCustomResource primary, Context context) {
    nginxDeploymentDependentResource.reconcile(primary, context);

    ConditionChecker.<Deployment>checker()
        .withConditionNotFulfilledHandler(() -> {
          primary.getStatus().setWasNotReadyYet(true);
          return UpdateControl.updateStatus(primary);
        })
        .withCondition(r -> r.getSpec().getReplicas().equals(r.getStatus().getReadyReplicas()))
        .check(nginxDeploymentDependentResource, primary);

    nginxDeploymentDependentResource.getResource(primary).ifPresentOrElse(
        d -> primary.getStatus()
            .setReady(d.getSpec().getReplicas().equals(d.getStatus().getReadyReplicas())),
        () -> {
          throw new IllegalStateException("Should not end here");
        });

    return UpdateControl.updateStatus(primary);
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    nginxDeploymentDependentResource.setKubernetesClient(kubernetesClient);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return this.kubernetesClient;
  }
}
