package io.javaoperatorsdk.operator.baseapi.startsecondaryaccess;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.baseapi.startsecondaryaccess.StartupSecondaryAccessIT.SECONDARY_NUMBER;

@ControllerConfiguration
public class StartupSecondaryAccessReconciler
    implements Reconciler<StartupSecondaryAccessCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(StartupSecondaryAccessReconciler.class);

  public static final String LABEL_KEY = "app";
  public static final String LABEL_VALUE = "secondary-test";

  private InformerEventSource<ConfigMap, StartupSecondaryAccessCustomResource> cmInformer;

  private boolean secondaryAndCacheSameAmount = true;
  private boolean reconciled = false;

  @Override
  public UpdateControl<StartupSecondaryAccessCustomResource> reconcile(
      StartupSecondaryAccessCustomResource resource,
      Context<StartupSecondaryAccessCustomResource> context) {

    var secondary = context.getSecondaryResources(ConfigMap.class);
    var cached = cmInformer.list().toList();

    log.info(
        "Secondary number: {}, cached: {}, expected: {}",
        secondary.size(),
        cached.size(),
        SECONDARY_NUMBER);

    if (secondary.size() != cached.size()) {
      secondaryAndCacheSameAmount = false;
    }
    reconciled = true;
    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, StartupSecondaryAccessCustomResource>> prepareEventSources(
      EventSourceContext<StartupSecondaryAccessCustomResource> context) {
    cmInformer =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, StartupSecondaryAccessCustomResource.class)
                .withLabelSelector(LABEL_KEY + "=" + LABEL_VALUE)
                .build(),
            context);
    return List.of(cmInformer);
  }

  public boolean isSecondaryAndCacheSameAmount() {
    return secondaryAndCacheSameAmount;
  }

  public boolean isReconciled() {
    return reconciled;
  }
}
