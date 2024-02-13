package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.expiration.Expiration;
import io.javaoperatorsdk.operator.processing.expiration.ExpirationExecution;
import io.javaoperatorsdk.operator.processing.expiration.RetryExpiration;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public class CRDPresentActivationCondition implements Condition<HasMetadata, HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(CRDPresentActivationCondition.class);

  public static final int DEFAULT_EXPIRATION_INITIAL_INTERVAL = 1000;
  public static final int DEFAULT_EXPIRATION_INTERVAL_MULTIPLIER = 4;
  public static final int DEFAULT_EXPIRATION_MAX_RETRY_ATTEMPTS = 10;

  /**
   * The idea behind default expiration is that on cluster start there might be different phases
   * when CRDs and controllers are added. For a few times it will be checked if the target CRD is
   * not present, after it will just use the cached state.
   */
  public static Retry DEFAULT_EXPIRATION_RETRY =
      new GenericRetry().setInitialInterval(DEFAULT_EXPIRATION_INITIAL_INTERVAL)
          .setIntervalMultiplier(DEFAULT_EXPIRATION_INTERVAL_MULTIPLIER)
          .setMaxAttempts(DEFAULT_EXPIRATION_MAX_RETRY_ATTEMPTS);

  private final Map<String, CRDCheckState> crdPresenceCache = new ConcurrentHashMap<>();

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
    final var crdName = HasMetadata.getFullResourceName(resourceClass);

    var crdCheckState = crdPresenceCache.computeIfAbsent(crdName,
        g -> new CRDCheckState(expiration.initExecution()));
    // in case of parallel execution it is only refreshed once
    synchronized (crdCheckState) {
      if (crdCheckState.isExpired()) {
        log.debug("Refreshing cache for resource: {}", crdName);
        final var found = context.getClient().resources(CustomResourceDefinition.class)
            .withName(crdName).get() != null;
        crdCheckState.refresh(found);
      }
    }
    return crdPresenceCache.get(crdName).isCrdPresent();
  }

  static class CRDCheckState {
    private final ExpirationExecution expirationExecution;
    private boolean crdPresent;

    public CRDCheckState(ExpirationExecution expirationExecution) {
      this.expirationExecution = expirationExecution;
    }

    void refresh(boolean found) {
      crdPresent = found;
      expirationExecution.refreshed();
    }

    boolean isExpired() {
      return expirationExecution.isExpired();
    }

    public boolean isCrdPresent() {
      return crdPresent;
    }
  }
}
