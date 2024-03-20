package io.javaoperatorsdk.operator.sample.builtinresourcecleaner;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration(labelSelector = "builtintest=true")
public class ObservedGenerationTestReconciler
    implements Reconciler<Service>, Cleaner<Service> {

  private static final Logger log = LoggerFactory.getLogger(ObservedGenerationTestReconciler.class);

  private final AtomicInteger reconciled = new AtomicInteger(0);
  private final AtomicInteger cleaned = new AtomicInteger(0);

  @Override
  public UpdateControl<Service> reconcile(
      Service resource,
      Context<Service> context) {
    reconciled.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(Service resource, Context<Service> context) {
    cleaned.addAndGet(1);
    return DeleteControl.defaultDelete();
  }

  public int getReconcileCount() {
    return reconciled.get();
  }

  public int getCleanCount() {
    return cleaned.get();
  }
}
