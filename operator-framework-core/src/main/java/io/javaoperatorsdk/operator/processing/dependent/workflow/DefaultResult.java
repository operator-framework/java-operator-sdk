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

public class DefaultResult<T> implements DetailedCondition.Result<T> {
  private final T result;
  private final boolean success;

  public DefaultResult(boolean success, T result) {
    this.result = result;
    this.success = success;
  }

  @Override
  public T getDetail() {
    return result;
  }

  @Override
  public boolean isSuccess() {
    return success;
  }

  @Override
  public String toString() {
    return asString();
  }
}
