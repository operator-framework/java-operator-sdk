package io.javaoperatorsdk.operator.sample.eventsourcebyannotation.observedgeneration;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration
public class EventSourceByAnnotationReconciler
    implements Reconciler<EventSourceByAnnotationCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(EventSourceByAnnotationReconciler.class);

  @Override
  public UpdateControl<EventSourceByAnnotationCustomResource> reconcile(
      EventSourceByAnnotationCustomResource resource,
      Context<EventSourceByAnnotationCustomResource> context) {

    return UpdateControl.noUpdate();
  }
}
