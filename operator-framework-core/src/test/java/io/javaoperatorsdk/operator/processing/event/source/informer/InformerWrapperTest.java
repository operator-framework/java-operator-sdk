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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.InformerClassifier;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The informer behind a wrapper can be shared by event sources of several controllers, while its
 * indexer is a single namespace of index names. The wrapper therefore qualifies index names with
 * the event source that registered them, which only works as long as registration, lookup and
 * removal all apply the same qualification.
 */
class InformerWrapperTest {

  private static final String INDEX_NAME = "my-index";
  private static final String INDEX_KEY = "key";

  @SuppressWarnings("unchecked")
  private final Cache<TestCustomResource> indexer = mock(Cache.class);

  @Test
  void indexNamesAreQualifiedOnRegistrationAndOnLookup() {
    var wrapper = wrapper("controller", "event-source");

    wrapper.addIndexers(Map.of(INDEX_NAME, r -> List.of(INDEX_KEY)));
    wrapper.byIndex(INDEX_NAME, INDEX_KEY);

    var registered = registeredNames();
    // the caller's name is not what reaches the informer...
    assertThat(registered).hasSize(1).allSatisfy(name -> assertThat(name).isNotEqualTo(INDEX_NAME));
    // ...but it is still recognizable in it, so that client side errors stay diagnosable
    assertThat(registered)
        .allSatisfy(
            name ->
                assertThat(name)
                    .contains("controller")
                    .contains("event-source")
                    .endsWith(INDEX_NAME));
    // and lookup asks for exactly the name that was registered
    verify(indexer).byIndex(eq(registered.get(0)), eq(INDEX_KEY));
  }

  @Test
  void twoEventSourcesRegisterTheSameIndexNameUnderDistinctQualifiedNames() {
    // this is what keeps the client from rejecting the second one with an "Indexer conflict", and
    // what keeps either of them from reading the other's index
    wrapper("controller-1", "event-source")
        .addIndexers(Map.of(INDEX_NAME, r -> List.of(INDEX_KEY)));
    wrapper("controller-2", "event-source")
        .addIndexers(Map.of(INDEX_NAME, r -> List.of(INDEX_KEY)));
    wrapper("controller-1", "other-event-source")
        .addIndexers(Map.of(INDEX_NAME, r -> List.of(INDEX_KEY)));

    assertThat(registeredNames()).hasSize(3).doesNotHaveDuplicates();
  }

  @Test
  void removeIndexersDropsExactlyTheNamesThisWrapperRegistered() {
    var wrapper = wrapper("controller", "event-source");
    wrapper.addIndexers(Map.of(INDEX_NAME, r -> List.of(INDEX_KEY)));
    var registered = registeredNames().get(0);

    wrapper.removeIndexers();

    verify(indexer).removeIndexer(registered);
  }

  @Test
  void removeIndexersIsANoopWithoutRegisteredIndexers() {
    wrapper("controller", "event-source").removeIndexers();

    verify(indexer, never()).removeIndexer(any());
  }

  @SuppressWarnings("unchecked")
  private InformerWrapper<TestCustomResource> wrapper(String controller, String eventSource) {
    SharedIndexInformer<TestCustomResource> informer = mock(SharedIndexInformer.class);
    when(informer.getStore()).thenReturn(indexer);
    when(informer.getIndexer()).thenReturn(indexer);
    return new InformerWrapper<>(
        informer,
        "default",
        new InformerClassifier<>(
            null, null, null, "default", TestCustomResource.class, null, null, null, null),
        controller,
        eventSource);
  }

  @SuppressWarnings("unchecked")
  private List<String> registeredNames() {
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(indexer, atLeastOnce()).addIndexers(captor.capture());
    return captor.getAllValues().stream()
        .flatMap(m -> m.keySet().stream())
        .map(String::valueOf)
        .toList();
  }
}
