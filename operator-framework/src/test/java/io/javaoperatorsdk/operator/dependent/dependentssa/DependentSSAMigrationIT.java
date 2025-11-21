package io.javaoperatorsdk.operator.dependent.dependentssa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Migrating Dependent Resources from Legacy to SSA",
    description =
        """
        Demonstrates migrating dependent resource management from legacy update methods to \
        Server-Side Apply (SSA). Tests show bidirectional migration scenarios and field manager \
        handling, including using the default fabric8 field manager to avoid creating duplicate \
        managed field entries during migration.
        """)
class DependentSSAMigrationIT {

  public static final String FABRIC8_CLIENT_DEFAULT_FIELD_MANAGER = "fabric8-kubernetes-client";
  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String INITIAL_VALUE = "INITIAL_VALUE";
  public static final String CHANGED_VALUE = "CHANGED_VALUE";

  private String namespace;
  private final KubernetesClient client = new KubernetesClientBuilder().build();

  @BeforeEach
  void setup(TestInfo testInfo) {
    SSAConfigMapDependent.NUMBER_OF_UPDATES.set(0);
    LocallyRunOperatorExtension.applyCrd(DependentSSACustomResource.class, client);
    testInfo
        .getTestMethod()
        .ifPresent(
            method -> {
              namespace = KubernetesResourceUtil.sanitizeName(method.getName());
              cleanup();
              client
                  .namespaces()
                  .resource(
                      new NamespaceBuilder()
                          .withMetadata(new ObjectMetaBuilder().withName(namespace).build())
                          .build())
                  .create();
            });
  }

  @AfterEach
  void cleanup() {
    client
        .namespaces()
        .resource(
            new NamespaceBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(namespace).build())
                .build())
        .delete();
  }

  @Test
  void migratesFromLegacyToWorksAndBack() {
    var legacyOperator = createOperator(client, true, null);
    DependentSSACustomResource testResource = reconcileWithLegacyOperator(legacyOperator);

    var operator = createOperator(client, false, null);
    testResource = reconcileWithNewApproach(testResource, operator);
    var cm = getDependentConfigMap();
    assertThat(cm.getMetadata().getManagedFields()).hasSize(2);

    reconcileAgainWithLegacy(legacyOperator, testResource);
  }

  @Test
  void usingDefaultFieldManagerDoesNotCreatesANewOneWithApplyOperation() {
    var legacyOperator = createOperator(client, true, null);
    DependentSSACustomResource testResource = reconcileWithLegacyOperator(legacyOperator);

    var operator = createOperator(client, false, FABRIC8_CLIENT_DEFAULT_FIELD_MANAGER);
    reconcileWithNewApproach(testResource, operator);

    var cm = getDependentConfigMap();

    assertThat(cm.getMetadata().getManagedFields()).hasSize(2);
    assertThat(cm.getMetadata().getManagedFields())
        // Jetty seems to be a bug in fabric8 client, it is only the default fieldManager if Jetty
        // is used as http client
        .allMatch(
            fm ->
                fm.getManager().equals(FABRIC8_CLIENT_DEFAULT_FIELD_MANAGER)
                    || fm.getManager().equals("Jetty"));
  }

  private void reconcileAgainWithLegacy(
      Operator legacyOperator, DependentSSACustomResource testResource) {
    legacyOperator.start();

    testResource.getSpec().setValue(INITIAL_VALUE);
    testResource.getMetadata().setResourceVersion(null);
    client.resource(testResource).update();

    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm.getData()).containsEntry(SSAConfigMapDependent.DATA_KEY, INITIAL_VALUE);
            });

    legacyOperator.stop();
  }

  private DependentSSACustomResource reconcileWithNewApproach(
      DependentSSACustomResource testResource, Operator operator) {
    operator.start();

    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm).isNotNull();
              assertThat(cm.getData()).hasSize(1);
            });

    testResource.getSpec().setValue(CHANGED_VALUE);
    testResource.getMetadata().setResourceVersion(null);
    testResource = client.resource(testResource).update();

    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm.getData()).containsEntry(SSAConfigMapDependent.DATA_KEY, CHANGED_VALUE);
            });
    operator.stop();
    return testResource;
  }

  private ConfigMap getDependentConfigMap() {
    return client.configMaps().inNamespace(namespace).withName(TEST_RESOURCE_NAME).get();
  }

  private DependentSSACustomResource reconcileWithLegacyOperator(Operator legacyOperator) {
    legacyOperator.start();

    var testResource = client.resource(testResource()).create();

    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm).isNotNull();
              assertThat(cm.getMetadata().getManagedFields()).hasSize(1);
              assertThat(cm.getData()).hasSize(1);
            });

    legacyOperator.stop();
    return testResource;
  }

  private Operator createOperator(
      KubernetesClient client, boolean legacyDependentHandling, String fieldManager) {
    Operator operator =
        new Operator(o -> o.withKubernetesClient(client).withCloseClientOnStop(false));
    var reconciler = new DependentSSAReconciler(!legacyDependentHandling);
    operator.register(
        reconciler,
        o -> {
          o.settingNamespace(namespace);
          if (fieldManager != null) {
            o.withFieldManager(fieldManager);
          }
        });
    return operator;
  }

  public DependentSSACustomResource testResource() {
    DependentSSACustomResource resource = new DependentSSACustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder().withNamespace(namespace).withName(TEST_RESOURCE_NAME).build());
    resource.setSpec(new DependentSSASpec());
    resource.getSpec().setValue(INITIAL_VALUE);
    return resource;
  }
}
