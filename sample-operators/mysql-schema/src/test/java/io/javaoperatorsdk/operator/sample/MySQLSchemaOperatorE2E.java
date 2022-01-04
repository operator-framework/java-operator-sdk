package io.javaoperatorsdk.operator.sample;

import java.io.File;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
      operator.register(new MySQLSchemaReconciler(client, dbConfig));
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

    if ("true".equals(System.getenv("DEPLOY_MY_SQL_SERVER"))) {
      log.info("Deploying MySQL server");
      deployMySQLServer(client);
    }

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

  private void deployMySQLServer(KubernetesClient client) throws IOException {
    Namespace mysql = new NamespaceBuilder().withMetadata(
        new ObjectMetaBuilder().withName(MY_SQL_NS).build()).build();

    if (mysql != null) {
      log.info("Cleanup: deleting mysql namespace {}", MY_SQL_NS);
      client.namespaces().delete(mysql);
      await().atMost(5, MINUTES)
          .until(() -> client.namespaces().withName(MY_SQL_NS).get() == null);
    }
    client.namespaces().create(mysql);

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Deployment deployment =
        mapper.readValue(new File("k8s/mysql-deployment.yaml"), Deployment.class);
    deployment.getMetadata().setNamespace(MY_SQL_NS);
    Service service = mapper.readValue(new File("k8s/mysql-service.yaml"), Service.class);
    service.getMetadata().setNamespace(MY_SQL_NS);
    client.resource(deployment).createOrReplace();
    client.resource(service).createOrReplace();

    log.info("Waiting for MySQL server to start");
    await().atMost(5, MINUTES).until(() -> {
      Deployment mysqlDeployment = client.apps().deployments().inNamespace(MY_SQL_NS)
          .withName(deployment.getMetadata().getName()).get();
      return mysqlDeployment.getStatus().getReadyReplicas() != null
          && mysqlDeployment.getStatus().getReadyReplicas() == 1;
    });
  }

}
