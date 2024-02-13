package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public class CRDPresentActivationCondition implements Condition<HasMetadata, HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(CRDPresentActivationCondition.class);

  private static final Map<GroupVersionKind, Boolean> crdPresenceCache = new ConcurrentHashMap<>();
  private static final Map<GroupVersionKind, LocalDateTime> crdPresenceLastRefresh =
      new ConcurrentHashMap<>();

  // todo configurable, enhanced behavior (exp backoff etc)
  private int cacheRefreshInterval = 5000;

  @Override
  public boolean isMet(DependentResource<HasMetadata, HasMetadata> dependentResource,
      HasMetadata primary, Context<HasMetadata> context) {

    var resourceClass = dependentResource.resourceType();
    var apiVersion = HasMetadata.getApiVersion(resourceClass);
    var kind = HasMetadata.getKind(resourceClass);
    var gvk = new GroupVersionKind(apiVersion, kind);

    InformerEventSource<CustomResourceDefinition, HasMetadata> crdInformer = null;
    try {
      crdInformer = (InformerEventSource<CustomResourceDefinition, HasMetadata>) context
          .eventSourceRetriever().getResourceEventSourceFor(CustomResourceDefinition.class);
    } catch (IllegalArgumentException e) {
      log.debug("Error when finding event source for CustomResourceDefinitions", e);
    }

    if (crdInformer != null) {
      return crdInformer
          .list(crd -> crd.getSpec().getNames().getKind().equals(kind)
              && crd.getSpec().getGroup().equals(gvk.getGroup()))
          .findAny().isPresent();
    } else {
      if (crdPresenceCache.get(gvk) == null ||
          crdPresenceLastRefresh.get(gvk)
              .isBefore(LocalDateTime.now().minus(cacheRefreshInterval, ChronoUnit.MILLIS))) {
        refreshCache(gvk, context.getClient());
      }
      return crdPresenceCache.get(gvk);
    }
  }

  private void refreshCache(GroupVersionKind gvk, KubernetesClient client) {
    boolean found = client.resources(CustomResourceDefinition.class).list().getItems()
        .stream().anyMatch(crd -> crd.getSpec().getNames().getKind().equals(gvk.getKind())
            && crd.getSpec().getGroup().equals(gvk.getGroup()));
    crdPresenceCache.put(gvk, found);
    crdPresenceLastRefresh.put(gvk, LocalDateTime.now());
  }


}
