package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AbstractSimpleDependentResourceTest {

  UpdatableCache<SampleExternalResource> updatableCacheMock = mock(UpdatableCache.class);
  Supplier<SampleExternalResource> supplierMock = mock(Supplier.class);

  SimpleDependentResource simpleDependentResource =
      new SimpleDependentResource(updatableCacheMock, supplierMock);

  @BeforeEach
  void setup() {
    when(supplierMock.get()).thenReturn(SampleExternalResource.testResource1());
  }

  @Test
  void getsTheResourceFromSupplyIfReconciling() {
    simpleDependentResource = new SimpleDependentResource(supplierMock);

    simpleDependentResource.reconcile(TestUtils.testCustomResource1(), null);

    verify(supplierMock, times(1)).get();
    assertThat(simpleDependentResource.getResource(TestUtils.testCustomResource1()))
        .isPresent()
        .isEqualTo(Optional.of(SampleExternalResource.testResource1()));
  }

  @Test
  void getResourceReadsTheResourceFromCache() {
    simpleDependentResource.getResource(TestUtils.testCustomResource1());

    verify(supplierMock, times(0)).get();
    verify(updatableCacheMock, times(1)).get(any());
  }

  @Test
  void createPutsNewResourceToTheCache() {
    when(supplierMock.get()).thenReturn(null);
    when(updatableCacheMock.get(any())).thenReturn(Optional.empty());

    simpleDependentResource.reconcile(TestUtils.testCustomResource1(), null);

    verify(updatableCacheMock, times(1)).put(any(), any());
  }

  @Test
  void updatePutsNewResourceToCache() {
    var actual = SampleExternalResource.testResource1();
    actual.setValue("changedValue");
    when(supplierMock.get()).thenReturn(actual);
    when(updatableCacheMock.get(any())).thenReturn(Optional.of(actual));

    simpleDependentResource.reconcile(TestUtils.testCustomResource1(), null);

    verify(updatableCacheMock, times(1))
        .put(ResourceID.fromResource(TestUtils.testCustomResource1()), actual);

    verify(updatableCacheMock, times(1))
        .put(
            ResourceID.fromResource(TestUtils.testCustomResource1()),
            SampleExternalResource.testResource1());
  }

  @Test
  void deleteRemovesResourceFromCache() {
    simpleDependentResource.delete(TestUtils.testCustomResource1(), null);
    verify(updatableCacheMock, times(1)).remove(any());
  }

  private static class SimpleDependentResource
      extends AbstractSimpleDependentResource<SampleExternalResource, TestCustomResource>
      implements Creator<SampleExternalResource, TestCustomResource>,
      Updater<SampleExternalResource, TestCustomResource>,
      Deleter<TestCustomResource> {

    private final Supplier<SampleExternalResource> supplier;

    public SimpleDependentResource(Supplier<SampleExternalResource> supplier) {
      this.supplier = supplier;
    }

    public SimpleDependentResource(
        UpdatableCache<SampleExternalResource> cache, Supplier<SampleExternalResource> supplier) {
      super(cache);
      this.supplier = supplier;
    }

    @Override
    public Optional<SampleExternalResource> fetchResource(HasMetadata primaryResource) {
      return Optional.ofNullable(supplier.get());
    }

    @Override
    protected void deleteResource(TestCustomResource primary,
        Context<TestCustomResource> context) {}

    @Override
    public SampleExternalResource create(
        SampleExternalResource desired, TestCustomResource primary,
        Context<TestCustomResource> context) {
      return SampleExternalResource.testResource1();
    }

    @Override
    public SampleExternalResource update(
        SampleExternalResource actual,
        SampleExternalResource desired,
        TestCustomResource primary,
        Context<TestCustomResource> context) {
      return SampleExternalResource.testResource1();
    }

    @Override
    protected SampleExternalResource desired(TestCustomResource primary,
        Context<TestCustomResource> context) {
      return SampleExternalResource.testResource1();
    }

    @Override
    public Class<SampleExternalResource> resourceType() {
      return SampleExternalResource.class;
    }
  }
}
