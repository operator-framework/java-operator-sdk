package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.InClusterCurl;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class TomcatOperatorE2E {

  static final Logger log = LoggerFactory.getLogger(TomcatOperatorE2E.class);

  static final KubernetesClient client = new DefaultKubernetesClient();

  public TomcatOperatorE2E() throws FileNotFoundException {}

  static final int tomcatReplicas = 2;

  boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator " + (remote ? "remote" : "locally"));
    return !remote;
  }

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withReconciler(new TomcatReconciler())
              .withReconciler(new WebappReconciler(client))
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).items())
              .build();

  Tomcat getTomcat() {
    Tomcat tomcat = new Tomcat();
    tomcat.setMetadata(
        new ObjectMetaBuilder()
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
    webapp1.setMetadata(
        new ObjectMetaBuilder()
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
  void test() {
    var tomcat = getTomcat();
    var webapp1 = getWebapp();
    var tomcatClient = client.resources(Tomcat.class);
    var webappClient = client.resources(Webapp.class);

    log.info("Creating test Tomcat object: {}", tomcat);
    tomcatClient.inNamespace(operator.getNamespace()).resource(tomcat).create();
    log.info("Creating test Webapp object: {}", webapp1);
    webappClient.inNamespace(operator.getNamespace()).resource(webapp1).create();

    log.info("Waiting 5 minutes for Tomcat and Webapp CR statuses to be updated");
    await()
        .atMost(5, MINUTES)
        .untilAsserted(
            () -> {
              Tomcat updatedTomcat =
                  tomcatClient
                      .inNamespace(operator.getNamespace())
                      .withName(tomcat.getMetadata().getName())
                      .get();
              Webapp updatedWebapp =
                  webappClient
                      .inNamespace(operator.getNamespace())
                      .withName(webapp1.getMetadata().getName())
                      .get();
              assertThat(updatedTomcat.getStatus(), is(notNullValue()));
              assertThat(updatedTomcat.getStatus().getReadyReplicas(), equalTo(tomcatReplicas));
              assertThat(updatedWebapp.getStatus(), is(notNullValue()));
              assertThat(updatedWebapp.getStatus().getDeployedArtifact(), is(notNullValue()));
            });

    String url =
        "http://" + tomcat.getMetadata().getName() + "/" + webapp1.getSpec().getContextPath() + "/";
    var inClusterCurl = new InClusterCurl(client, operator.getNamespace());
    log.info("Starting curl Pod and waiting 5 minutes for GET of {} to return 200", url);

    await("wait-for-webapp")
        .atMost(6, MINUTES)
        .untilAsserted(
            () -> {
              try {
                var curlOutput = inClusterCurl.checkUrl(url);
                assertThat(curlOutput, equalTo("200"));
              } catch (KubernetesClientException ex) {
                throw new AssertionError(ex);
              }
            });

    log.info("Deleting test Tomcat object: {}", tomcat);
    tomcatClient.inNamespace(operator.getNamespace()).resource(tomcat).delete();
    log.info("Deleting test Webapp object: {}", webapp1);
    webappClient.inNamespace(operator.getNamespace()).resource(webapp1).delete();
  }
}
