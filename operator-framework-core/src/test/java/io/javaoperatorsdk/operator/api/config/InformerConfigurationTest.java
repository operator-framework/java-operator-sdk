package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Constants;

import static org.junit.jupiter.api.Assertions.*;

class InformerConfigurationTest {

  @Test
  void allNamespacesWatched() {
    assertThrows(
        IllegalArgumentException.class, () -> InformerConfiguration.allNamespacesWatched(null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            InformerConfiguration.allNamespacesWatched(
                Set.of(Constants.WATCH_CURRENT_NAMESPACE, Constants.WATCH_ALL_NAMESPACES, "foo")));
    assertThrows(
        IllegalArgumentException.class,
        () -> InformerConfiguration.allNamespacesWatched(Collections.emptySet()));
    assertFalse(InformerConfiguration.allNamespacesWatched(Set.of("foo", "bar")));
    assertTrue(InformerConfiguration.allNamespacesWatched(Set.of(Constants.WATCH_ALL_NAMESPACES)));
    assertFalse(InformerConfiguration.allNamespacesWatched(Set.of("foo")));
    assertFalse(
        InformerConfiguration.allNamespacesWatched(Set.of(Constants.WATCH_CURRENT_NAMESPACE)));
  }

  @Test
  void currentNamespaceWatched() {
    assertThrows(
        IllegalArgumentException.class, () -> InformerConfiguration.currentNamespaceWatched(null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            InformerConfiguration.currentNamespaceWatched(
                Set.of(Constants.WATCH_CURRENT_NAMESPACE, Constants.WATCH_ALL_NAMESPACES, "foo")));
    assertThrows(
        IllegalArgumentException.class,
        () -> InformerConfiguration.currentNamespaceWatched(Collections.emptySet()));
    assertFalse(InformerConfiguration.currentNamespaceWatched(Set.of("foo", "bar")));
    assertFalse(
        InformerConfiguration.currentNamespaceWatched(Set.of(Constants.WATCH_ALL_NAMESPACES)));
    assertFalse(InformerConfiguration.currentNamespaceWatched(Set.of("foo")));
    assertTrue(
        InformerConfiguration.currentNamespaceWatched(Set.of(Constants.WATCH_CURRENT_NAMESPACE)));
  }

  @Test
  void nullLabelSelectorByDefault() {
    final var informerConfig = InformerConfiguration.builder(ConfigMap.class).build();
    assertNull(informerConfig.getLabelSelector());
  }

  @Test
  void shouldWatchAllNamespacesByDefaultForControllers() {
    final var informerConfig = InformerConfiguration.builder(ConfigMap.class).buildForController();
    assertTrue(informerConfig.watchAllNamespaces());
  }

  @Test
  void shouldFollowControllerNamespacesByDefaultForInformerEventSource() {
    final var informerConfig = InformerConfiguration.builder(ConfigMap.class).build();
    assertTrue(informerConfig.getFollowControllerNamespaceChanges());
  }

  @Test
  void failIfNotValid() {
    assertThrows(IllegalArgumentException.class, () -> InformerConfiguration.failIfNotValid(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> InformerConfiguration.failIfNotValid(Collections.emptySet()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            InformerConfiguration.failIfNotValid(
                Set.of(Constants.WATCH_CURRENT_NAMESPACE, Constants.WATCH_ALL_NAMESPACES, "foo")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            InformerConfiguration.failIfNotValid(Set.of(Constants.WATCH_CURRENT_NAMESPACE, "foo")));
    assertThrows(
        IllegalArgumentException.class,
        () -> InformerConfiguration.failIfNotValid(Set.of(Constants.WATCH_ALL_NAMESPACES, "foo")));

    // should work
    InformerConfiguration.failIfNotValid(Set.of("foo", "bar"));
  }
}
