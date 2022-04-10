package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class WorkflowReconcileExecutor<P extends HasMetadata>  {

    private Set<DependentResourceNode> alreadyReconciled = ConcurrentHashMap.newKeySet();

    private Workflow<P> workflow;
    private List<Exception> exceptionsDuringExecution = Collections.synchronizedList(new ArrayList<>());
    private List<Future<?>> actualExecutions = Collections.synchronizedList(new ArrayList<>());

    public WorkflowReconcileExecutor(Workflow<P> workflow) {
        this.workflow = workflow;
    }

    public synchronized void reconcile(P primary, Context<P> context) {
        try {
            for (DependentResourceNode dependentResourceNode : workflow.getTopLevelResources()) {
               var nodeFuture = workflow.getExecutorService().submit(new NodeExecutor(dependentResourceNode));
               actualExecutions.add(nodeFuture);
            }
            this.wait();
        } catch (InterruptedException e) {
            // todo check this better
            throw new IllegalStateException(e);
        }
    }

    private boolean terminateExecution() {
        return !exceptionsDuringExecution.isEmpty();
    }
    private class NodeExecutor implements Runnable {

        private final DependentResourceNode dependentResourceNode;

        private NodeExecutor(DependentResourceNode dependentResourceNode) {
            this.dependentResourceNode = dependentResourceNode;
        }

        @Override
        public void run() {

        }
    }
}
