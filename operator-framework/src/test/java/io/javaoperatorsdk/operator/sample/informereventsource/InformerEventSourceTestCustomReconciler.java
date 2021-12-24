package io.javaoperatorsdk.operator.sample.informereventsource;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.Dependent;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomReconciler.ConfigMapDR;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

/**
 * Copies the config map value from spec into status. The main purpose is to test and demonstrate
 * sample usage of InformerEventSource
 */
@ControllerConfiguration(finalizerName = NO_FINALIZER,
    dependents = @Dependent(resourceType = ConfigMap.class, type = ConfigMapDR.class))
public class InformerEventSourceTestCustomReconciler
    implements Reconciler<InformerEventSourceTestCustomResource> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(InformerEventSourceTestCustomReconciler.class);

  public static final String RELATED_RESOURCE_NAME = "relatedResourceName";
  public static final String TARGET_CONFIG_MAP_KEY = "targetStatus";
  public static final String MISSING_CONFIG_MAP = "Missing Config Map";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public static class ConfigMapDR
      implements DependentResource<ConfigMap, InformerEventSourceTestCustomResource>,
      PrimaryResourcesRetriever<ConfigMap> {
    private final PrimaryResourcesRetriever<ConfigMap> retriever = Mappers.fromAnnotation(
        RELATED_RESOURCE_NAME);

    @Override
    public Set<ResourceID> associatedPrimaryResources(ConfigMap dependentResource) {
      return retriever.associatedPrimaryResources(dependentResource);
    }
  }

  @Override
  public UpdateControl<InformerEventSourceTestCustomResource> reconcile(
      InformerEventSourceTestCustomResource resource,
      Context context) {
    numberOfExecutions.incrementAndGet();

    resource.setStatus(new InformerEventSourceTestCustomResourceStatus());
    // Reading the config map from the informer not from the API
    // name of the config map same as custom resource for sake of simplicity
    Optional<ConfigMap> configMap = context.getSecondaryResource(ConfigMap.class);
    if (configMap.isEmpty()) {
      resource.getStatus().setConfigMapValue(MISSING_CONFIG_MAP);
    } else {
      String targetStatus = configMap.get().getData().get(TARGET_CONFIG_MAP_KEY);
      LOGGER.debug("Setting target status for CR: {}", targetStatus);
      resource.getStatus().setConfigMapValue(targetStatus);
    }

    return UpdateControl.updateStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
