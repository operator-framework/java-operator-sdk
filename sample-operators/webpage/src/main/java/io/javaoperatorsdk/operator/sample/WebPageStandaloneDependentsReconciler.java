package io.javaoperatorsdk.operator.sample;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.sample.Utils.*;

/**
 * Shows how to implement reconciler using standalone dependent resources.
 */
@ControllerConfiguration(
    labelSelector = WebPageStandaloneDependentsReconciler.DEPENDENT_RESOURCE_LABEL_SELECTOR)
public class WebPageStandaloneDependentsReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "!low-level";
  private static final Logger log =
      LoggerFactory.getLogger(WebPageStandaloneDependentsReconciler.class);

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;
  private KubernetesDependentResource<Ingress, WebPage> ingressDR;

  public WebPageStandaloneDependentsReconciler(KubernetesClient kubernetesClient) {
    createDependentResources(kubernetesClient);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    InformerEventSource<ConfigMap, WebPage> configMapEventSource = new InformerEventSource<>(
        InformerConfiguration.from(ConfigMap.class, context)
            .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR)
            .build(),
        context);
    configMapDR.configureWith(configMapEventSource);
    InformerEventSource<Deployment, WebPage> deploymentEventSource = new InformerEventSource<>(
        InformerConfiguration.from(Deployment.class, context)
            .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR)
            .build(),
        context);
    deploymentDR.configureWith(deploymentEventSource);
    InformerEventSource<Service, WebPage> serviceEventSource = new InformerEventSource<>(
        InformerConfiguration.from(Service.class, context)
            .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR)
            .build(),
        context);
    serviceDR.configureWith(serviceEventSource);
    InformerEventSource<Ingress, WebPage> ingressEventSource = new InformerEventSource<>(
        InformerConfiguration.from(Ingress.class, context)
            .withLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR)
            .build(),
        context);
    ingressDR.configureWith(ingressEventSource);


    return EventSourceInitializer.nameEventSources(configMapDR.initEventSource(context),
        deploymentDR.initEventSource(context), serviceDR.initEventSource(context),
        ingressDR.initEventSource(context));
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    if (!isValidHtml(webPage)) {
      return UpdateControl.patchStatus(setInvalidHtmlErrorMessage(webPage));
    }

    Arrays.asList(configMapDR, deploymentDR, serviceDR)
        .forEach(dr -> dr.reconcile(webPage, context));

    if (Boolean.TRUE.equals(webPage.getSpec().getExposed())) {
      ingressDR.reconcile(webPage, context);
    } else {
      ingressDR.delete(webPage, context);
    }

    webPage.setStatus(
        createStatus(
            configMapDR.getSecondaryResource(webPage).orElseThrow().getMetadata().getName()));
    return UpdateControl.patchStatus(webPage);
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  private void createDependentResources(KubernetesClient client) {
    this.configMapDR = new ConfigMapDependentResource();
    this.deploymentDR = new DeploymentDependentResource();
    this.serviceDR = new ServiceDependentResource();
    this.ingressDR = new IngressDependentResource();

    Arrays.asList(configMapDR, deploymentDR, serviceDR, ingressDR).forEach(dr -> {
      dr.setKubernetesClient(client);
    });
  }



}
