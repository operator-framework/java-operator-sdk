package io.javaoperatorsdk.operator.sample.standalonedependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DesiredSupplier;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class StandaloneDependentTestReconciler
    implements Reconciler<StandaloneDependentTestCustomResource>,
        EventSourceInitializer<StandaloneDependentTestCustomResource>,
        TestExecutionInfoProvider,
        KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient kubernetesClient;

  KubernetesDependentResource<ConfigMap, StandaloneDependentTestCustomResource> configMapDependent;


  public StandaloneDependentTestReconciler() {
    configMapDependent = new KubernetesDependentResource();
    configMapDependent.setDesiredSupplier(new DesiredSupplier<ConfigMap, StandaloneDependentTestCustomResource>() {
      @Override
      public ConfigMap getDesired(StandaloneDependentTestCustomResource primary, Context context) {
        return null;
      }
    });
  }

  @Override
  public List<EventSource> prepareEventSources(
          EventSourceContext<StandaloneDependentTestCustomResource> context) {
    return List.of(configMapDependent.eventSource(context).get());
  }

  @Override
  public UpdateControl<StandaloneDependentTestCustomResource> reconcile(
      StandaloneDependentTestCustomResource resource, Context context) {
    numberOfExecutions.addAndGet(1);

    configMapDependent.reconcile(resource,context);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    configMapDependent.setClient(kubernetesClient);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return this.kubernetesClient;
  }


}
