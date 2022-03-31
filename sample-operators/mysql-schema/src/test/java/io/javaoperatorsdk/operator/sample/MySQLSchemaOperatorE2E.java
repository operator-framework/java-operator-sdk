package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.core.ConditionFactory;
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
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MySQLSchemaOperatorE2E {

  static final Logger log = LoggerFactory.getLogger(MySQLSchemaOperatorE2E.class);

  static final KubernetesClient client = new DefaultKubernetesClient();

  static final String MY_SQL_NS = "mysql";

  private final static List<HasMetadata> infrastructure = new ArrayList<>();
  public static final String TEST_RESOURCE_NAME = "mydb1";
  public static final Integer LOCAL_PORT = 3307;
  public static final MySQLDbConfig MY_SQL_DB_CONFIG =
      new MySQLDbConfig("127.0.0.1", LOCAL_PORT.toString(), "root",
          "password");

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
                          700, MY_SQL_DB_CONFIG)))
              .withInfrastructure(infrastructure)
              .awaitInfrastructure(MySQLSchemaOperatorE2E::DatabaseAvailable)
              .withPortForward(MY_SQL_NS, "app", "mysql", 3306, LOCAL_PORT)
              .build()
          : E2EOperatorExtension.builder()
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).get())
              .withInfrastructure(infrastructure)
              .awaitInfrastructure(MySQLSchemaOperatorE2E::DatabaseAvailable)
              .build();

  public MySQLSchemaOperatorE2E() throws FileNotFoundException {}

  private static void DatabaseAvailable(ConditionFactory awaiter) {
    var service = new SchemaService(MY_SQL_DB_CONFIG);
    awaiter.atMost(2, MINUTES).ignoreExceptionsInstanceOf(IllegalStateException.class)
        .untilAsserted(() -> {
          service.getSchema("foo");
        });
  }


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
    final var hasBeenErrored = new AtomicBoolean();
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
              if (updatedSchema.getStatus().getStatus().startsWith("ERROR")) {
                hasBeenErrored.set(true);
              }
              assertThat(updatedSchema.getStatus().getStatus(), equalTo("CREATED"));
              assertThat(updatedSchema.getStatus().getSecretName(), is(notNullValue()));
              assertThat(updatedSchema.getStatus().getUserName(), is(notNullValue()));
            });

    assertFalse(hasBeenErrored.get(), "Should never been errored");
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
