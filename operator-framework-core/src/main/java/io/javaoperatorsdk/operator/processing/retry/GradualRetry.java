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
package io.javaoperatorsdk.operator.processing.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GradualRetry {

  int DEFAULT_MAX_ATTEMPTS = 5;
  long DEFAULT_INITIAL_INTERVAL = 2000L;
  double DEFAULT_MULTIPLIER = 1.5D;

  long DEFAULT_MAX_INTERVAL =
      (long)
          (GradualRetry.DEFAULT_INITIAL_INTERVAL
              * Math.pow(GradualRetry.DEFAULT_MULTIPLIER, GradualRetry.DEFAULT_MAX_ATTEMPTS));

  long UNSET_VALUE = Long.MAX_VALUE;

  int maxAttempts() default DEFAULT_MAX_ATTEMPTS;

  long initialInterval() default DEFAULT_INITIAL_INTERVAL;

  double intervalMultiplier() default DEFAULT_MULTIPLIER;

  long maxInterval() default UNSET_VALUE;
}
