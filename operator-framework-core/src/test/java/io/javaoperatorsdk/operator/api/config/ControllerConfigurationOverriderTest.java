package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.BasicItemStore;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration.inheritsNamespacesFromController;
import static org.junit.jupiter.api.Assertions.*;

class ControllerConfigurationOverriderTest {
  private final BaseConfigurationService configurationService = new BaseConfigurationService();

  @SuppressWarnings("unchecked")
  private static Object extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    final var spec =
        configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().get(index);
    return configuration.getConfigurationFor(spec);
  }

  @BeforeEach
  void clearStaticState() {
    DependentResourceConfigurationResolver.clear();
  }

  @Test
  void overridingNSShouldPreserveUntouchedDependents() {
    var configuration = createConfiguration(new NamedDependentReconciler());

    // check that we have the proper number of dependent configs
    var dependentResources =
        configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertEquals(2, dependentResources.size());

    // override the NS
    final var namespace = "some-ns";
    final var externalDRName =
        DependentResource.defaultNameFor(NamedDependentReconciler.ExternalDependentResource.class);
    final var stringConfig = "some String configuration";
    configuration =
        ControllerConfigurationOverrider.override(configuration)
            .settingNamespace(namespace)
            .replacingNamedDependentResourceConfig(externalDRName, stringConfig)
            .build();
    assertEquals(Set.of(namespace), configuration.getInformerConfig().getNamespaces());

    // check that we still have the proper number of dependent configs
    dependentResources = configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertEquals(2, dependentResources.size());
    final var resourceConfig = extractDependentKubernetesResourceConfig(configuration, 1);
    assertEquals(stringConfig, resourceConfig);
  }

  @SuppressWarnings({"rawtypes"})
  private KubernetesDependentResourceConfig extractFirstDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration) {
    return (KubernetesDependentResourceConfig)
        extractDependentKubernetesResourceConfig(configuration, 0);
  }

  private io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> createConfiguration(
      Reconciler<?> reconciler) {
    return configurationService.configFor(reconciler);
  }

  @Test
  void overridingNamespacesShouldNotThrowNPE() {
    var configuration = createConfiguration(new NullReconciler());
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespaces().build();
    assertTrue(configuration.getInformerConfig().watchAllNamespaces());
  }

  private static class NullReconciler implements Reconciler<HasMetadata> {
    @Override
    public UpdateControl<HasMetadata> reconcile(HasMetadata resource, Context<HasMetadata> context)
        throws Exception {
      return null;
    }
  }

  @Test
  void overridingNamespacesShouldWork() {
    var configuration = createConfiguration(new WatchCurrentReconciler());
    var informerConfig = configuration.getInformerConfig();
    assertEquals(Set.of("foo"), informerConfig.getNamespaces());
    assertFalse(informerConfig.watchAllNamespaces());
    assertFalse(informerConfig.watchCurrentNamespace());

    configuration =
        ControllerConfigurationOverrider.override(configuration)
            .addingNamespaces("foo", "bar")
            .build();
    informerConfig = configuration.getInformerConfig();
    assertEquals(Set.of("foo", "bar"), informerConfig.getNamespaces());
    assertFalse(informerConfig.watchAllNamespaces());
    assertFalse(informerConfig.watchCurrentNamespace());

    configuration =
        ControllerConfigurationOverrider.override(configuration).removingNamespaces("bar").build();
    informerConfig = configuration.getInformerConfig();
    assertEquals(Set.of("foo"), informerConfig.getNamespaces());
    assertFalse(informerConfig.watchAllNamespaces());
    assertFalse(informerConfig.watchCurrentNamespace());

    configuration =
        ControllerConfigurationOverrider.override(configuration).removingNamespaces("foo").build();
    informerConfig = configuration.getInformerConfig();
    assertTrue(informerConfig.watchAllNamespaces());
    assertFalse(informerConfig.watchCurrentNamespace());

    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace("foo").build();
    informerConfig = configuration.getInformerConfig();
    assertFalse(informerConfig.watchAllNamespaces());
    assertFalse(informerConfig.watchCurrentNamespace());
    assertEquals(Set.of("foo"), informerConfig.getNamespaces());

    configuration =
        ControllerConfigurationOverrider.override(configuration)
            .watchingOnlyCurrentNamespace()
            .build();
    informerConfig = configuration.getInformerConfig();
    assertFalse(informerConfig.watchAllNamespaces());
    assertTrue(informerConfig.watchCurrentNamespace());

    configuration =
        ControllerConfigurationOverrider.override(configuration).watchingAllNamespaces().build();
    informerConfig = configuration.getInformerConfig();
    assertTrue(informerConfig.watchAllNamespaces());
    assertFalse(informerConfig.watchCurrentNamespace());
  }

  @Test
  void itemStorePreserved() {
    var configuration = createConfiguration(new WatchCurrentReconciler());

    configuration = ControllerConfigurationOverrider.override(configuration).build();

    assertNotNull(configuration.getInformerConfig().getItemStore());
  }

  @Test
  void configuredDependentShouldNotChangeOnParentOverrideEvenWhenInitialConfigIsSame() {
    var configuration = createConfiguration(new OverriddenNSOnDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var kubeDependentConfig = extractFirstDependentKubernetesResourceConfig(configuration);

    // override the parent NS to match the dependent's
    configuration =
        ControllerConfigurationOverrider.override(configuration)
            .settingNamespace(OverriddenNSDependent.DEP_NS)
            .build();
    assertEquals(
        Set.of(OverriddenNSDependent.DEP_NS), configuration.getInformerConfig().getNamespaces());

    // check that the DependentResource inherits has its own configured NS
    assertEquals(
        Set.of(OverriddenNSDependent.DEP_NS), kubeDependentConfig.informerConfig().getNamespaces());

    // override the parent's NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still using its own NS
    kubeDependentConfig = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(
        Set.of(OverriddenNSDependent.DEP_NS), kubeDependentConfig.informerConfig().getNamespaces());
  }

  @SuppressWarnings("unchecked")
  @Test
  void dependentShouldWatchAllNamespacesIfParentDoesAsWell() {
    var configuration = createConfiguration(new WatchAllNamespacesReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // check that the DependentResource inherits the controller's configuration if applicable
    var informerConfig = config.informerConfig();
    assertTrue(inheritsNamespacesFromController(informerConfig.getNamespaces()));
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldBePossibleToForceDependentToWatchAllNamespaces() {
    var configuration = createConfiguration(new DependentWatchesAllNSReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // check that the DependentResource inherits the controller's configuration if applicable
    assertTrue(InformerConfiguration.allNamespacesWatched(config.informerConfig().getNamespaces()));

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still configured to watch all NS
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertTrue(InformerConfiguration.allNamespacesWatched(config.informerConfig().getNamespaces()));
  }

  @Test
  void overridingNamespacesShouldBePropagatedToDependentsWithDefaultConfig() {
    var configuration = createConfiguration(new OneDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.informerConfig().getNamespaces().size());
  }

  @Test
  void alreadyOverriddenDependentNamespacesShouldNotBePropagated() {
    var configuration = createConfiguration(new OverriddenNSOnDepReconciler());
    // retrieve the config for the first (and unique) dependent
    var config = extractFirstDependentKubernetesResourceConfig(configuration);

    // DependentResource has its own NS
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.informerConfig().getNamespaces());

    // override the NS
    final var newNS = "bar";
    configuration =
        ControllerConfigurationOverrider.override(configuration).settingNamespace(newNS).build();

    // check that dependent config is still using its own NS
    config = extractFirstDependentKubernetesResourceConfig(configuration);
    assertEquals(Set.of(OverriddenNSDependent.DEP_NS), config.informerConfig().getNamespaces());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void replaceNamedDependentResourceConfigShouldWork() {
    var configuration = createConfiguration(new OneDepReconciler());
    var dependents = configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());

    final var dependentResourceName = DependentResource.defaultNameFor(ReadOnlyDependent.class);
    assertTrue(dependents.stream().anyMatch(dr -> dr.getName().equals(dependentResourceName)));

    var dependentSpec =
        dependents.stream()
            .filter(dr -> dr.getName().equals(dependentResourceName))
            .findFirst()
            .orElseThrow();
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = extractFirstDependentKubernetesResourceConfig(configuration);
    assertNotNull(maybeConfig);
    assertInstanceOf(KubernetesDependentResourceConfig.class, maybeConfig);

    var config = (KubernetesDependentResourceConfig) maybeConfig;
    // check that the DependentResource inherits the controller's configuration if applicable
    var informerConfig = config.informerConfig();
    assertEquals(1, informerConfig.getNamespaces().size());
    assertNull(informerConfig.getLabelSelector());

    // override the namespaces for the dependent resource
    final var overriddenNS = "newNS";
    final var labelSelector = "foo=bar";
    final var overridingInformerConfig =
        InformerConfiguration.builder(ConfigMap.class)
            .withNamespaces(Set.of(overriddenNS))
            .withLabelSelector(labelSelector)
            .build();
    final var overridden =
        ControllerConfigurationOverrider.override(configuration)
            .replacingNamedDependentResourceConfig(
                dependentResourceName,
                new KubernetesDependentResourceConfigBuilder<ConfigMap>()
                    .withKubernetesDependentInformerConfig(overridingInformerConfig)
                    .build())
            .build();
    dependents = overridden.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    dependentSpec =
        dependents.stream()
            .filter(dr -> dr.getName().equals(dependentResourceName))
            .findFirst()
            .orElseThrow();
    config = (KubernetesDependentResourceConfig) overridden.getConfigurationFor(dependentSpec);
    informerConfig = config.informerConfig();
    assertEquals(labelSelector, informerConfig.getLabelSelector());
    assertEquals(Set.of(overriddenNS), informerConfig.getNamespaces());
    // check that we still have the proper workflow configuration
    assertInstanceOf(TestCondition.class, dependentSpec.getReadyCondition());
  }

  private static class MyItemStore<T extends HasMetadata> extends BasicItemStore<T> {

    public MyItemStore() {
      super(Cache::metaNamespaceKeyFunc);
    }
  }

  @ControllerConfiguration(informer = @Informer(namespaces = "foo", itemStore = MyItemStore.class))
  private static class WatchCurrentReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = @Dependent(type = ReadOnlyDependent.class))
  @ControllerConfiguration
  private static class WatchAllNamespacesReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = @Dependent(type = WatchAllNSDependent.class))
  @ControllerConfiguration
  private static class DependentWatchesAllNSReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  private static class TestCondition implements Condition<ConfigMap, ConfigMap> {

    @Override
    public boolean isMet(
        DependentResource<ConfigMap, ConfigMap> dependentResource,
        ConfigMap primary,
        Context<ConfigMap> context) {
      return true;
    }
  }

  @Workflow(
      dependents =
          @Dependent(type = ReadOnlyDependent.class, readyPostcondition = TestCondition.class))
  @ControllerConfiguration(informer = @Informer(namespaces = OneDepReconciler.CONFIGURED_NS))
  private static class OneDepReconciler implements Reconciler<ConfigMap> {

    private static final String CONFIGURED_NS = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  public static class ReadOnlyDependent extends KubernetesDependentResource<ConfigMap, ConfigMap>
      implements GarbageCollected<ConfigMap> {}

  @KubernetesDependent(informer = @Informer(namespaces = Constants.WATCH_ALL_NAMESPACES))
  public static class WatchAllNSDependent extends KubernetesDependentResource<ConfigMap, ConfigMap>
      implements GarbageCollected<ConfigMap> {}

  @Workflow(dependents = @Dependent(type = OverriddenNSDependent.class))
  @ControllerConfiguration(
      informer = @Informer(namespaces = OverriddenNSOnDepReconciler.CONFIGURED_NS))
  public static class OverriddenNSOnDepReconciler implements Reconciler<ConfigMap> {

    private static final String CONFIGURED_NS = "parentNS";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @KubernetesDependent(informer = @Informer(namespaces = OverriddenNSDependent.DEP_NS))
  public static class OverriddenNSDependent
      extends KubernetesDependentResource<ConfigMap, ConfigMap>
      implements GarbageCollected<ConfigMap> {

    private static final String DEP_NS = "dependentNS";
  }

  @Workflow(
      dependents = {
        @Dependent(type = NamedDependentReconciler.NamedDependentResource.class),
        @Dependent(type = NamedDependentReconciler.ExternalDependentResource.class)
      })
  @ControllerConfiguration
  public static class NamedDependentReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }

    private static class NamedDependentResource
        extends KubernetesDependentResource<ConfigMap, ConfigMap>
        implements GarbageCollected<ConfigMap> {}

    private static class ExternalDependentResource
        implements DependentResource<Object, ConfigMap>,
            ConfiguredDependentResource<String>,
            GarbageCollected<ConfigMap> {

      private String config = "UNSET";

      @Override
      public ReconcileResult<Object> reconcile(ConfigMap primary, Context<ConfigMap> context) {
        return null;
      }

      @Override
      public Class<Object> resourceType() {
        return Object.class;
      }

      @Override
      public void configureWith(String config) {
        this.config = config;
      }

      @Override
      public Optional<String> configuration() {
        return Optional.of(config);
      }

      @Override
      public void delete(ConfigMap primary, Context<ConfigMap> context) {}
    }
  }
}
