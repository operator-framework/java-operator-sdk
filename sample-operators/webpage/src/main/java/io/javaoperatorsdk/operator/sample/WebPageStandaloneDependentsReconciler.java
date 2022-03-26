package io.javaoperatorsdk.operator.sample;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static io.javaoperatorsdk.operator.sample.Utils.createStatus;
import static io.javaoperatorsdk.operator.sample.Utils.handleError;
import static io.javaoperatorsdk.operator.sample.Utils.simulateErrorIfRequested;

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

  private final Map<String, KubernetesDependentResource<?, WebPage>> dependentResources;

  public WebPageStandaloneDependentsReconciler(KubernetesClient kubernetesClient) {
    dependentResources = Map.of(
        "configmap", new ConfigMapDependentResource(),
        "deployment", new DeploymentDependentResource(),
        "service", new ServiceDependentResource());
    final var config = new KubernetesDependentResourceConfig()
        .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR);
    dependentResources.values().forEach(dr -> {
      dr.setKubernetesClient(kubernetesClient);
      dr.configureWith(config);
    });
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return dependentResources.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
      throws Exception {
    simulateErrorIfRequested(webPage);

    dependentResources.values().forEach(dr -> dr.reconcile(webPage, context));

    webPage.setStatus(createStatus(getConfigMapName(webPage)));
    return UpdateControl.updateStatus(webPage);
  }

  private String getConfigMapName(WebPage webPage) {
    return dependent("configmap").getResource(webPage).orElseThrow().getMetadata().getName();
  }

  @Override
  public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
      WebPage resource, Context<WebPage> retryInfo, Exception e) {
    return handleError(resource, e);
  }

  private KubernetesDependentResource<? extends HasMetadata, WebPage> dependent(String name) {
    return dependentResources.get(name);
  }
}
