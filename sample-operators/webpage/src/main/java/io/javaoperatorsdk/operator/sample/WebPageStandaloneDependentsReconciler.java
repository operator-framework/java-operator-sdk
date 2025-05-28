package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
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

/** Shows how to implement reconciler using standalone dependent resources and workflows. */
@ControllerConfiguration
public class WebPageStandaloneDependentsReconciler implements Reconciler<WebPage> {

  private final Workflow<WebPage> workflow;

  public WebPageStandaloneDependentsReconciler() {
    // initialize the workflow
    workflow = createDependentResourcesAndWorkflow();
  }

  @Override
  public List<EventSource<?, WebPage>> prepareEventSources(EventSourceContext<WebPage> context) {
    // initializes the dependents' event sources from the given context
    return EventSourceUtils.eventSourcesFromWorkflow(context, workflow);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    // for testing purposes
    simulateErrorIfRequested(webPage);

    // validate the html page and update the status with an error message if it isn't valid
    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    // Explicitly reconcile the dependent resources.
    // Calling the workflow reconciliation explicitly allows control over the workflow customization
    // but also *when* dependents are reconciled (as opposed to before the main reconciler's
    // reconcile method in the managed case).
    // With the default configuration, this will throw an exception if one of the dependents
    // couldn't be properly reconciled
    workflow.reconcile(webPage, context);

    // retrieve the name of the ConfigMap secondary resource to update the status if everything went
    // well
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

  /**
   * Initializes the dependent resources and connect them in the context of a {@link Workflow}
   *
   * @return the {@link Workflow} that will reconcile automatically secondary resources
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Workflow<WebPage> createDependentResourcesAndWorkflow() {
    // create the dependent resources
    var configMapDR = new ConfigMapDependentResource();
    var deploymentDR = new DeploymentDependentResource();
    var serviceDR = new ServiceDependentResource();
    var ingressDR = new IngressDependentResource();

    // configure them with our label selector
    Arrays.asList(configMapDR, deploymentDR, serviceDR, ingressDR)
        .forEach(
            dr ->
                dr.configureWith(
                    new KubernetesDependentResourceConfigBuilder()
                        .withKubernetesDependentInformerConfig(
                            InformerConfiguration.builder(dr.resourceType())
                                .withLabelSelector(SELECTOR + "=true")
                                .build())
                        .build()));

    // connect the dependent resources into a workflow, configuring them as we go
    // Note the method call order is significant and configuration applies to the dependent being
    // configured as defined by the method call order (in this example, the reconcile pre-condition
    // that is added applies to the Ingress dependent)
    return new WorkflowBuilder<WebPage>()
        .addDependentResource(configMapDR)
        .addDependentResource(deploymentDR)
        .addDependentResource(serviceDR)
        .addDependentResourceAndConfigure(ingressDR)
        // prevent the Ingress from being created based on the linked condition (here: only if the
        // `exposed` flag is set in the primary resource), delete the Ingress if it already exists
        // and the condition becomes false
        .withReconcilePrecondition(new ExposedIngressCondition())
        .build();
  }
}
