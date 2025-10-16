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
package io.javaoperatorsdk.operator.processing.event.source.inbound;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.ResourceKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachingInboundEventSourceTest
    extends AbstractEventSourceTestBase<
        CachingInboundEventSource<SampleExternalResource, TestCustomResource, String>,
        EventHandler> {

  @SuppressWarnings("unchecked")
  private final CachingInboundEventSource.ResourceFetcher<
          SampleExternalResource, TestCustomResource>
      supplier = mock(CachingInboundEventSource.ResourceFetcher.class);

  private final TestCustomResource testCustomResource = TestUtils.testCustomResource();
  private final ResourceKeyMapper<SampleExternalResource, String> resourceKeyMapper =
      r -> r.getName() + "#" + r.getValue();

  @BeforeEach
  public void setup() {
    when(supplier.fetchResources(any())).thenReturn(Set.of(SampleExternalResource.testResource1()));

    setUpSource(
        new CachingInboundEventSource<>(supplier, SampleExternalResource.class, resourceKeyMapper));
  }

  @Test
  void getSecondaryResourceFromCacheOrSupplier() throws InterruptedException {
    when(supplier.fetchResources(any())).thenReturn(Set.of(SampleExternalResource.testResource1()));

    var value = source.getSecondaryResources(testCustomResource);

    verify(supplier, times(1)).fetchResources(eq(testCustomResource));
    verify(eventHandler, never()).handleEvent(any());
    assertThat(value).hasSize(1);

    value = source.getSecondaryResources(testCustomResource);

    assertThat(value).hasSize(1);
    verify(supplier, times(1)).fetchResources(eq(testCustomResource));
    verify(eventHandler, never()).handleEvent(any());

    source.handleResourceEvent(
        ResourceID.fromResource(testCustomResource),
        Set.of(SampleExternalResource.testResource1(), SampleExternalResource.testResource2()));

    verify(supplier, times(1)).fetchResources(eq(testCustomResource));
    value = source.getSecondaryResources(testCustomResource);
    assertThat(value).hasSize(2);
  }

  @Test
  void propagateEventOnDeletedResource() throws InterruptedException {
    source.handleResourceEvent(
        ResourceID.fromResource(testCustomResource), SampleExternalResource.testResource1());
    source.handleResourceDeleteEvent(
        ResourceID.fromResource(testCustomResource),
        resourceKeyMapper.keyFor(SampleExternalResource.testResource1()));
    source.handleResourceDeleteEvent(
        ResourceID.fromResource(testCustomResource),
        resourceKeyMapper.keyFor(SampleExternalResource.testResource2()));

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void propagateEventOnUpdateResources() throws InterruptedException {
    source.handleResourceEvent(
        ResourceID.fromResource(testCustomResource),
        Set.of(SampleExternalResource.testResource1(), SampleExternalResource.testResource2()));

    verify(eventHandler, times(1)).handleEvent(any());
  }
}
