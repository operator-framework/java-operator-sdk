package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
class GenericResourceUpdaterTest {

  private static final Context context = mock(Context.class);

  @BeforeAll
  static void setUp() {
    final var controllerConfiguration = mock(ControllerConfiguration.class);
    final var configService = mock(ConfigurationService.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configService);

    final var client = MockKubernetesClient.client(HasMetadata.class);
    when(configService.getKubernetesClient()).thenReturn(client);
    when(configService.getResourceCloner()).thenCallRealMethod();
    when(context.getClient()).thenReturn(client);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
  }

  @Test
  void preservesValues() {
    var desired = createDeployment();
    var actual = createDeployment();
    actual.getMetadata().setLabels(new HashMap<>());
    actual.getMetadata().getLabels().put("additionalActualKey", "value");
    actual.getMetadata().setResourceVersion("1234");
    actual.getSpec().setRevisionHistoryLimit(5);

    var result = GenericResourceUpdater.updateResource(actual, desired, context);

    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec().getRevisionHistoryLimit()).isEqualTo(10);
  }

  @Test
  void checkNamespaces() {
    var desired = new NamespaceBuilder().withNewMetadata().withName("foo").endMetadata().build();
    var actual = new NamespaceBuilder().withNewMetadata().withName("foo").endMetadata().build();
    actual.getMetadata().setLabels(new HashMap<>());
    actual.getMetadata().getLabels().put("additionalActualKey", "value");
    actual.getMetadata().setResourceVersion("1234");

    var result = GenericResourceUpdater.updateResource(actual, desired, context);
    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");

    desired.setSpec(new NamespaceSpec(List.of("halkyon.io/finalizer")));

    result = GenericResourceUpdater.updateResource(actual, desired, context);
    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec().getFinalizers()).containsExactly("halkyon.io/finalizer");

    desired = new NamespaceBuilder().withNewMetadata().withName("foo").endMetadata().build();

    result = GenericResourceUpdater.updateResource(actual, desired, context);
    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec()).isNull();
  }

  @Test
  void checkSecret() {
    var desired =
        new SecretBuilder()
            .withMetadata(new ObjectMeta())
            .withImmutable()
            .withType("Opaque")
            .addToData("foo", "bar")
            .build();
    var actual = new SecretBuilder().withMetadata(new ObjectMeta()).build();

    final var secret = GenericResourceUpdater.updateResource(actual, desired, context);
    assertThat(secret.getImmutable()).isTrue();
    assertThat(secret.getType()).isEqualTo("Opaque");
    assertThat(secret.getData()).containsOnlyKeys("foo");
  }

  @Test
  void checkServiceAccount() {
    var desired =
        new ServiceAccountBuilder()
            .withMetadata(new ObjectMetaBuilder().addToLabels("new", "label").build())
            .build();
    var actual =
        new ServiceAccountBuilder()
            .withMetadata(new ObjectMetaBuilder().addToLabels("a", "label").build())
            .withImagePullSecrets(new LocalObjectReferenceBuilder().withName("secret").build())
            .build();

    final var serviceAccount = GenericResourceUpdater.updateResource(actual, desired, context);
    assertThat(serviceAccount.getMetadata().getLabels())
        .isEqualTo(Map.of("a", "label", "new", "label"));
    assertThat(serviceAccount.getImagePullSecrets()).isNullOrEmpty();
  }

  Deployment createDeployment() {
    return ReconcilerUtils.loadYaml(
        Deployment.class, GenericResourceUpdaterTest.class, "nginx-deployment.yaml");
  }
}
