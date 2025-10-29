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
package io.javaoperatorsdk.operator.dependent.dependentssa;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class SSAConfigMapDependent
    extends CRUDKubernetesDependentResource<ConfigMap, DependentSSACustomResource> {

  public static AtomicInteger NUMBER_OF_UPDATES = new AtomicInteger(0);

  public static final String DATA_KEY = "key1";

  @Override
  protected ConfigMap desired(
      DependentSSACustomResource primary, Context<DependentSSACustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(primary.getMetadata().getName())
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .withData(Map.of(DATA_KEY, primary.getSpec().getValue()))
        .build();
  }

  @Override
  public ConfigMap update(
      ConfigMap actual,
      ConfigMap desired,
      DependentSSACustomResource primary,
      Context<DependentSSACustomResource> context) {
    NUMBER_OF_UPDATES.incrementAndGet();
    return super.update(actual, desired, primary, context);
  }
}
