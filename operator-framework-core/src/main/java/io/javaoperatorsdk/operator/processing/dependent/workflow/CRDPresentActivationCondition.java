package io.javaoperatorsdk.operator.processing.dependent.workflow;

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
import io.javaoperatorsdk.operator.processing.expiration.Expiration;
import io.javaoperatorsdk.operator.processing.expiration.RetryExpiration;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public class CRDPresentActivationCondition implements Condition<HasMetadata, HasMetadata> {

  public static Retry DEFAULT_EXPIRATION_RETRY = new GenericRetry().setInitialInterval(2000)
      .setMaxInterval(1000 * 60 * 60)
      .setIntervalMultiplier(2);

  private static final Logger log = LoggerFactory.getLogger(CRDPresentActivationCondition.class);

  private final Map<GroupVersionKind, CRDCheckState> crdPresenceCache = new ConcurrentHashMap<>();

  private final Retry expirationRetry;

  public CRDPresentActivationCondition() {
    this(DEFAULT_EXPIRATION_RETRY);
  }

  public CRDPresentActivationCondition(Retry expirationRetry) {
    this.expirationRetry = expirationRetry;
  }

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
      var crdCheckState = crdPresenceCache.computeIfAbsent(gvk,
          g -> new CRDCheckState(new RetryExpiration(expirationRetry.initExecution())));
      // in case of parallel execution it is only refreshed once
      synchronized (crdCheckState) {
        if (crdCheckState.getExpiration().isExpired()) {
          refreshCache(gvk, context.getClient());
        }
      }
      return crdPresenceCache.get(gvk).getCrdPresent();
    }
  }

  private void refreshCache(GroupVersionKind gvk, KubernetesClient client) {
    var state = crdPresenceCache.computeIfAbsent(gvk,
        g -> new CRDCheckState(new RetryExpiration(expirationRetry.initExecution())));

    boolean found = client.resources(CustomResourceDefinition.class).list().getItems()
        .stream().anyMatch(crd -> crd.getSpec().getNames().getKind().equals(gvk.getKind())
            && crd.getSpec().getGroup().equals(gvk.getGroup()));

    state.setCrdPresent(found);
    state.getExpiration().refreshed();
  }

  static class CRDCheckState {

    public CRDCheckState(Expiration expiration) {
      this.expiration = expiration;
    }

    private Expiration expiration;

    private Boolean crdPresent;

    public Expiration getExpiration() {
      return expiration;
    }

    public void setExpiration(Expiration expiration) {
      this.expiration = expiration;
    }

    public Boolean getCrdPresent() {
      return crdPresent;
    }

    public void setCrdPresent(Boolean crdPresent) {
      this.crdPresent = crdPresent;
    }
  }
}
