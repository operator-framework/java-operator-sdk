package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

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
            reconcileCondition = ExposedIngressCondition.class)
    })
public class WebPageManagedDependentsReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage> {

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
}
