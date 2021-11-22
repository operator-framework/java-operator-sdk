package io.javaoperatorsdk.operator.sample.observedgeneration;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class ObservedGenerationTestReconciler
    implements Reconciler<ObservedGenerationTestCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(ObservedGenerationTestReconciler.class);

  @Override
  public UpdateControl<ObservedGenerationTestCustomResource> reconcile(
          ObservedGenerationTestCustomResource resource, Context context) {
    log.info("Reconcile ObservedGenerationTestCustomResource: {}",resource.getMetadata().getName());
    return UpdateControl.updateStatusSubResource(resource);
  }
}
