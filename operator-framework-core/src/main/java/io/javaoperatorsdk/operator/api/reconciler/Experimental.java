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
package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks experimental features.
 *
 * <p>Experimental features are not yet stable and may change in future releases. Usually based on
 * the feedback of the users.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.PACKAGE})
public @interface Experimental {
  /**
   * Message for experimental features that we intend to keep and maintain, but
   * the API might change usually, based on user feedback.
   * */
  String API_MIGHT_CHANGE = "API might change, usually based on feedback";

  /**
   * Describes why the annotated element is experimental.
   *
   * @return the experimental description.
   */
  String value();
}
