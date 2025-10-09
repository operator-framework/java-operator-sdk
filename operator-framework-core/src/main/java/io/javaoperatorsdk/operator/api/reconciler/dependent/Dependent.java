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
package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

/**
 * The annotation used to create managed {@link DependentResource} associated with a given {@link
 * io.javaoperatorsdk.operator.api.reconciler.Reconciler}
 */
public @interface Dependent {

  @SuppressWarnings("rawtypes")
  Class<? extends DependentResource> type();

  /**
   * The name of this dependent. This is needed to be able to refer to it when creating dependencies
   * between dependent resources.
   *
   * @return the name if it has been set, {@link
   *     io.javaoperatorsdk.operator.api.reconciler.Constants#NO_VALUE_SET} otherwise
   */
  String name() default NO_VALUE_SET;

  /**
   * The condition (if it exists) that needs to become true before the workflow can further proceed.
   *
   * @return a {@link Condition} implementation, defaulting to the interface itself if no value is
   *     set
   */
  Class<? extends Condition> readyPostcondition() default Condition.class;

  /**
   * The condition (if it exists) that needs to become true before the associated {@link
   * DependentResource} is reconciled. Note that if this condition is set and the condition doesn't
   * hold true, the associated secondary will be deleted.
   *
   * @return a {@link Condition} implementation, defaulting to the interface itself if no value is
   *     set
   */
  Class<? extends Condition> reconcilePrecondition() default Condition.class;

  /**
   * The condition (if it exists) that needs to become true before further reconciliation of
   * dependents can proceed after the secondary resource associated with this dependent is supposed
   * to have been deleted.
   *
   * @return a {@link Condition} implementation, defaulting to the interface itself if no value is
   *     set
   */
  Class<? extends Condition> deletePostcondition() default Condition.class;

  /**
   * A condition that needs to become true for the dependent to even be considered as part of the
   * workflow. This is useful for dependents that represent optional resources on the cluster and
   * might not be present. In this case, a reconcile pre-condition is not enough because in that
   * situation, the associated informer will still be registered. With this activation condition,
   * the associated event source will only be registered if the condition is met. Otherwise, this
   * behaves like a reconcile pre-condition in the sense that dependents, that depend on this one,
   * will only get created if the condition is met and will get deleted if the condition becomes
   * false.
   *
   * <p>As other conditions, this gets evaluated at the beginning of every reconciliation, which
   * means that it allows to react to optional resources becoming available on the cluster as the
   * operator runs. More specifically, this means that the associated event source can get
   * dynamically registered or de-registered during reconciliation.
   */
  Class<? extends Condition> activationCondition() default Condition.class;

  /**
   * The list of named dependents that need to be reconciled before this one can be.
   *
   * @return the list (possibly empty) of named dependents that need to be reconciled before this
   *     one can be
   */
  String[] dependsOn() default {};

  /**
   * Setting here a name of the event source means that dependent resource will use an event source
   * registered with that name. So won't create one. This is helpful if more dependent resources
   * created for the same type, and want to share a common event source.
   *
   * @return event source name (if any) provided by the dependent resource should be used.
   */
  String useEventSourceWithName() default NO_VALUE_SET;
}
