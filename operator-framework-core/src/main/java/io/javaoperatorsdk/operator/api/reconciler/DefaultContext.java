package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DefaultManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.api.reconciler.expectation.ExpectationResult;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class DefaultContext<P extends HasMetadata> extends DefaultCacheAware<P>
    implements Context<P> {

  private RetryInfo retryInfo;

  private final DefaultManagedWorkflowAndDependentResourceContext<P>
      defaultManagedDependentResourceContext;

  private final ExpectationResult<P> expectationResult;

  public DefaultContext(
      RetryInfo retryInfo,
      Controller<P> controller,
      P primaryResource,
      ExpectationResult<P> expectationResult) {
    super(controller, primaryResource);
    this.retryInfo = retryInfo;
    this.defaultManagedDependentResourceContext =
        new DefaultManagedWorkflowAndDependentResourceContext<>(controller, primaryResource, this);
    this.expectationResult = expectationResult;
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }

  @Override
  public ManagedWorkflowAndDependentResourceContext managedWorkflowAndDependentResourceContext() {
    return defaultManagedDependentResourceContext;
  }

  @Override
  public KubernetesClient getClient() {
    return controller.getClient();
  }

  @Override
  public ExecutorService getWorkflowExecutorService() {
    // note that this should be always received from executor service manager, so we are able to do
    // restarts.
    return controller.getExecutorServiceManager().workflowExecutorService();
  }

  @Override
  public boolean isNextReconciliationImminent() {
    return controller
        .getEventProcessor()
        .isNextReconciliationImminent(ResourceID.fromResource(primaryResource));
  }

  @Override
  public Optional<ExpectationResult<P>> expectationResult() {
    return Optional.ofNullable(expectationResult);
  }

  public DefaultContext<P> setRetryInfo(RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
    return this;
  }
}
