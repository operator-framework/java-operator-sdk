package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Workflow<P extends HasMetadata> {

    private final List<DependentResourceNode> dependentResourceNodes;

    private List<DependentResourceNode> topLevelResources = new ArrayList<>();
    private Map<DependentResourceNode, List<DependentResourceNode>> reverseDependsOn;

    // it's "global" executor service shared between multiple reconciliations running parallel
    private ExecutorService executorService;

    public Workflow(List<DependentResourceNode> dependentResourceNodes, ExecutorService executorService) {
        this.executorService = executorService;
        this.dependentResourceNodes = dependentResourceNodes;
        preprocessForReconcile();
    }

    public Workflow(List<DependentResourceNode> dependentResourceNodes, int globalParallelism) {
        this(dependentResourceNodes,Executors.newFixedThreadPool(globalParallelism));
    }

    public void reconcile(P primary, Context<P> context) {

    }

    public void cleanup(P resource, Context<P> context) {

    }

    private void preprocessForReconcile() {
        reverseDependsOn = new ConcurrentHashMap<>(dependentResourceNodes.size());

        for (DependentResourceNode node : dependentResourceNodes) {
            if (node.getDependsOnRelations().isEmpty()) {
              topLevelResources.add(node);
            } else {
                for (DependsOnRelation relation : node.getDependsOnRelations()) {
                    reverseDependsOn.computeIfAbsent(relation.getDependsOn(), dr -> new ArrayList<>());
                    reverseDependsOn.get(relation.getDependsOn()).add(relation.getOwner());
                }
            }
        }
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    List<DependentResourceNode> getTopLevelResources() {
        return topLevelResources;
    }

    Map<DependentResourceNode, List<DependentResourceNode>> getReverseDependsOn() {
        return reverseDependsOn;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }
}
