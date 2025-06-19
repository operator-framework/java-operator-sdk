package io.javaoperatorsdk.operator.baseapi.fieldselector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.Field;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(
    informer =
        @Informer(
            withFields =
                @Field(field = "type", value = FieldSelectorTestReconciler.MY_SECRET_TYPE)))
public class FieldSelectorTestReconciler implements Reconciler<Secret>, TestExecutionInfoProvider {

  public static final String MY_SECRET_TYPE = "my-secret-type";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private Set<String> reconciledSecrets = Collections.synchronizedSet(new HashSet<>());

  @Override
  public UpdateControl<Secret> reconcile(Secret resource, Context<Secret> context) {
    reconciledSecrets.add(resource.getMetadata().getName());
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public Set<String> getReconciledSecrets() {
    return reconciledSecrets;
  }
}
