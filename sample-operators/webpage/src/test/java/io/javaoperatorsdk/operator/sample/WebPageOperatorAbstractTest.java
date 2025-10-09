/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.customresource.WebPageSpec;

import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.Utils.serviceName;
import static io.javaoperatorsdk.operator.sample.WebPageReconciler.INDEX_HTML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class WebPageOperatorAbstractTest {

  static final Logger log =
      LoggerFactory.getLogger(WebPageOperatorStandaloneDependentResourcesE2E.class);

  static final KubernetesClient client = new KubernetesClientBuilder().build();
  public static final String TEST_PAGE = "test-page";
  public static final String TITLE1 = "Hello Operator World";
  public static final String TITLE2 = "Hello Operator World Title 2";
  public static final int WAIT_SECONDS = 360;
  public static final int LONG_WAIT_SECONDS = 120;
  public static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

  boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator " + (remote ? "remote" : "locally"));
    return !remote;
  }

  @Test
  void testAddingWebPage() {

    var webPage = createWebPage(TITLE1);
    operator().create(webPage);

    await()
        .atMost(Duration.ofSeconds(WAIT_SECONDS))
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              var actual = operator().get(WebPage.class, TEST_PAGE);
              var deployment = operator().get(Deployment.class, deploymentName(webPage));
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getAreWeGood()).isTrue();
              assertThat(deployment.getSpec().getReplicas())
                  .isEqualTo(deployment.getStatus().getReadyReplicas());
            });
    assertThat(httpGetForWebPage(webPage)).contains(TITLE1);

    // update part: changing title
    operator().replace(createWebPage(TITLE2));

    await()
        .atMost(Duration.ofSeconds(LONG_WAIT_SECONDS))
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              String page =
                  operator()
                      .get(ConfigMap.class, Utils.configMapName(webPage))
                      .getData()
                      .get(INDEX_HTML);
              // not using portforward here since there were issues with GitHub actions
              // String page = httpGetForWebPage(webPage);
              assertThat(page).isNotNull().contains(TITLE2);
            });

    // delete part: deleting webpage
    operator().delete(createWebPage(TITLE2));

    await()
        .atMost(Duration.ofSeconds(WAIT_SECONDS))
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              Deployment deployment = operator().get(Deployment.class, deploymentName(webPage));
              assertThat(deployment).isNull();
            });
  }

  String httpGetForWebPage(WebPage webPage) {
    LocalPortForward portForward = null;
    try {
      portForward =
          client
              .services()
              .inNamespace(webPage.getMetadata().getNamespace())
              .withName(serviceName(webPage))
              .portForward(80);
      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      HttpRequest request =
          HttpRequest.newBuilder()
              .GET()
              .uri(new URI("http://localhost:" + portForward.getLocalPort()))
              .build();
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (URISyntaxException | IOException | InterruptedException e) {
      return null;
    } finally {
      if (portForward != null) {
        try {
          portForward.close();
        } catch (IOException e) {
          log.error("Port forward close error.", e);
        }
      }
    }
  }

  WebPage createWebPage(String title) {
    WebPage webPage = new WebPage();
    webPage.setMetadata(new ObjectMeta());
    webPage.getMetadata().setName(TEST_PAGE);
    webPage.getMetadata().setNamespace(operator().getNamespace());
    webPage.setSpec(new WebPageSpec());
    webPage
        .getSpec()
        .setHtml(
            "<html>\n"
                + "      <head>\n"
                + "        <title>"
                + title
                + "</title>\n"
                + "      </head>\n"
                + "      <body>\n"
                + "        Hello World! \n"
                + "      </body>\n"
                + "    </html>");

    return webPage;
  }

  abstract AbstractOperatorExtension operator();
}
