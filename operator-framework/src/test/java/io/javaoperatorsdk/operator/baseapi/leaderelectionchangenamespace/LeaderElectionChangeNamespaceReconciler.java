package io.javaoperatorsdk.operator.baseapi.leaderelectionchangenamespace;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration()
public class LeaderElectionChangeNamespaceReconciler
    implements Reconciler<LeaderElectionChangeNamespaceCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<LeaderElectionChangeNamespaceCustomResource> reconcile(
      LeaderElectionChangeNamespaceCustomResource resource,
      Context<LeaderElectionChangeNamespaceCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
