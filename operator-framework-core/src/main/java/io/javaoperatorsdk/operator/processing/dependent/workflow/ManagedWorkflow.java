package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;

@SuppressWarnings("rawtypes")
public class ManagedWorkflow<P extends HasMetadata> {

  private final Workflow<P> workflow;
  private final boolean isCleaner;
  private final boolean isEmptyWorkflow;
  private final Map<String, DependentResource> dependentResourceByName;

  public ManagedWorkflow(KubernetesClient client,
      List<DependentResourceSpec> dependentResourceSpecs) {
    ManagedWorkflowUtils.checkForNameDuplication(dependentResourceSpecs);
    var orderedSpecs = ManagedWorkflowUtils.orderAndDetectCycles(dependentResourceSpecs);
    dependentResourceByName = orderedSpecs
        .stream().collect(Collectors.toMap(DependentResourceSpec::getName,
            spec -> createAndConfigureFrom(spec, client)));


    workflow = toWorkFlow(client, orderedSpecs);
    isCleaner = checkIfCleaner();
    isEmptyWorkflow = workflow.getDependentResources().isEmpty();
  }

  public WorkflowExecutionResult reconcile(P primary, Context<P> context) {
    return workflow.reconcile(primary, context);
  }

  public WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    return workflow.cleanup(primary, context);
  }


  private boolean checkIfCleaner() {
    for (var dr : workflow.getDependentResources()) {
      if (dr instanceof Deleter && !(dr instanceof GarbageCollected)) {
        return true;
      }
    }
    return false;
  }


  @SuppressWarnings("unchecked")
  private Workflow<P> toWorkFlow(KubernetesClient client,
      List<DependentResourceSpec> dependentResourceSpecs) {
    var orderedSpecs = orderDependentsToBeAdded(dependentResourceSpecs);

    var workflow = new WorkflowBuilder<P>();
    orderedSpecs.forEach(spec -> {
      var drBuilder =
          workflow.addDependent(dependentResourceByName.get(spec.getName())).dependsOn(
              (Set<DependentResource>) spec.getDependsOn()
                  .stream().map(dependentResourceByName::get).collect(Collectors.toSet()));
      drBuilder.withDeletePostCondition(spec.getDeletePostCondition());
      drBuilder.withReconcileCondition(spec.getReconcileCondition());
      drBuilder.withReadyCondition(spec.getReadyCondition());
    });
    return workflow.build();
  }

  // todo
  private List<DependentResourceSpec> orderDependentsToBeAdded(
      List<DependentResourceSpec> dependentResourceSpecs) {
    return dependentResourceSpecs;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private DependentResource createAndConfigureFrom(DependentResourceSpec spec,
      KubernetesClient client) {
    final var dependentResource =
        ConfigurationServiceProvider.instance().dependentResourceFactory().createFrom(spec);

    if (dependentResource instanceof KubernetesClientAware) {
      ((KubernetesClientAware) dependentResource).setKubernetesClient(client);
    }

    if (dependentResource instanceof DependentResourceConfigurator) {
      final var configurator = (DependentResourceConfigurator) dependentResource;
      spec.getDependentResourceConfiguration().ifPresent(configurator::configureWith);
    }
    return dependentResource;
  }

  public boolean isCleaner() {
    return isCleaner;
  }

  public boolean isEmptyWorkflow() {
    return isEmptyWorkflow;
  }

  public Map<String, DependentResource> getDependentResourceByName() {
    return dependentResourceByName;
  }
}
