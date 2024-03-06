package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.processing.expiration.Expiration;
import io.javaoperatorsdk.operator.processing.expiration.RetryExpiration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.expiration.ExpirationExecution;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public class CRDPresentActivationCondition implements Condition<HasMetadata, HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(CRDPresentActivationCondition.class);

  /**
   *
   * */
  public static Retry DEFAULT_EXPIRATION_RETRY = new GenericRetry().setInitialInterval(2000)
      .setIntervalMultiplier(2)
      .setMaxAttempts(10);

  private final Map<GroupVersionKind, CRDCheckState> crdPresenceCache = new ConcurrentHashMap<>();

  private final Expiration expiration;

  public CRDPresentActivationCondition() {
    this(new RetryExpiration(DEFAULT_EXPIRATION_RETRY));
  }

  public CRDPresentActivationCondition(Expiration expiration) {
    this.expiration = expiration;
  }

  @Override
  public boolean isMet(DependentResource<HasMetadata, HasMetadata> dependentResource,
      HasMetadata primary, Context<HasMetadata> context) {

    var resourceClass = dependentResource.resourceType();
    var apiVersion = HasMetadata.getApiVersion(resourceClass);
    var kind = HasMetadata.getKind(resourceClass);
    var gvk = new GroupVersionKind(apiVersion, kind);

    var crdCheckState = crdPresenceCache.computeIfAbsent(gvk,
        g -> new CRDCheckState(expiration.initExecution()));
    // in case of parallel execution it is only refreshed once
    synchronized (crdCheckState) {
      if (crdCheckState.getExpiration().isExpired()) {
        refreshCache(crdCheckState,gvk, context.getClient());
      }
    }
    return crdPresenceCache.get(gvk).getCrdPresent();
  }

  private void refreshCache(CRDCheckState crdCheckState, GroupVersionKind gvk, KubernetesClient client) {

    boolean found = client.resources(CustomResourceDefinition.class).list().getItems()
        .stream().anyMatch(crd -> crd.getSpec().getNames().getKind().equals(gvk.getKind())
            && crd.getSpec().getGroup().equals(gvk.getGroup()));

    crdCheckState.setCrdPresent(found);
    crdCheckState.getExpiration().refreshed();
  }

  static class CRDCheckState {

    public CRDCheckState(ExpirationExecution expirationExecution) {
      this.expirationExecution = expirationExecution;
    }

    private ExpirationExecution expirationExecution;

    private Boolean crdPresent;

    public ExpirationExecution getExpiration() {
      return expirationExecution;
    }

    public void setExpiration(ExpirationExecution expirationExecution) {
      this.expirationExecution = expirationExecution;
    }

    public Boolean getCrdPresent() {
      return crdPresent;
    }

    public void setCrdPresent(Boolean crdPresent) {
      this.crdPresent = crdPresent;
    }
  }
}
