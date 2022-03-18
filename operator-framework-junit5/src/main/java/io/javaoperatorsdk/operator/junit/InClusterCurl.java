package io.javaoperatorsdk.operator.junit;

import java.util.UUID;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;

public class InClusterCurl {

  private final KubernetesClient client;
  private final String namespace;

  public InClusterCurl(KubernetesClient client, String namespace) {
    this.client = client;
    this.namespace = namespace;
  }

  public String checkUrl(String url) {
    return checkUrl("-s", "-o", "/dev/null", "-w", "%{http_code}", url);
  }

  public String checkUrl(String... args) {
    String podName = KubernetesResourceUtil.sanitizeName("curl-" + UUID.randomUUID());
    try {
      Pod curlPod = client.run().inNamespace(namespace)
          .withRunConfig(new RunConfigBuilder()
              .withArgs(args)
              .withName(podName)
              .withImage("curlimages/curl:7.78.0")
              .withRestartPolicy("Never")
              .build())
          .done();
      await("wait-for-curl-pod-run").atMost(2, MINUTES)
          .until(() -> {
            String phase =
                client.pods().inNamespace(namespace).withName(podName).get()
                    .getStatus().getPhase();
            return phase.equals("Succeeded") || phase.equals("Failed");
          });

      String curlOutput =
          client.pods().inNamespace(namespace)
              .withName(curlPod.getMetadata().getName()).getLog();

      return curlOutput;
    } finally {
      client.pods().inNamespace(namespace).withName(podName).delete();
      await("wait-for-curl-pod-stop").atMost(1, MINUTES)
          .until(() -> client.pods().inNamespace(namespace).withName(podName)
              .get() == null);
    }
  }
}
