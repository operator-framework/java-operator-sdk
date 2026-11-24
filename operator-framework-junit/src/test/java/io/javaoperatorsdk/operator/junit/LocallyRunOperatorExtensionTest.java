/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.junit;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedConstruction;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocallyRunOperatorExtensionTest {
  private static final String CRD_NAME = "tests.crd.example";
  private final CustomResourceDefinition crd =
      new CustomResourceDefinitionBuilder()
          .withNewMetadata()
          .withName(CRD_NAME)
          .endMetadata()
          .build();
  private final ExtensionContext methodContext = mock(ExtensionContext.class);
  private final Map<Object, AutoCloseable> resources = new HashMap<>();
  private MockedConstruction<Operator> operators;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    final var classContext = mock(ExtensionContext.class);
    final var store = mock(ExtensionContext.Store.class);
    when(methodContext.getTestMethod()).thenReturn(Optional.of(mock(Method.class)));
    when(methodContext.getParent()).thenReturn(Optional.of(classContext));
    when(classContext.getStore(any())).thenReturn(store);
    when(store.computeIfAbsent(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final Function<Object, AutoCloseable> factory = invocation.getArgument(1);
              return resources.computeIfAbsent(invocation.getArgument(0), factory);
            });
    operators =
        mockConstruction(
            Operator.class,
            (operator, construction) -> {
              final var configure =
                  (Consumer<ConfigurationServiceOverrider>) construction.arguments().get(0);
              when(operator.getConfigurationService())
                  .thenReturn(ConfigurationService.newOverriddenConfigurationService(configure));
            });
  }

  @AfterEach
  void tearDown() throws Exception {
    try {
      closeClassContext();
    } finally {
      operators.close();
    }
  }

  @Test
  void doesNotEnableVirtualThreadsWhenNotRequested() {
    final var builder =
        LocallyRunOperatorExtension.builder().withKubernetesClient(mock(KubernetesClient.class));
    builder.useVirtualThreads = false;

    assertThat(builder.build().getOperator().getConfigurationService())
        .returns(false, ConfigurationService::useVirtualThreads);
  }

  @Test
  void virtualThreadsCanBeEnabledForTheWholeTestSuite() {
    final var builder =
        LocallyRunOperatorExtension.builder().withKubernetesClient(mock(KubernetesClient.class));
    builder.useVirtualThreads = true;

    assertThat(builder.build().getOperator().getConfigurationService())
        .returns(true, ConfigurationService::useVirtualThreads);
  }

  @Test
  void explicitConfigurationTakesPrecedenceOverTestSuiteWideVirtualThreadsSetting() {
    final var builder =
        LocallyRunOperatorExtension.builder()
            .withKubernetesClient(mock(KubernetesClient.class))
            .withConfigurationService(overrider -> overrider.withUseVirtualThreads(false));
    builder.useVirtualThreads = true;

    assertThat(builder.build().getOperator().getConfigurationService())
        .returns(false, ConfigurationService::useVirtualThreads);
  }

  @Test
  void infrastructureClientsRemainOpenUntilClassCleanup() throws Exception {
    final var clientFirst = kubernetesClient();
    final var clientSecond = kubernetesClient();
    runTest(locallyRunOperatorExtension(kubernetesClient(), clientFirst, true));
    runTest(locallyRunOperatorExtension(kubernetesClient(), clientSecond, true));

    verify(clientFirst, never()).close();
    verify(clientSecond, never()).close();
    verify(clientFirst.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME), never())
        .delete();
    verify(
            clientSecond.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME),
            never())
        .delete();

    closeClassContext();

    verifyDeletedBeforeClose(clientFirst);
    verifyDeletedBeforeClose(clientSecond);
  }

  @Test
  void sharedClientIsNotClosedByOperatorStop() throws Exception {
    final var client = kubernetesClient();
    final var extension = locallyRunOperatorExtension(client, client, true);

    runTest(extension);

    assertThat(extension.getOperator().getConfigurationService().closeClientOnStop()).isFalse();
    verify(client, never()).close();

    closeClassContext();

    verifyDeletedBeforeClose(client);
  }

  @Test
  void deletionDisabledStillClosesInfrastructureClient() throws Exception {
    final var client = kubernetesClient();
    runTest(locallyRunOperatorExtension(client, client, false));

    closeClassContext();

    verify(client.resource(crd), never()).delete();
    verify(client).close();
  }

  @Test
  void sharedClientWithExternalOwnershipRemainsOpen() throws Exception {
    final var client = kubernetesClient();
    runTest(
        locallyRunOperatorExtension(
            client, client, true, overrider -> overrider.withCloseClientOnStop(false)));

    closeClassContext();

    verify(client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME)).delete();
    verify(client, never()).close();
  }

  @SuppressWarnings("unchecked")
  private KubernetesClient kubernetesClient() {
    final var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    final NamespaceableResource<CustomResourceDefinition> resource =
        mock(NamespaceableResource.class, RETURNS_SELF);
    when(client.resource(crd)).thenReturn(resource);
    when(client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME))
        .thenReturn(resource);
    final Resource<Namespace> namespace = mock(Resource.class, RETURNS_SELF);
    when(client.namespaces().resource(any(Namespace.class))).thenReturn(namespace);
    return client;
  }

  private LocallyRunOperatorExtension locallyRunOperatorExtension(
      KubernetesClient kubernetesClient,
      KubernetesClient infrastructureKubernetesClient,
      boolean deleteCRDs) {
    return locallyRunOperatorExtension(
        kubernetesClient, infrastructureKubernetesClient, deleteCRDs, null);
  }

  private LocallyRunOperatorExtension locallyRunOperatorExtension(
      KubernetesClient kubernetesClient,
      KubernetesClient infrastructureKubernetesClient,
      boolean deleteCRDs,
      Consumer<ConfigurationServiceOverrider> configurationServiceOverrider) {
    return LocallyRunOperatorExtension.builder()
        .withKubernetesClient(kubernetesClient)
        .withInfrastructureKubernetesClient(infrastructureKubernetesClient)
        .withAdditionalCustomResourceDefinition(crd)
        .withDeleteCRDs(deleteCRDs)
        .withConfigurationService(configurationServiceOverrider)
        .withNamespaceNameSupplier(context -> "test")
        .skipNamespaceDeletion(true)
        .build();
  }

  private void runTest(LocallyRunOperatorExtension extension) {
    extension.beforeEach(methodContext);
    extension.afterEach(methodContext);
    verify(extension.getOperator()).stop();
    verify(extension.getInfrastructureKubernetesClient().resource(crd)).serverSideApply();
  }

  private void closeClassContext() throws Exception {
    for (var resource : resources.values()) {
      resource.close();
    }
    resources.clear();
  }

  private static void verifyDeletedBeforeClose(KubernetesClient client) {
    final var resource = client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME);
    final var order = inOrder(resource, client);
    order.verify(resource).delete();
    order.verify(client).close();
  }
}
