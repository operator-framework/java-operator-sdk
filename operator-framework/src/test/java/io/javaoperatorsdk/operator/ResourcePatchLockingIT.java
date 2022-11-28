package io.javaoperatorsdk.operator;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import static org.assertj.core.api.Assertions.assertThat;

// todo delete
class ResourcePatchLockingIT {

  @Disabled
  @Test
  void patchWithOptimisticLocking() {
    var client = new KubernetesClientBuilder().build();
    var cm = new ConfigMap();
    cm.setMetadata(new ObjectMetaBuilder()
        .withName("testpatch1")
        .withNamespace("default")
        .build());
    cm.setData(Map.of("key1", "val1"));
    var created = client.configMaps().resource(cm).createOrReplace();

    var modified = clone(created);
    // modified.getMetadata().setResourceVersion("1234567");
    modified.addFinalizer("finalizer.com/finalizer");
    var patched =
        client.configMaps().resource(created).patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY),
            modified);

    var noFin = clone(patched);
    noFin.removeFinalizer("finalizer.com/finalizer");
    patched =
        client.configMaps().resource(patched).patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY),
            noFin);

    assertThat(patched.getMetadata().getFinalizers()).isEmpty();
    System.out.println(patched);
  }


  private ConfigMap clone(ConfigMap cm) {
    return new ConfigurationService() {
      @Override
      public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
          Reconciler<R> reconciler) {
        return null;
      }

      @Override
      public Set<String> getKnownReconcilerNames() {
        return null;
      }

      @Override
      public Version getVersion() {
        return null;
      }
    }.getResourceCloner().clone(cm);
  }


}
