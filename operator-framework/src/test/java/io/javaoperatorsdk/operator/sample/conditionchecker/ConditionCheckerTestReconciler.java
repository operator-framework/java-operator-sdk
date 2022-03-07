package io.javaoperatorsdk.operator.sample.conditionchecker;

import java.util.List;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Creator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Updater;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.waitfor.ConditionChecker;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class ConditionCheckerTestReconciler
    implements Reconciler<ConditionCheckerTestCustomResource>,
    EventSourceInitializer<ConditionCheckerTestCustomResource>,
    KubernetesClientAware {

  private KubernetesClient kubernetesClient;
  private final CheckerDeploymentDependentResource deploymentDependent =
      new CheckerDeploymentDependentResource();

  public ConditionCheckerTestReconciler() {}

  @Override
  public List<EventSource> prepareEventSources(
      EventSourceContext<ConditionCheckerTestCustomResource> context) {
    return List.of(deploymentDependent.eventSource(context));
  }

  @Override
  public UpdateControl<ConditionCheckerTestCustomResource> reconcile(
      ConditionCheckerTestCustomResource primary, Context context) {
    deploymentDependent.reconcile(primary, context);

    ConditionChecker.<Deployment>checker()
        .withConditionNotFulfilledHandler(() -> {
          primary.getStatus().setWasNotReadyYet(true);
          return UpdateControl.updateStatus(primary);
        })
        .withCondition(r -> r.getSpec().getReplicas().equals(r.getStatus().getReadyReplicas()))
        .check(deploymentDependent, primary);

    deploymentDependent.getResource(primary).ifPresentOrElse(
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
    deploymentDependent.setKubernetesClient(kubernetesClient);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return this.kubernetesClient;
  }

  public static class CheckerDeploymentDependentResource extends
      KubernetesDependentResource<Deployment, ConditionCheckerTestCustomResource>
      implements Creator<Deployment, ConditionCheckerTestCustomResource>,
      Updater<Deployment, ConditionCheckerTestCustomResource> {

    @Override
    protected Deployment desired(ConditionCheckerTestCustomResource primary, Context context) {
      Deployment deployment =
          ReconcilerUtils.loadYaml(Deployment.class, getClass(),
              "nginx-deployment.yaml");
      deployment.getMetadata().setName(primary.getMetadata().getName());
      deployment.getSpec().setReplicas(primary.getSpec().getReplicaCount());
      deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
      return deployment;
    }
  }
}
