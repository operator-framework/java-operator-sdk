package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;

import java.util.List;

@SuppressWarnings("rawtypes")
class ManagedWorkflow<P extends HasMetadata> {

    private Workflow<P> workflow;
    private final boolean isCleaner;

    public ManagedWorkflow(KubernetesClient client, List<DependentResourceSpec> dependentResourceSpecs) {
        workflow = toWorkFlow(client,dependentResourceSpecs);

        isCleaner = checkIfCleaner();
    }

    // todo
    private boolean checkIfCleaner() {
        return false;
    }

    // todo
    public boolean isCleaner() {
        return isCleaner;
    }

    private Workflow<P> toWorkFlow(KubernetesClient client, List<DependentResourceSpec> dependentResourceSpecs) {
        List<DependentResourceSpec> orderedDependentResources = orderDependentsToBeAdded(dependentResourceSpecs);

        var w = new WorkflowBuilder<P>();
        return w.build();
    }

    private List<DependentResourceSpec> orderDependentsToBeAdded(List<DependentResourceSpec> dependentResourceSpecs) {
        return null;
    }

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



}
