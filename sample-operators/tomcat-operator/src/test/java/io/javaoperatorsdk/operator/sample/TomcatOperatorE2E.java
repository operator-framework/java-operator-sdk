package io.javaoperatorsdk.operator.sample;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

import static java.util.concurrent.TimeUnit.*;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public class TomcatOperatorE2E {

  final static String TEST_NS = "tomcat-test";

  final static Logger log = LoggerFactory.getLogger(TomcatOperatorE2E.class);

  @Test
  public void test() {
    Config config = new ConfigBuilder().withNamespace(null).build();
    KubernetesClient client = new DefaultKubernetesClient(config);

    // Use this if you want to run the test without deploying the Operator to Kubernetes
    if ("true".equals(System.getenv("RUN_OPERATOR_IN_TEST"))) {
      Operator operator = new Operator(client, DefaultConfigurationService.instance());
      operator.register(new TomcatReconciler());
      operator.register(new WebappReconciler(client));
      operator.start();
    }

    Tomcat tomcat = new Tomcat();
    tomcat.setMetadata(new ObjectMetaBuilder()
        .withName("test-tomcat1")
        .withNamespace(TEST_NS)
        .build());
    tomcat.setSpec(new TomcatSpec());
    tomcat.getSpec().setReplicas(3);
    tomcat.getSpec().setVersion(9);

    Webapp webapp1 = new Webapp();
    webapp1.setMetadata(new ObjectMetaBuilder()
        .withName("test-webapp1")
        .withNamespace(TEST_NS)
        .build());
    webapp1.setSpec(new WebappSpec());
    webapp1.getSpec().setContextPath("webapp1");
    webapp1.getSpec().setTomcat(tomcat.getMetadata().getName());
    webapp1.getSpec().setUrl("http://tomcat.apache.org/tomcat-7.0-doc/appdev/sample/sample.war");

    var tomcatClient = client.customResources(Tomcat.class);
    var webappClient = client.customResources(Webapp.class);

    Namespace testNs = new NamespaceBuilder().withMetadata(
        new ObjectMetaBuilder().withName(TEST_NS).build()).build();

    if (testNs != null) {
      // We perform a pre-run cleanup instead of a post-run cleanup. This is to help with debugging
      // test results when running against a persistent cluster. The test namespace would stay
      // after the test run so we can check what's there, but it would be cleaned up during the next
      // test run.
      log.info("Cleanup: deleting test namespace {}", TEST_NS);
      client.namespaces().delete(testNs);
      await().atMost(5, MINUTES)
          .until(() -> client.namespaces().withName("tomcat-test").get() == null);
    }

    log.info("Creating test namespace {}", TEST_NS);
    client.namespaces().create(testNs);

    log.info("Creating test Tomcat object: {}", tomcat);
    tomcatClient.inNamespace(TEST_NS).create(tomcat);
    log.info("Creating test Webapp object: {}", webapp1);
    webappClient.inNamespace(TEST_NS).create(webapp1);

    log.info("Waiting 5 minutes for Tomcat and Webapp CR statuses to be updated");
    await().atMost(5, MINUTES).untilAsserted(() -> {
      Tomcat updatedTomcat =
          tomcatClient.inNamespace(TEST_NS).withName(tomcat.getMetadata().getName()).get();
      Webapp updatedWebapp =
          webappClient.inNamespace(TEST_NS).withName(webapp1.getMetadata().getName()).get();
      assertThat(updatedTomcat.getStatus(), is(notNullValue()));
      assertThat(updatedTomcat.getStatus().getReadyReplicas(), equalTo(3));
      assertThat(updatedWebapp.getStatus(), is(notNullValue()));
      assertThat(updatedWebapp.getStatus().getDeployedArtifact(), is(notNullValue()));
    });

    String url =
        "http://" + tomcat.getMetadata().getName() + "/" + webapp1.getSpec().getContextPath() + "/";
    log.info("Starting curl Pod and waiting 5 minutes for GET of {} to return 200", url);

    await("wait-for-webapp").atMost(6, MINUTES).untilAsserted(() -> {
      try {

        log.info("Starting curl Pod to test if webapp was deployed correctly");
        Pod curlPod = client.run().inNamespace(TEST_NS)
            .withRunConfig(new RunConfigBuilder()
                .withArgs("-s", "-o", "/dev/null", "-w", "%{http_code}", url)
                .withName("curl")
                .withImage("curlimages/curl:7.78.0")
                .withRestartPolicy("Never")
                .build())
            .done();
        log.info("Waiting for curl Pod to finish running");
        await("wait-for-curl-pod-run").atMost(2, MINUTES)
            .until(() -> {
              String phase =
                  client.pods().inNamespace(TEST_NS).withName("curl").get().getStatus().getPhase();
              return phase.equals("Succeeded") || phase.equals("Failed");
            });

        String curlOutput =
            client.pods().inNamespace(TEST_NS).withName(curlPod.getMetadata().getName()).getLog();
        log.info("Output from curl: '{}'", curlOutput);
        assertThat(curlOutput, equalTo("200"));
      } catch (KubernetesClientException ex) {
        throw new AssertionError(ex);
      } finally {
        log.info("Deleting curl Pod");
        client.pods().inNamespace(TEST_NS).withName("curl").delete();
        await("wait-for-curl-pod-stop").atMost(1, MINUTES)
            .until(() -> client.pods().inNamespace(TEST_NS).withName("curl").get() == null);
      }
    });
  }

}
