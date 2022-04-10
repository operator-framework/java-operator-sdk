package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorkflowExecutor<P extends HasMetadata> {

    private Set<DependentResourceNode> alreadyReconciled = ConcurrentHashMap.newKeySet();
    private Workflow<P> workflow;



    public void reconcile(P primary, Context<P> context) {

    }

}
