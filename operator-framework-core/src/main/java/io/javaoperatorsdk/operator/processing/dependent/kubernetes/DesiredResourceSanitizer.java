package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource.useSSA;

public class DesiredResourceSanitizer {

  private DesiredResourceSanitizer() {}

  public static <R, P extends HasMetadata> void sanitizeDesired(R desired, R actual, P primary,
      Context<P> context) {
    if (useSSA(context)) {
      if (desired instanceof StatefulSet) {
        fillDefaultsOnVolumeClaimTemplate((StatefulSet) desired);
      }
      if (desired instanceof Secret) {
        checkIfStringDataUsed((Secret) desired);
      }
    }
  }

  private static void checkIfStringDataUsed(Secret secret) {
    if (secret.getStringData() != null && !secret.getStringData().isEmpty()) {
      throw new IllegalStateException(
          "There is a known issue using StringData with SSA. Use data instead.");
    }
  }

  private static void fillDefaultsOnVolumeClaimTemplate(StatefulSet statefulSet) {
    if (!statefulSet.getSpec().getVolumeClaimTemplates().isEmpty()) {
      statefulSet.getSpec().getVolumeClaimTemplates().forEach(t -> {
        if (t.getSpec().getVolumeMode() == null) {
          t.getSpec().setVolumeMode("Filesystem");
        }
        if (t.getStatus() == null) {
          t.setStatus(new PersistentVolumeClaimStatus());
        }
        if (t.getStatus().getPhase() == null) {
          t.setStatus(new PersistentVolumeClaimStatus());
          t.getStatus().setPhase("pending");
        }
      });
    }
  }
}
