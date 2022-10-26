package io.javaoperatorsdk.operator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.V1ApiextensionAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.*;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Indexer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

public class MockKubernetesClient {

  public static <T extends HasMetadata> KubernetesClient client(Class<T> clazz) {
    return client(clazz, null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T extends HasMetadata> KubernetesClient client(Class<T> clazz,
      Consumer<Void> informerRunBehavior) {
    final var client = mock(KubernetesClient.class);
    MixedOperation<T, KubernetesResourceList<T>, Resource<T>> resources =
        mock(MixedOperation.class);
    NonNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> nonNamespaceOperation =
        mock(NonNamespaceOperation.class);
    AnyNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> inAnyNamespace = mock(
        AnyNamespaceOperation.class);
    FilterWatchListDeletable<T, KubernetesResourceList<T>, Resource<T>> filterable =
        mock(FilterWatchListDeletable.class);
    when(resources.inNamespace(anyString())).thenReturn(nonNamespaceOperation);
    when(nonNamespaceOperation.withLabelSelector(nullable(String.class))).thenReturn(filterable);
    when(resources.inAnyNamespace()).thenReturn(inAnyNamespace);
    when(inAnyNamespace.withLabelSelector(nullable(String.class))).thenReturn(filterable);
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    CompletableFuture<Void> informerStartRes = new CompletableFuture<>();
    informerStartRes.complete(null);
    when(informer.start()).thenReturn(informerStartRes);
    CompletableFuture<Void> stopped = new CompletableFuture<>();
    when(informer.stopped()).thenReturn(stopped);
    when(informer.getApiTypeClass()).thenReturn(clazz);
    if (informerRunBehavior != null) {
      doAnswer(invocation -> {
        try {
          informerRunBehavior.accept(null);
        } catch (Exception e) {
          stopped.completeExceptionally(e);
        }
        return stopped;
      }).when(informer).start();
    }
    doAnswer(invocation -> null).when(informer).stop();
    Indexer mockIndexer = mock(Indexer.class);
    when(informer.getIndexer()).thenReturn(mockIndexer);
    when(filterable.runnableInformer(anyLong())).thenReturn(informer);
    when(client.resources(clazz)).thenReturn(resources);
    when(client.leaderElector())
        .thenReturn(new LeaderElectorBuilder(client, Executors.newSingleThreadExecutor()));

    final var apiGroupDSL = mock(ApiextensionsAPIGroupDSL.class);
    when(client.apiextensions()).thenReturn(apiGroupDSL);
    final var v1 = mock(V1ApiextensionAPIGroupDSL.class);
    when(apiGroupDSL.v1()).thenReturn(v1);
    final var operation = mock(NonNamespaceOperation.class);
    when(v1.customResourceDefinitions()).thenReturn(operation);
    when(operation.withName(any())).thenReturn(mock(Resource.class));

    return client;
  }
}
