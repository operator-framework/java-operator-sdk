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

public abstract class AbstractUpdateControl<T extends BaseControl<T>> extends BaseControl<T> {

  private boolean filterPatchEvent = true;

  /**
   * The event from resource primary updates are filtered by default, thus does not trigger the
   * reconciliation. Setting this to false will turn the filtering off.
   *
   * @since 5.3.0
   */
  public T filterPatchEvent(boolean filterPatchEvent) {
    this.filterPatchEvent = filterPatchEvent;
    return (T) this;
  }

  public boolean isFilterPatchEvent() {
    return filterPatchEvent;
  }
}
