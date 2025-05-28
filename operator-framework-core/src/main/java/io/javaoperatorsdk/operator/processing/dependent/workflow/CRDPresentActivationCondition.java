package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

/**
 * A generic CRD checking activation condition. Makes sure that the CRD is not checked unnecessarily
 * even used in multiple condition. By default, it checks CRD at most 10 times with a delay at least
 * 10 seconds. To fully customize CRD check trigger behavior you can extend this class and override
 * the {@link CRDPresentActivationCondition#shouldCheckStateNow(CRDCheckState)} method.
 *
 * @param <R> the resource type associated with the CRD to check for presence
 * @param <P> the primary resource type associated with the reconciler processing dependents
 *     associated with this condition
 */
public class CRDPresentActivationCondition<R extends HasMetadata, P extends HasMetadata>
    implements Condition<R, P> {

  public static final int DEFAULT_CRD_CHECK_LIMIT = 10;
  public static final Duration DEFAULT_CRD_CHECK_INTERVAL = Duration.ofSeconds(10);

  private static final Map<String, CRDCheckState> crdPresenceCache = new ConcurrentHashMap<>();

  private final CRDPresentChecker crdPresentChecker;
  private final int checkLimit;
  private final Duration crdCheckInterval;

  public CRDPresentActivationCondition() {
    this(DEFAULT_CRD_CHECK_LIMIT, DEFAULT_CRD_CHECK_INTERVAL);
  }

  public CRDPresentActivationCondition(int checkLimit, Duration crdCheckInterval) {
    this(new CRDPresentChecker(), checkLimit, crdCheckInterval);
  }

  // for testing purposes only
  CRDPresentActivationCondition(
      CRDPresentChecker crdPresentChecker, int checkLimit, Duration crdCheckInterval) {
    this.crdPresentChecker = crdPresentChecker;
    this.checkLimit = checkLimit;
    this.crdCheckInterval = crdCheckInterval;
  }

  @Override
  public boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context) {

    var resourceClass = dependentResource.resourceType();
    final var crdName = HasMetadata.getFullResourceName(resourceClass);

    var crdCheckState = crdPresenceCache.computeIfAbsent(crdName, g -> new CRDCheckState());

    synchronized (crdCheckState) {
      if (shouldCheckStateNow(crdCheckState)) {
        boolean isPresent = crdPresentChecker.checkIfCRDPresent(crdName, context.getClient());
        crdCheckState.checkedNow(isPresent);
      }
    }

    if (crdCheckState.isCrdPresent() == null) {
      throw new IllegalStateException("State should be already checked at this point.");
    }
    return crdCheckState.isCrdPresent();
  }

  /** Override this method to fine tune when the crd state should be refreshed; */
  protected boolean shouldCheckStateNow(CRDCheckState crdCheckState) {
    if (crdCheckState.isCrdPresent() == null) {
      return true;
    }
    // assumption is that if CRD is present, it is not deleted anymore
    if (crdCheckState.isCrdPresent()) {
      return false;
    }
    if (crdCheckState.getCheckCount() >= checkLimit) {
      return false;
    }
    if (crdCheckState.getLastChecked() == null) {
      return true;
    }
    return LocalDateTime.now().isAfter(crdCheckState.getLastChecked().plus(crdCheckInterval));
  }

  public static class CRDCheckState {
    private Boolean crdPresent;
    private LocalDateTime lastChecked;
    private int checkCount = 0;

    public void checkedNow(boolean crdPresent) {
      this.crdPresent = crdPresent;
      lastChecked = LocalDateTime.now();
      checkCount++;
    }

    public Boolean isCrdPresent() {
      return crdPresent;
    }

    public LocalDateTime getLastChecked() {
      return lastChecked;
    }

    public int getCheckCount() {
      return checkCount;
    }
  }

  public static class CRDPresentChecker {
    boolean checkIfCRDPresent(String crdName, KubernetesClient client) {
      return client.resources(CustomResourceDefinition.class).withName(crdName).get() != null;
    }
  }

  /** For testing purposes only */
  public static void clearState() {
    crdPresenceCache.clear();
  }
}
