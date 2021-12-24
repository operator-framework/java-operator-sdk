package io.javaoperatorsdk.operator.sample;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public class MySQLSchemaOperatorE2E {

  final static String TEST_NS = "mysql-schema-test";
  final static String MY_SQL_NS = "mysql";

  final static Logger log = LoggerFactory.getLogger(MySQLSchemaOperatorE2E.class);

  @Test
  public void test() throws IOException {
    Config config = new ConfigBuilder().withNamespace(null).build();
    KubernetesClient client = new DefaultKubernetesClient(config);

    // Use this if you want to run the test without deploying the Operator to Kubernetes
    if ("true".equals(System.getenv("RUN_OPERATOR_IN_TEST"))) {
      Operator operator = new Operator(client, DefaultConfigurationService.instance());
      MySQLDbConfig dbConfig = new MySQLDbConfig("mysql", null, "root", "password");
      operator.register(new MySQLSchemaReconciler(dbConfig));
      operator.start();
    }

    MySQLSchema testSchema = new MySQLSchema();
    testSchema.setMetadata(new ObjectMetaBuilder()
        .withName("mydb1")
        .withNamespace(TEST_NS)
        .build());
    testSchema.setSpec(new SchemaSpec());
    testSchema.getSpec().setEncoding("utf8");

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
          .until(() -> client.namespaces().withName(TEST_NS).get() == null);
    }

    log.info("Creating test namespace {}", TEST_NS);
    client.namespaces().create(testNs);

    log.info("Creating test MySQLSchema object: {}", testSchema);
    client.resource(testSchema).createOrReplace();

    log.info("Waiting 5 minutes for expected resources to be created and updated");
    await().atMost(5, MINUTES).untilAsserted(() -> {
      MySQLSchema updatedSchema = client.resources(MySQLSchema.class).inNamespace(TEST_NS)
          .withName(testSchema.getMetadata().getName()).get();
      assertThat(updatedSchema.getStatus(), is(notNullValue()));
      assertThat(updatedSchema.getStatus().getStatus(), equalTo("CREATED"));
      assertThat(updatedSchema.getStatus().getSecretName(), is(notNullValue()));
      assertThat(updatedSchema.getStatus().getUserName(), is(notNullValue()));
    });
  }

}
