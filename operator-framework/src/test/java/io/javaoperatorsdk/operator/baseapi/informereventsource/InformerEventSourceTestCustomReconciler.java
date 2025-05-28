package io.javaoperatorsdk.operator.baseapi.informereventsource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

/**
 * Copies the config map value from spec into status. The main purpose is to test and demonstrate
 * sample usage of InformerEventSource
 */
@ControllerConfiguration
public class InformerEventSourceTestCustomReconciler
    implements Reconciler<InformerEventSourceTestCustomResource> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(InformerEventSourceTestCustomReconciler.class);

  public static final String RELATED_RESOURCE_NAME = "relatedResourceName";
  public static final String RELATED_RESOURCE_TYPE = "relatedResourceType";
  public static final String TARGET_CONFIG_MAP_KEY = "targetStatus";
  public static final String MISSING_CONFIG_MAP = "Missing Config Map";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public List<EventSource<?, InformerEventSourceTestCustomResource>> prepareEventSources(
      EventSourceContext<InformerEventSourceTestCustomResource> context) {

    InformerEventSourceConfiguration<ConfigMap> config =
        InformerEventSourceConfiguration.from(
                ConfigMap.class, InformerEventSourceTestCustomResource.class)
            .withSecondaryToPrimaryMapper(
                Mappers.fromAnnotation(
                    RELATED_RESOURCE_NAME,
                    RELATED_RESOURCE_TYPE,
                    InformerEventSourceTestCustomResource.class))
            .build();

    return List.of(new InformerEventSource<>(config, context));
  }

  @Override
  public UpdateControl<InformerEventSourceTestCustomResource> reconcile(
      InformerEventSourceTestCustomResource resource,
      Context<InformerEventSourceTestCustomResource> context) {
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

    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
