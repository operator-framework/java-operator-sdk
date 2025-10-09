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
package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.TestUtils;

import static io.javaoperatorsdk.operator.TestUtils.markForDeletion;
import static org.assertj.core.api.Assertions.assertThat;

class InternalEventFiltersTest {

  public static final String FINALIZER = "finalizer";

  @Test
  void onUpdateMarkedForDeletion() {
    var oldRes = TestUtils.testCustomResource();
    var res = markForDeletion(TestUtils.testCustomResource());
    assertThat(InternalEventFilters.onUpdateMarkedForDeletion().accept(res, oldRes)).isTrue();
  }

  @Test
  void generationAware() {
    var res = TestUtils.testCustomResource1();
    var res2 = TestUtils.testCustomResource1();
    res2.getMetadata().setGeneration(2L);

    assertThat(InternalEventFilters.onUpdateGenerationAware(true).accept(res2, res)).isTrue();
    assertThat(InternalEventFilters.onUpdateGenerationAware(true).accept(res, res)).isFalse();
    assertThat(InternalEventFilters.onUpdateGenerationAware(false).accept(res, res)).isTrue();
  }

  @Test
  void acceptsEventIfNoGenerationOnResource() {
    assertThat(
            InternalEventFilters.onUpdateGenerationAware(true).accept(testService(), testService()))
        .isTrue();
  }

  @Test
  void finalizerCheckedIfConfigured() {
    assertThat(
            InternalEventFilters.onUpdateFinalizerNeededAndApplied(true, FINALIZER)
                .accept(TestUtils.testCustomResource1(), TestUtils.testCustomResource1()))
        .isTrue();

    var res = TestUtils.testCustomResource1();
    res.getMetadata().setFinalizers(List.of(FINALIZER));

    assertThat(
            InternalEventFilters.onUpdateFinalizerNeededAndApplied(true, FINALIZER)
                .accept(res, res))
        .isFalse();
  }

  @Test
  void acceptsIfFinalizerWasJustAdded() {
    var res = TestUtils.testCustomResource1();
    res.getMetadata().setFinalizers(List.of(FINALIZER));

    assertThat(
            InternalEventFilters.onUpdateFinalizerNeededAndApplied(true, "finalizer")
                .accept(res, TestUtils.testCustomResource1()))
        .isTrue();
  }

  @Test
  void dontAcceptIfFinalizerNotUsed() {
    assertThat(
            InternalEventFilters.onUpdateFinalizerNeededAndApplied(false, FINALIZER)
                .accept(TestUtils.testCustomResource1(), TestUtils.testCustomResource1()))
        .isFalse();
  }

  Service testService() {
    var service = new Service();
    service.setMetadata(new ObjectMetaBuilder().withName("test").withNamespace("default").build());
    return service;
  }
}
