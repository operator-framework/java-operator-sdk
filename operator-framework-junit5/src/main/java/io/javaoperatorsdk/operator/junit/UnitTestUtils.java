package io.javaoperatorsdk.operator.junit;

import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;

public class UnitTestUtils {

  public static final int CRD_READY_WAIT = 2000;

  private static final Logger LOGGER = LoggerFactory.getLogger(UnitTestUtils.class);

  public static void applyCrd(KubernetesClient client,
      Class<? extends CustomResource<?, ?>> crClass) {
    applyCrd(client, ReconcilerUtils.getResourceTypeName(crClass));
  }

  public static void applyCrd(KubernetesClient client, String resourceTypeName) {
    String path = "/META-INF/fabric8/" + resourceTypeName + "-v1.yml";
    try (InputStream is = UnitTestUtils.class.getResourceAsStream(path)) {
      final var crd = client.load(is);
      crd.createOrReplace();
      Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
      LOGGER.debug("Applied CRD with path: {}", path);
    } catch (InterruptedException ex) {
      LOGGER.error("Interrupted.", ex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot apply CRD yaml: " + path, ex);
    }
  }

  public static String generateNamespaceForTestName(TestInfo testInfo) {
    String namespace =
        (testInfo.getTestMethod().orElseThrow().getName() + UUID.randomUUID()).toLowerCase();
    if (namespace.length() > 63) {
      namespace = namespace.substring(0, 63);
    }
    if (namespace.endsWith("-")) {
      namespace = namespace.substring(0, namespace.length() - 1);
    }
    return namespace;
  }
}
