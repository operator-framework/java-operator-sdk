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
package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class StatefulSetDesiredSanitizerDependentResource
    extends CRUDKubernetesDependentResource<
        StatefulSet, StatefulSetDesiredSanitizerCustomResource> {

  public static volatile Boolean nonMatchedAtLeastOnce;

  @Override
  protected StatefulSet desired(
      StatefulSetDesiredSanitizerCustomResource primary,
      Context<StatefulSetDesiredSanitizerCustomResource> context) {
    var template =
        ReconcilerUtilsInternal.loadYaml(
            StatefulSet.class, getClass(), "/io/javaoperatorsdk/operator/statefulset.yaml");
    template.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    return template;
  }

  @Override
  public Result<StatefulSet> match(
      StatefulSet actualResource,
      StatefulSetDesiredSanitizerCustomResource primary,
      Context<StatefulSetDesiredSanitizerCustomResource> context) {
    var res = super.match(actualResource, primary, context);
    if (!res.matched()) {
      nonMatchedAtLeastOnce = true;
    } else if (nonMatchedAtLeastOnce == null) {
      nonMatchedAtLeastOnce = false;
    }
    return res;
  }
}
