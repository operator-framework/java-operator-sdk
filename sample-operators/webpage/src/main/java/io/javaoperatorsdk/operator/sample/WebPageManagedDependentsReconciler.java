package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.*;

import static io.javaoperatorsdk.operator.sample.Utils.createStatus;
import static io.javaoperatorsdk.operator.sample.Utils.handleError;
import static io.javaoperatorsdk.operator.sample.Utils.simulateErrorIfRequested;

/**
 * Shows how to implement a reconciler with managed dependent resources.
 */
@ControllerConfiguration(
    dependents = {
        @Dependent(type = ConfigMapDependentResource.class),
        @Dependent(type = DeploymentDependentResource.class),
        @Dependent(type = ServiceDependentResource.class),
        @Dependent(type = IngressDependentResource.class,
            reconcilePrecondition = ExposedIngressCondition.class)
    })
public class WebPageManagedDependentsReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, Cleaner<WebPage> {

  public static final String SELECTOR = "managed";

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(WebPage resource,
      Context<WebPage> context, Exception e) {
    return handleError(resource, e);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    final var name = context.getSecondaryResource(ConfigMap.class).orElseThrow()
        .getMetadata().getName();
    webPage.setStatus(createStatus(name));
    return UpdateControl.patchStatus(webPage);
  }

  @Override
  public DeleteControl cleanup(WebPage resource, Context<WebPage> context) {
    return DeleteControl.defaultDelete();
  }
}
