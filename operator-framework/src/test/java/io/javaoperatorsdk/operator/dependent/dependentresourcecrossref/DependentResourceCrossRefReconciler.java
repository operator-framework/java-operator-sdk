/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.dependent.dependentresourcecrossref;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

import static io.javaoperatorsdk.operator.dependent.dependentresourcecrossref.DependentResourceCrossRefReconciler.SECRET_NAME;

@Workflow(
    dependents = {
      @Dependent(
          name = SECRET_NAME,
          type = DependentResourceCrossRefReconciler.SecretDependentResource.class),
      @Dependent(
          type = DependentResourceCrossRefReconciler.ConfigMapDependentResource.class,
          dependsOn = SECRET_NAME)
    })
@ControllerConfiguration
public class DependentResourceCrossRefReconciler
    implements Reconciler<DependentResourceCrossRefResource> {
  private static final Logger log =
      LoggerFactory.getLogger(DependentResourceCrossRefReconciler.class);

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
      Context<DependentResourceCrossRefResource> context,
      Exception e) {
    log.error("Status update on error", e);
    errorHappened = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorHappened() {
    return errorHappened;
  }

  public static class SecretDependentResource
      extends CRUDKubernetesDependentResource<Secret, DependentResourceCrossRefResource> {

    @Override
    protected Secret desired(
        DependentResourceCrossRefResource primary,
        Context<DependentResourceCrossRefResource> context) {
      Secret secret = new Secret();
      secret.setMetadata(
          new ObjectMetaBuilder()
              .withName(primary.getMetadata().getName())
              .withNamespace(primary.getMetadata().getNamespace())
              .build());
      secret.setData(Map.of("key", Base64.getEncoder().encodeToString("secretData".getBytes())));
      return secret;
    }
  }

  public static class ConfigMapDependentResource
      extends CRUDKubernetesDependentResource<ConfigMap, DependentResourceCrossRefResource> {

    @Override
    protected ConfigMap desired(
        DependentResourceCrossRefResource primary,
        Context<DependentResourceCrossRefResource> context) {
      var secret = context.getSecondaryResource(Secret.class);
      if (secret.isEmpty()) {
        throw new IllegalStateException("Secret is empty");
      }
      ConfigMap configMap = new ConfigMap();
      configMap.setMetadata(
          new ObjectMetaBuilder()
              .withName(primary.getMetadata().getName())
              .withNamespace(primary.getMetadata().getNamespace())
              .build());
      configMap.setData(
          Map.of("secretKey", new ArrayList<>(secret.get().getData().keySet()).get(0)));
      return configMap;
    }
  }
}
