package io.javaoperatorsdk.operator.sample;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.*;

import static io.javaoperatorsdk.operator.sample.Utils.*;

/**
 * Shows how to implement reconciler using standalone dependent resources.
 */
@ControllerConfiguration(
    labelSelector = WebPageDependentsWorkflowReconciler.DEPENDENT_RESOURCE_LABEL_SELECTOR)
@SuppressWarnings("unused")
public class WebPageDependentsWorkflowReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "!low-level";

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;
  private KubernetesDependentResource<Ingress, WebPage> ingressDR;
  @SuppressWarnings("rawtypes")
  private final static KubernetesDependentResourceConfig config =
      new KubernetesDependentResourceConfigBuilder()
          .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR).build();

  private final Workflow<WebPage> workflow;

  public WebPageDependentsWorkflowReconciler(KubernetesClient kubernetesClient) {
    initDependentResources(kubernetesClient);
    workflow = new WorkflowBuilder<WebPage>()
        .addDependentResource(configMapDR)
        .addDependentResource(deploymentDR)
        .addDependentResource(serviceDR)
        .addDependentResource(ingressDR).withReconcilePrecondition(new ExposedIngressCondition())
        .build();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceInitializer.nameEventSourcesFromDependentResource(context, configMapDR,
        deploymentDR, serviceDR,
        ingressDR);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

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

  private void initDependentResources(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();
    configureDR(configMapDR, client);
    this.deploymentDR = new DeploymentDependentResource();
    configureDR(deploymentDR, client);
    this.serviceDR = new ServiceDependentResource();
    configureDR(serviceDR, client);
    this.ingressDR = new IngressDependentResource();
    configureDR(ingressDR, client);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void configureDR(KubernetesDependentResource dr, KubernetesClient client) {
    dr.configureWith(config);
    dr.setKubernetesClient(client);
  }
}
