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
import static org.hamcrest.Matchers.nullValue;

class MySQLSchemaOperatorE2E {

  static final Logger log = LoggerFactory.getLogger(MySQLSchemaOperatorE2E.class);

  static final KubernetesClient client = new DefaultKubernetesClient();

  static final String MY_SQL_NS = "mysql";

  private final static List<HasMetadata> infrastructure = new ArrayList<>();
  public static final String TEST_RESOURCE_NAME = "mydb1";
  public static final Integer LOCAL_PORT = 3307;

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
              .withReconciler(
                  new MySQLSchemaReconciler(),
                  c -> c.replacingNamedDependentResourceConfig(
                      SchemaDependentResource.NAME,
                      new ResourcePollerConfig(
                          700, new MySQLDbConfig("127.0.0.1", LOCAL_PORT.toString(), "root",
                              "password"))))
              .withInfrastructure(infrastructure)
              .withPortForward(MY_SQL_NS, "app", "mysql", 3306, LOCAL_PORT)
              .build()
          : E2EOperatorExtension.builder()
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).get())
              .withInfrastructure(infrastructure)
              .build();

  public MySQLSchemaOperatorE2E() throws FileNotFoundException {}

  @Test
  void test() throws IOException {

    MySQLSchema testSchema = new MySQLSchema();
    testSchema.setMetadata(
        new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).withNamespace(operator.getNamespace())
            .build());
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

    client.resources(MySQLSchema.class).inNamespace(operator.getNamespace())
        .withName(testSchema.getMetadata().getName()).delete();

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
              assertThat(updatedSchema, is(nullValue()));
            });
  }
}
