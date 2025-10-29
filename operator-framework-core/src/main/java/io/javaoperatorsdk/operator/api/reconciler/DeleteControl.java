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

public class DeleteControl extends BaseControl<DeleteControl> {

  private final boolean removeFinalizer;

  private DeleteControl(boolean removeFinalizer) {
    this.removeFinalizer = removeFinalizer;
  }

  /**
   * @return delete control that will remove finalizer.
   */
  public static DeleteControl defaultDelete() {
    return new DeleteControl(true);
  }

  /**
   * In some corner cases it might take some time for secondary resources to be cleaned up. In such
   * situation, the reconciler shouldn't remove the finalizer until it is safe for the primary
   * resource to be deleted (because, e.g., secondary resources being removed might need its
   * information to proceed correctly). Using this method will instruct the reconciler to leave the
   * finalizer in place, presumably to wait for a new reconciliation triggered by event sources on
   * the secondary resources (e.g. when they are effectively deleted/cleaned-up) to check whether it
   * is now safe to delete the primary resource and therefore, remove its finalizer by returning
   * {@link #defaultDelete()} then.
   *
   * @return delete control that will not remove finalizer.
   */
  public static DeleteControl noFinalizerRemoval() {
    return new DeleteControl(false);
  }

  public boolean isRemoveFinalizer() {
    return removeFinalizer;
  }

  @Override
  public DeleteControl rescheduleAfter(long delay) {
    if (removeFinalizer) {
      throw new IllegalStateException("Cannot reschedule cleanup if removing finalizer");
    }
    return super.rescheduleAfter(delay);
  }
}
