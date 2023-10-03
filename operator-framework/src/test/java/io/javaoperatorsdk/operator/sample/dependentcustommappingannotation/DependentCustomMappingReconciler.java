package io.javaoperatorsdk.operator.sample.dependentcustommappingannotation;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import java.util.Map;

@ControllerConfiguration(dependents = {@Dependent(type = CustomMappingConfigMapDependentResource.class)})
public class DependentCustomMappingReconciler
    implements Reconciler<DependentCustomMappingCustomResource> {

  @Override
  public UpdateControl<DependentCustomMappingCustomResource> reconcile(
      DependentCustomMappingCustomResource resource,
      Context<DependentCustomMappingCustomResource> context) throws Exception {

    return UpdateControl.noUpdate();
  }


}
