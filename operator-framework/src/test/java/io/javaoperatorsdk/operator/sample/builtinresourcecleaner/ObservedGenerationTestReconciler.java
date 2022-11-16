package io.javaoperatorsdk.operator.sample.builtinresourcecleaner;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration(labelSelector = "builtintest=true")
public class ObservedGenerationTestReconciler
    implements Reconciler<Service>, Cleaner<Service> {

  private static final Logger log = LoggerFactory.getLogger(ObservedGenerationTestReconciler.class);

  private AtomicInteger reconciled = new AtomicInteger(0);
  private AtomicInteger cleaned = new AtomicInteger(0);

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
