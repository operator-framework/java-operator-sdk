package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Constants;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigurationTest {

  public static final ResourceConfiguration<HasMetadata> DEFAULT =
      () -> InformerConfiguration.builder().buildForInformerEventSource();

  @Test
  void allNamespacesWatched() {
    assertThrows(IllegalArgumentException.class,
        () -> ResourceConfiguration.allNamespacesWatched(null));
    assertThrows(IllegalArgumentException.class, () -> ResourceConfiguration.allNamespacesWatched(
        Set.of(Constants.WATCH_CURRENT_NAMESPACE, Constants.WATCH_ALL_NAMESPACES, "foo")));
    assertThrows(IllegalArgumentException.class, () -> ResourceConfiguration.allNamespacesWatched(
        Collections.emptySet()));
    assertFalse(ResourceConfiguration.allNamespacesWatched(Set.of("foo", "bar")));
    assertTrue(ResourceConfiguration.allNamespacesWatched(Set.of(Constants.WATCH_ALL_NAMESPACES)));
    assertFalse(ResourceConfiguration.allNamespacesWatched(Set.of("foo")));
    assertFalse(
        ResourceConfiguration.allNamespacesWatched(Set.of(Constants.WATCH_CURRENT_NAMESPACE)));
  }

  @Test
  void currentNamespaceWatched() {
    assertThrows(IllegalArgumentException.class,
        () -> ResourceConfiguration.currentNamespaceWatched(null));
    assertThrows(IllegalArgumentException.class,
        () -> ResourceConfiguration.currentNamespaceWatched(
            Set.of(Constants.WATCH_CURRENT_NAMESPACE, Constants.WATCH_ALL_NAMESPACES, "foo")));
    assertThrows(IllegalArgumentException.class,
        () -> ResourceConfiguration.currentNamespaceWatched(Collections.emptySet()));
    assertFalse(ResourceConfiguration.currentNamespaceWatched(Set.of("foo", "bar")));
    assertFalse(
        ResourceConfiguration.currentNamespaceWatched(Set.of(Constants.WATCH_ALL_NAMESPACES)));
    assertFalse(ResourceConfiguration.currentNamespaceWatched(Set.of("foo")));
    assertTrue(
        ResourceConfiguration.currentNamespaceWatched(Set.of(Constants.WATCH_CURRENT_NAMESPACE)));
  }

  @Test
  void nullLabelSelectorByDefault() {
    assertNull(DEFAULT.getLabelSelector());
  }

  // todo: fix me
  @Disabled
  @Test
  void shouldWatchAllNamespacesByDefault() {
    assertTrue(DEFAULT.watchAllNamespaces());
  }

  @Test
  void failIfNotValid() {
    assertThrows(IllegalArgumentException.class, () -> ResourceConfiguration.failIfNotValid(null));
    assertThrows(IllegalArgumentException.class,
        () -> ResourceConfiguration.failIfNotValid(Collections.emptySet()));
    assertThrows(IllegalArgumentException.class, () -> ResourceConfiguration.failIfNotValid(
        Set.of(Constants.WATCH_CURRENT_NAMESPACE, Constants.WATCH_ALL_NAMESPACES, "foo")));
    assertThrows(IllegalArgumentException.class, () -> ResourceConfiguration.failIfNotValid(
        Set.of(Constants.WATCH_CURRENT_NAMESPACE, "foo")));
    assertThrows(IllegalArgumentException.class, () -> ResourceConfiguration.failIfNotValid(
        Set.of(Constants.WATCH_ALL_NAMESPACES, "foo")));

    // should work
    ResourceConfiguration.failIfNotValid(Set.of("foo", "bar"));
  }
}
