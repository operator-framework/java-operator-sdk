package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.E2EOperatorExtension;
import io.javaoperatorsdk.operator.junit.OperatorExtension;

import static io.javaoperatorsdk.operator.sample.WebPageReconcilerDependentResources.serviceName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WebPageOperatorDependentResourcesE2E {

  static final Logger log = LoggerFactory.getLogger(WebPageOperatorDependentResourcesE2E.class);

  static final KubernetesClient client = new DefaultKubernetesClient();
  public static final String TEST_PAGE = "test-page";

  public WebPageOperatorDependentResourcesE2E() throws FileNotFoundException {}

  boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator " + (remote ? "remote" : "locally"));
    return !remote;
  }

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? OperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withConfigurationService(DefaultConfigurationService.instance())
              .withReconciler(new WebPageReconcilerDependentResources(client))
              .build()
          : E2EOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withConfigurationService(DefaultConfigurationService.instance())
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).get())
              .build();

  @Test
  void testAddingWebPage() throws IOException {
    LocalPortForward portForward = null;
    try {
      var webPage = createWebPage();
      operator.create(WebPage.class, webPage);

      await()
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofSeconds(1))
          .until(
              () -> {
                var actual = operator.get(WebPage.class, TEST_PAGE);
                var deployment = operator.get(Deployment.class,
                    WebPageReconcilerDependentResources.deploymentName(webPage));

                return Boolean.TRUE.equals(actual.getStatus().getAreWeGood())
                    && Objects.equals(deployment.getSpec().getReplicas(),
                        deployment.getStatus().getReadyReplicas());
              });

      portForward =
          client.services().inNamespace(webPage.getMetadata().getNamespace())
              .withName(serviceName(webPage)).portForward(80);

      String response = httpGet(portForward.getLocalPort());
      assertThat(response).contains("<title>Hello Operator World</title>");
    } finally {
      if (portForward != null) {
        portForward.close();
      }
    }
  }

  String httpGet(int localPort) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    try {
      HttpRequest request =
          HttpRequest.newBuilder().GET().uri(new URI("http://localhost:" + localPort)).build();
      HttpResponse<String> response = null;
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.body();
    } catch (URISyntaxException | IOException | InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  WebPage createWebPage() {
    WebPage webPage = new WebPage();
    webPage.setMetadata(new ObjectMeta());
    webPage.getMetadata().setName(TEST_PAGE);
    webPage.getMetadata().setNamespace(operator.getNamespace());
    webPage.setSpec(new WebPageSpec());
    webPage
        .getSpec()
        .setHtml(
            "<html>\n"
                + "      <head>\n"
                + "        <title>Hello Operator World</title>\n"
                + "      </head>\n"
                + "      <body>\n"
                + "        Hello World! \n"
                + "      </body>\n"
                + "    </html>");

    return webPage;
  }
}
