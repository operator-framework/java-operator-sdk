package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowBuilder;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.dependentresource.*;

import static io.javaoperatorsdk.operator.sample.Utils.*;

/** Shows how to implement reconciler using standalone dependent resources. */
@ControllerConfiguration(
    informer =
        @Informer(
            labelSelector = WebPageDependentsWorkflowReconciler.DEPENDENT_RESOURCE_LABEL_SELECTOR))
@SuppressWarnings("unused")
public class WebPageDependentsWorkflowReconciler implements Reconciler<WebPage> {

  public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "!low-level";

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;
  private KubernetesDependentResource<Ingress, WebPage> ingressDR;

  private final Workflow<WebPage> workflow;

  public WebPageDependentsWorkflowReconciler(KubernetesClient kubernetesClient) {
    initDependentResources(kubernetesClient);
    workflow =
        new WorkflowBuilder<WebPage>()
            .addDependentResource(configMapDR)
            .addDependentResource(deploymentDR)
            .addDependentResource(serviceDR)
            .addDependentResourceAndConfigure(ingressDR)
            .withReconcilePrecondition(new ExposedIngressCondition())
            .build();
  }

  @Override
  public List<EventSource<?, WebPage>> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceUtils.dependentEventSources(
        context, configMapDR, deploymentDR, serviceDR, ingressDR);
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    workflow.reconcile(webPage, context);

    return UpdateControl.patchStatus(
        createWebPageForStatusUpdate(
            webPage,
            context.getSecondaryResource(ConfigMap.class).orElseThrow().getMetadata().getName()));
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void initDependentResources(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();
    this.deploymentDR = new DeploymentDependentResource();
    this.serviceDR = new ServiceDependentResource();
    this.ingressDR = new IngressDependentResource();

    Arrays.asList(configMapDR, deploymentDR, serviceDR, ingressDR)
        .forEach(
            dr ->
                dr.configureWith(
                    new KubernetesDependentResourceConfigBuilder()
                        .withKubernetesDependentInformerConfig(
                            InformerConfiguration.builder(dr.resourceType())
                                .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR)
                                .build())
                        .build()));
  }
}
