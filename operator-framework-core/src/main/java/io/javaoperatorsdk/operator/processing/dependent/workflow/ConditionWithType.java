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
package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

class ConditionWithType<R, P extends HasMetadata, T> implements DetailedCondition<R, P, T> {
  private final Condition<R, P> condition;
  private final Type type;

  ConditionWithType(Condition<R, P> condition, Type type) {
    this.condition = condition;
    this.type = type;
  }

  public Type type() {
    return type;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Result<T> detailedIsMet(
      DependentResource<R, P> dependentResource, P primary, Context<P> context) {
    if (condition instanceof DetailedCondition detailedCondition) {
      return detailedCondition.detailedIsMet(dependentResource, primary, context);
    } else {
      return Result.withoutResult(condition.isMet(dependentResource, primary, context));
    }
  }
}
