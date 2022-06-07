package io.javaoperatorsdk.operator.sample.dependentresourcecrossref;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

import static io.javaoperatorsdk.operator.sample.dependentresourcecrossref.DependentResourceCrossRefReconciler.SECRET_NAME;

@ControllerConfiguration(dependents = {
    @Dependent(name = SECRET_NAME,
        type = DependentResourceCrossRefReconciler.SecretDependentResource.class),
    @Dependent(type = DependentResourceCrossRefReconciler.ConfigMapDependentResource.class,
        dependsOn = SECRET_NAME)})
public class DependentResourceCrossRefReconciler
    implements Reconciler<DependentResourceCrossRefResource>,
    ErrorStatusHandler<DependentResourceCrossRefResource> {

  public static final String SECRET_NAME = "secret";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private volatile boolean errorHappened = false;

  @Override
  public UpdateControl<DependentResourceCrossRefResource> reconcile(
      DependentResourceCrossRefResource resource,
      Context<DependentResourceCrossRefResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public ErrorStatusUpdateControl<DependentResourceCrossRefResource> updateErrorStatus(
      DependentResourceCrossRefResource resource,
      Context<DependentResourceCrossRefResource> context, Exception e) {
    errorHappened = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorHappened() {
    return errorHappened;
  }

  public static class SecretDependentResource extends
      CRUDKubernetesDependentResource<Secret, DependentResourceCrossRefResource> {

    public SecretDependentResource() {
      super(Secret.class);
    }

    @Override
    protected Secret desired(DependentResourceCrossRefResource primary,
        Context<DependentResourceCrossRefResource> context) {
      Secret secret = new Secret();
      secret.setMetadata(new ObjectMetaBuilder()
          .withName(primary.getMetadata().getName())
          .withNamespace(primary.getMetadata().getNamespace())
          .build());
      secret.setData(Map.of("key", Base64.getEncoder().encodeToString("secretData".getBytes())));
      return secret;
    }
  }

  public static class ConfigMapDependentResource extends
      CRUDKubernetesDependentResource<ConfigMap, DependentResourceCrossRefResource> {

    public ConfigMapDependentResource() {
      super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(DependentResourceCrossRefResource primary,
        Context<DependentResourceCrossRefResource> context) {
      var secret = context.getSecondaryResource(Secret.class);
      if (secret.isEmpty()) {
        throw new IllegalStateException("Secret is empty");
      }
      ConfigMap configMap = new ConfigMap();
      configMap.setMetadata(new ObjectMetaBuilder()
          .withName(primary.getMetadata().getName())
          .withNamespace(primary.getMetadata().getNamespace())
          .build());
      configMap
          .setData(Map.of("secretKey", new ArrayList<>(secret.get().getData().keySet()).get(0)));
      return configMap;
    }
  }



}
