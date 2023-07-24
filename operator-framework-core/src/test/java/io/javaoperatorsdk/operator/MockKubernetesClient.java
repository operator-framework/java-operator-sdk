package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.authorization.v1.ResourceRule;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectRulesReviewStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.V1ApiextensionAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.*;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Indexer;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;

import static io.javaoperatorsdk.operator.LeaderElectionManager.COORDINATION_GROUP;
import static io.javaoperatorsdk.operator.LeaderElectionManager.LEASES_RESOURCE;
import static io.javaoperatorsdk.operator.LeaderElectionManager.UNIVERSAL_VERB;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.when;

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

    Informable<T> informable = mock(Informable.class);
    when(filterable.withLimit(anyLong())).thenReturn(informable);
    when(informable.runnableInformer(anyLong())).thenReturn(informer);

    when(client.resources(clazz)).thenReturn(resources);
    when(client.leaderElector())
        .thenReturn(new LeaderElectorBuilder(client, Executors.newSingleThreadExecutor()));
    var selfSubjectResourceResourceMock = mock(NamespaceableResource.class);
    when(client.resource(any(SelfSubjectRulesReview.class)))
        .thenReturn(selfSubjectResourceResourceMock);
    when(selfSubjectResourceResourceMock.create()).thenReturn(allowSelfSubjectReview());

    final var apiGroupDSL = mock(ApiextensionsAPIGroupDSL.class);
    when(client.apiextensions()).thenReturn(apiGroupDSL);
    final var v1 = mock(V1ApiextensionAPIGroupDSL.class);
    when(apiGroupDSL.v1()).thenReturn(v1);
    final var operation = mock(NonNamespaceOperation.class);
    when(v1.customResourceDefinitions()).thenReturn(operation);
    when(operation.withName(any())).thenReturn(mock(Resource.class));

    final var serialization = new KubernetesSerialization();
    when(client.getKubernetesSerialization()).thenReturn(serialization);

    return client;
  }

  private static Object allowSelfSubjectReview() {
    SelfSubjectRulesReview review = new SelfSubjectRulesReview();
    review.setStatus(new SubjectRulesReviewStatus());
    var resourceRule = new ResourceRule();
    resourceRule.setApiGroups(Arrays.asList(COORDINATION_GROUP));
    resourceRule.setResources(Arrays.asList(LEASES_RESOURCE));
    resourceRule.setVerbs(Arrays.asList(UNIVERSAL_VERB));
    review.getStatus().setResourceRules(Arrays.asList(resourceRule));
    return review;
  }
}
