package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.E2EOperatorExtension;
import io.javaoperatorsdk.operator.junit.OperatorExtension;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public class TomcatOperatorE2E {

  final static Logger log = LoggerFactory.getLogger(TomcatOperatorE2E.class);

  final static KubernetesClient client = new DefaultKubernetesClient();

  public TomcatOperatorE2E() throws FileNotFoundException {}

  final static int tomcatReplicas = 2;

  boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator " + (remote ? "remote" : "locally"));
    return !remote;
  }

  @RegisterExtension
  AbstractOperatorExtension operator = isLocal() ? OperatorExtension.builder()
      .waitForNamespaceDeletion(false)
      .withConfigurationService(DefaultConfigurationService.instance())
      .withReconciler(new TomcatReconciler(client))
      .withReconciler(new WebappReconciler(client))
      .build()
      : E2EOperatorExtension.builder()
          .waitForNamespaceDeletion(false)
          .withConfigurationService(DefaultConfigurationService.instance())
          .withOperatorDeployment(
              client.load(new FileInputStream("k8s/operator.yaml")).get())
          .build();

  Tomcat getTomcat() {
    Tomcat tomcat = new Tomcat();
    tomcat.setMetadata(new ObjectMetaBuilder()
        .withName("test-tomcat1")
        .withNamespace(operator.getNamespace())
        .build());
    tomcat.setSpec(new TomcatSpec());
    tomcat.getSpec().setReplicas(tomcatReplicas);
    tomcat.getSpec().setVersion(9);
    return tomcat;
  }

  Webapp getWebapp() {
    Webapp webapp1 = new Webapp();
    webapp1.setMetadata(new ObjectMetaBuilder()
        .withName("test-webapp1")
        .withNamespace(operator.getNamespace())
        .build());
    webapp1.setSpec(new WebappSpec());
    webapp1.getSpec().setContextPath("webapp1");
    webapp1.getSpec().setTomcat(getTomcat().getMetadata().getName());
    webapp1.getSpec().setUrl("http://tomcat.apache.org/tomcat-7.0-doc/appdev/sample/sample.war");
    return webapp1;
  }

  @Test
  public void test() {
    var tomcat = getTomcat();
    var webapp1 = getWebapp();
    var tomcatClient = client.resources(Tomcat.class);
    var webappClient = client.resources(Webapp.class);

    log.info("Creating test Tomcat object: {}", tomcat);
    tomcatClient.inNamespace(operator.getNamespace()).create(tomcat);
    log.info("Creating test Webapp object: {}", webapp1);
    webappClient.inNamespace(operator.getNamespace()).create(webapp1);

    log.info("Waiting 5 minutes for Tomcat and Webapp CR statuses to be updated");
    await().atMost(5, MINUTES).untilAsserted(() -> {
      Tomcat updatedTomcat =
          tomcatClient.inNamespace(operator.getNamespace()).withName(tomcat.getMetadata().getName())
              .get();
      Webapp updatedWebapp =
          webappClient.inNamespace(operator.getNamespace())
              .withName(webapp1.getMetadata().getName()).get();
      assertThat(updatedTomcat.getStatus(), is(notNullValue()));
      assertThat(updatedTomcat.getStatus().getReadyReplicas(), equalTo(tomcatReplicas));
      assertThat(updatedWebapp.getStatus(), is(notNullValue()));
      assertThat(updatedWebapp.getStatus().getDeployedArtifact(), is(notNullValue()));
    });

    String url =
        "http://" + tomcat.getMetadata().getName() + "/" + webapp1.getSpec().getContextPath() + "/";
    log.info("Starting curl Pod and waiting 5 minutes for GET of {} to return 200", url);

    await("wait-for-webapp").atMost(6, MINUTES).untilAsserted(() -> {
      try {

        log.info("Starting curl Pod to test if webapp was deployed correctly");
        Pod curlPod = client.run().inNamespace(operator.getNamespace())
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
                  client.pods().inNamespace(operator.getNamespace()).withName("curl").get()
                      .getStatus().getPhase();
              return phase.equals("Succeeded") || phase.equals("Failed");
            });

        String curlOutput =
            client.pods().inNamespace(operator.getNamespace())
                .withName(curlPod.getMetadata().getName()).getLog();
        log.info("Output from curl: '{}'", curlOutput);
        assertThat(curlOutput, equalTo("200"));
      } catch (KubernetesClientException ex) {
        throw new AssertionError(ex);
      } finally {
        log.info("Deleting curl Pod");
        client.pods().inNamespace(operator.getNamespace()).withName("curl").delete();
        await("wait-for-curl-pod-stop").atMost(1, MINUTES)
            .until(() -> client.pods().inNamespace(operator.getNamespace()).withName("curl")
                .get() == null);
      }
    });
  }

}
