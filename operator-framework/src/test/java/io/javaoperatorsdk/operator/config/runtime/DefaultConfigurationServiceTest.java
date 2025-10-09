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
package io.javaoperatorsdk.operator.config.runtime;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigurationServiceTest {

  public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";
  final DefaultConfigurationService configurationService = new DefaultConfigurationService();

  @Test
  void returnsValuesFromControllerAnnotationFinalizer() {
    final var reconciler = new TestCustomReconciler();
    final var configuration = configurationService.getConfigurationFor(reconciler);
    assertEquals(
        CustomResource.getCRDName(TestCustomResource.class), configuration.getResourceTypeName());
    assertEquals(
        ReconcilerUtils.getDefaultFinalizerName(TestCustomResource.class),
        configuration.getFinalizerName());
    assertEquals(TestCustomResource.class, configuration.getResourceClass());
    assertFalse(configuration.isGenerationAware());
  }

  @Test
  void returnCustomerFinalizerNameIfSet() {
    final var reconciler = new TestCustomFinalizerReconciler();
    final var configuration = configurationService.getConfigurationFor(reconciler);
    assertEquals(CUSTOM_FINALIZER_NAME, configuration.getFinalizerName());
  }

  @Test
  void supportsInnerClassCustomResources() {
    final var reconciler = new TestCustomFinalizerReconciler();
    assertDoesNotThrow(
        () -> {
          configurationService.getConfigurationFor(reconciler).getAssociatedReconcilerClassName();
        });
  }

  @ControllerConfiguration(finalizerName = CUSTOM_FINALIZER_NAME)
  static class TestCustomFinalizerReconciler
      implements Reconciler<TestCustomFinalizerReconciler.InnerCustomResource> {

    @Override
    public UpdateControl<TestCustomFinalizerReconciler.InnerCustomResource> reconcile(
        InnerCustomResource resource, Context<InnerCustomResource> context) {
      return null;
    }

    @Group("test.crd")
    @Version("v1")
    public static class InnerCustomResource extends CustomResource<Void, Void> {}
  }

  @ControllerConfiguration(name = NotAutomaticallyCreated.NAME)
  static class NotAutomaticallyCreated implements Reconciler<TestCustomResource> {

    public static final String NAME = "should-be-logged";

    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context<TestCustomResource> context) {
      return null;
    }
  }

  @ControllerConfiguration(generationAwareEventProcessing = false, name = "test")
  static class TestCustomReconciler implements Reconciler<TestCustomResource> {

    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context<TestCustomResource> context) {
      return null;
    }
  }
}
