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
package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapDeletePostCondition
    implements Condition<ConfigMap, WorkflowAllFeatureCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapDeletePostCondition.class);

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, WorkflowAllFeatureCustomResource> dependentResource,
      WorkflowAllFeatureCustomResource primary,
      Context<WorkflowAllFeatureCustomResource> context) {

    var configMapDeleted = dependentResource.getSecondaryResource(primary, context).isEmpty();
    log.debug("Config Map Deleted: {}", configMapDeleted);
    return configMapDeleted;
  }
}
