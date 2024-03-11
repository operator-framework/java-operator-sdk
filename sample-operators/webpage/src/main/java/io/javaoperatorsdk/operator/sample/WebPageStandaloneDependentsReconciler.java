package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.ConfigMapDependentResource;
import io.javaoperatorsdk.operator.sample.dependentresource.DeploymentDependentResource;
import io.javaoperatorsdk.operator.sample.dependentresource.ExposedIngressCondition;
import io.javaoperatorsdk.operator.sample.dependentresource.IngressDependentResource;
import io.javaoperatorsdk.operator.sample.dependentresource.ServiceDependentResource;

import static io.javaoperatorsdk.operator.sample.Utils.*;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

/**
 * Shows how to implement reconciler using standalone dependent resources.
 */
@ControllerConfiguration
public class WebPageStandaloneDependentsReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage> {

  private final Workflow<WebPage> workflow;

  public WebPageStandaloneDependentsReconciler() {
    workflow = createDependentResourcesAndWorkflow();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceUtils.eventSourcesFromWorkflow(context, workflow);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    workflow.reconcile(webPage, context);

    webPage.setStatus(
        createStatus(
            context.getSecondaryResource(ConfigMap.class).orElseThrow().getMetadata().getName()));
    return UpdateControl.patchStatus(webPage);
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Workflow<WebPage> createDependentResourcesAndWorkflow() {
    var configMapDR = new ConfigMapDependentResource();
    var deploymentDR = new DeploymentDependentResource();
    var serviceDR = new ServiceDependentResource();
    var ingressDR = new IngressDependentResource();

    Arrays.asList(configMapDR, deploymentDR, serviceDR, ingressDR)
        .forEach(dr -> dr.configureWith(new KubernetesDependentResourceConfigBuilder()
            .withLabelSelector(SELECTOR + "=true").build()));

    return new WorkflowBuilder<WebPage>()
        .addDependentResource(configMapDR)
        .addDependentResource(deploymentDR)
        .addDependentResource(serviceDR)
        .addDependentResource(ingressDR)
        .withReconcilePrecondition(new ExposedIngressCondition())
        .build();
  }
}
