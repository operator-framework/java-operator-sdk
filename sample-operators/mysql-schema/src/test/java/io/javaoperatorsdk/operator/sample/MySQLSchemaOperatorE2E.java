package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.E2EOperatorExtension;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.dependent.ResourcePollerConfig;
import io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class MySQLSchemaOperatorE2E {

  static final Logger log = LoggerFactory.getLogger(MySQLSchemaOperatorE2E.class);

  static final KubernetesClient client = new DefaultKubernetesClient();

  static final String MY_SQL_NS = "mysql";

  private static List<HasMetadata> infrastructure = new ArrayList<>();

  static {
    infrastructure.add(
        new NamespaceBuilder().withNewMetadata().withName(MY_SQL_NS).endMetadata().build());
    try {
      infrastructure.addAll(client.load(new FileInputStream("k8s/mysql-deployment.yaml")).get());
      infrastructure.addAll(client.load(new FileInputStream("k8s/mysql-service.yaml")).get());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

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
              .withConfigurationService(DefaultConfigurationService.instance())
              .withReconciler(
                  new MySQLSchemaReconciler(),
                  c -> {
                    c.replaceDependentResourceConfig(
                        SchemaDependentResource.class,
                        new ResourcePollerConfig(
                            700, new MySQLDbConfig("127.0.0.1", "3306", "root", "password")));
                  })
              .withInfrastructure(infrastructure)
              .build()
          : E2EOperatorExtension.builder()
              .withConfigurationService(DefaultConfigurationService.instance())
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).get())
              .withInfrastructure(infrastructure)
              .build();

  public MySQLSchemaOperatorE2E() throws FileNotFoundException {}

  @Test
  public void test() throws IOException {
    // Opening a port-forward if running locally
    LocalPortForward portForward = null;
    if (isLocal()) {
      String podName =
          client
              .pods()
              .inNamespace(MY_SQL_NS)
              .withLabel("app", "mysql")
              .list()
              .getItems()
              .get(0)
              .getMetadata()
              .getName();

      portForward = client.pods().inNamespace(MY_SQL_NS).withName(podName).portForward(3306, 3306);
    }

    MySQLSchema testSchema = new MySQLSchema();
    testSchema.setMetadata(
        new ObjectMetaBuilder().withName("mydb1").withNamespace(operator.getNamespace()).build());
    testSchema.setSpec(new SchemaSpec());
    testSchema.getSpec().setEncoding("utf8");

    log.info("Creating test MySQLSchema object: {}", testSchema);
    client.resource(testSchema).createOrReplace();

    log.info("Waiting 2 minutes for expected resources to be created and updated");
    await()
        .atMost(2, MINUTES)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              MySQLSchema updatedSchema =
                  client
                      .resources(MySQLSchema.class)
                      .inNamespace(operator.getNamespace())
                      .withName(testSchema.getMetadata().getName())
                      .get();
              assertThat(updatedSchema.getStatus(), is(notNullValue()));
              assertThat(updatedSchema.getStatus().getStatus(), equalTo("CREATED"));
              assertThat(updatedSchema.getStatus().getSecretName(), is(notNullValue()));
              assertThat(updatedSchema.getStatus().getUserName(), is(notNullValue()));
            });

    if (portForward != null) {
      portForward.close();
    }
  }
}
