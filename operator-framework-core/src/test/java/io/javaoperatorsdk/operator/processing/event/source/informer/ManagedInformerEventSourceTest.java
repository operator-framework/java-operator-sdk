package io.javaoperatorsdk.operator.processing.event.source.informer;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ManagedInformerEventSourceTest {
  @Test
  void sourcesWithSameConfigurationShouldShareInformer() {
    final var namespaces = new String[] {"foo", "bar"};
    final var config = InformerConfiguration.from(mock(ConfigurationService.class),
        HasMetadata.class)
        .withLabelSelector("label=value").withNamespaces(namespaces)
        .build();

    final var informer1 =
        new InformerEventSource<>(config, MockKubernetesClient.client(HasMetadata.class));
    final var informer2 =
        new InformerEventSource<>(config, MockKubernetesClient.client(HasMetadata.class));

    final var manager = informer1.manager();
    assertEquals(manager, informer2.manager());
    assertEquals(namespaces.length, manager.numberOfSources());
  }

  @Test
  void sourcesWithDifferentConfigurationsShouldNotShareInformer() {
    final var config1 = InformerConfiguration.from(mock(ConfigurationService.class),
        HasMetadata.class)
        .withLabelSelector("label=value").withNamespaces("foo", "bar")
        .build();

    final var config2 = InformerConfiguration.from(config1)
        .withLabelSelector("label=otherValue").withNamespaces("baz")
        .build();

    final var informer1 =
        new InformerEventSource<>(config1, MockKubernetesClient.client(HasMetadata.class));
    final var informer2 =
        new InformerEventSource<>(config2, MockKubernetesClient.client(HasMetadata.class));

    assertNotEquals(informer1.manager(), informer2.manager());
    assertEquals(1, informer2.manager().numberOfSources());
  }
}
