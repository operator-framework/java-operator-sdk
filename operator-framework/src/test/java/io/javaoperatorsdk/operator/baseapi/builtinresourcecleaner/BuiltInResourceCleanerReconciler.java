package io.javaoperatorsdk.operator.baseapi.builtinresourcecleaner;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration(informer = @Informer(labelSelector = "builtintest=true"))
public class BuiltInResourceCleanerReconciler implements Reconciler<Service>, Cleaner<Service> {

  private final AtomicInteger reconciled = new AtomicInteger(0);
  private final AtomicInteger cleaned = new AtomicInteger(0);

  @Override
  public UpdateControl<Service> reconcile(Service resource, Context<Service> context) {
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
