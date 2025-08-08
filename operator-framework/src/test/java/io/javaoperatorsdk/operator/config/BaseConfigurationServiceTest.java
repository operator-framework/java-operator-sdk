package io.javaoperatorsdk.operator.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.dependent.ConfigurationConverter;
import io.javaoperatorsdk.operator.api.config.dependent.Configured;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.dependent.dependentssa.DependentSSAReconciler;
import io.javaoperatorsdk.operator.dependent.readonly.ConfigMapReader;
import io.javaoperatorsdk.operator.dependent.readonly.ReadOnlyDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.BooleanWithUndefined;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimited;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;

import static io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval.DEFAULT_INTERVAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class BaseConfigurationServiceTest {

  // subclass to expose configFor method to this test class
  private static final class TestConfigurationService extends BaseConfigurationService {

    @Override
    protected <P extends HasMetadata>
        io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
            Reconciler<P> reconciler) {
      return super.configFor(reconciler);
    }
  }

  private final TestConfigurationService configurationService = new TestConfigurationService();

  private <P extends HasMetadata>
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
          Reconciler<P> reconciler) {
    // ensure that a new configuration is created each time
    return configurationService.configFor(reconciler);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static KubernetesDependentResourceConfig extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    final var spec =
        configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().get(index);
    return (KubernetesDependentResourceConfig) configuration.getConfigurationFor(spec);
  }

  @Test
  void test() {
    final var service = new BaseConfigurationService();
    final var config = service.getConfigurationFor(new NoDepReconciler());
    System.out.println(config);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void getDependentResources() {
    var configuration = configFor(new NoDepReconciler());

    var workflowSpec = configuration.getWorkflowSpec();
    assertTrue(workflowSpec.isEmpty());

    configuration = configFor(new OneDepReconciler());
    var dependents = configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    final var dependentResourceName = DependentResource.defaultNameFor(ReadOnlyDependent.class);
    assertTrue(dependents.stream().anyMatch(d -> d.getName().equals(dependentResourceName)));
    var dependentSpec = findByName(dependents, dependentResourceName);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = extractDependentKubernetesResourceConfig(configuration, 0);
    assertNotNull(maybeConfig);
    assertInstanceOf(KubernetesDependentResourceConfig.class, maybeConfig);
    final var config = (KubernetesDependentResourceConfig) maybeConfig;

    configuration = configFor(new NamedDepReconciler());
    dependents = configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    dependentSpec = findByName(dependents, NamedDepReconciler.NAME);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    maybeConfig = extractDependentKubernetesResourceConfig(configuration, 0);
    assertNotNull(maybeConfig);
    assertInstanceOf(KubernetesDependentResourceConfig.class, maybeConfig);
  }

  @Test
  void missingAnnotationCreatesDefaultConfig() {
    final var reconciler = new MissingAnnotationReconciler();
    var config = configFor(reconciler);

    assertThat(config.getName()).isEqualTo(ReconcilerUtils.getNameFor(reconciler));
    assertThat(config.getRetry()).isInstanceOf(GenericRetry.class);
    assertThat(config.getRateLimiter()).isInstanceOf(LinearRateLimiter.class);
    assertThat(config.maxReconciliationInterval()).hasValue(Duration.ofHours(DEFAULT_INTERVAL));
    assertThat(config.fieldManager()).isEqualTo(config.getName());
    assertThat(config.getFinalizerName())
        .isEqualTo(ReconcilerUtils.getDefaultFinalizerName(config.getResourceClass()));

    final var informerConfig = config.getInformerConfig();
    assertThat(informerConfig.getLabelSelector()).isNull();
    assertNull(informerConfig.getInformerListLimit());
    assertNull(informerConfig.getOnAddFilter());
    assertNull(informerConfig.getOnUpdateFilter());
    assertNull(informerConfig.getGenericFilter());
    assertNull(informerConfig.getItemStore());
    assertThat(informerConfig.getNamespaces()).isEqualTo(Constants.DEFAULT_NAMESPACES_SET);
  }

  @SuppressWarnings("rawtypes")
  private DependentResourceSpec findByName(
      List<DependentResourceSpec> dependentResourceSpecList, String name) {
    return dependentResourceSpecList.stream()
        .filter(d -> d.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @SuppressWarnings("rawtypes")
  private Optional<DependentResourceSpec> findByNameOptional(
      List<DependentResourceSpec> dependentResourceSpecList, String name) {
    return dependentResourceSpecList.stream().filter(d -> d.getName().equals(name)).findFirst();
  }

  @Test
  void tryingToAddDuplicatedDependentsWithoutNameShouldFail() {
    final var reconciler = new DuplicatedDepReconciler();
    assertThrows(IllegalArgumentException.class, () -> configFor(reconciler));
  }

  @Test
  void addingDuplicatedDependentsWithNameShouldWork() {
    var config = configFor(new NamedDuplicatedDepReconciler());
    var dependents = config.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertEquals(2, dependents.size());
    assertTrue(
        findByNameOptional(dependents, NamedDuplicatedDepReconciler.NAME).isPresent()
            && findByNameOptional(
                    dependents, DependentResource.defaultNameFor(ReadOnlyDependent.class))
                .isPresent());
  }

  @Test
  void maxIntervalCanBeConfigured() {
    var config = configFor(new MaxIntervalReconciler());
    assertEquals(50, config.maxReconciliationInterval().map(Duration::getSeconds).orElseThrow());
  }

  @Test
  void checkDefaultRateAndRetryConfigurations() {
    var config = configFor(new NoDepReconciler());
    final var retry = assertInstanceOf(GenericRetry.class, config.getRetry());
    assertEquals(GradualRetry.DEFAULT_MAX_ATTEMPTS, retry.getMaxAttempts());
    assertEquals(GradualRetry.DEFAULT_MULTIPLIER, retry.getIntervalMultiplier());
    assertEquals(GradualRetry.DEFAULT_INITIAL_INTERVAL, retry.getInitialInterval());
    assertEquals(GradualRetry.DEFAULT_MAX_INTERVAL, retry.getMaxInterval());

    final var limiter = assertInstanceOf(LinearRateLimiter.class, config.getRateLimiter());
    assertFalse(limiter.isActivated());
  }

  @Test
  void configuringRateAndRetryViaAnnotationsShouldWork() {
    var config = configFor(new ConfigurableRateLimitAndRetryReconciler());
    final var retry = config.getRetry();
    final var testRetry = assertInstanceOf(TestRetry.class, retry);
    assertEquals(12, testRetry.getValue());

    final var rateLimiter = assertInstanceOf(LinearRateLimiter.class, config.getRateLimiter());
    assertEquals(7, rateLimiter.getLimitForPeriod());
    assertEquals(Duration.ofSeconds(3), rateLimiter.getRefreshPeriod());
  }

  @Test
  void configuringRateLimitAndGradualRetryViaSuperClassShouldWork() {
    var config = configFor(new GradualRetryAndRateLimitedOnSuperClass());
    final var retry = config.getRetry();
    final var testRetry = assertInstanceOf(GenericRetry.class, retry);
    assertEquals(
        BaseClassWithGradualRetryAndRateLimited.RETRY_MAX_ATTEMPTS, testRetry.getMaxAttempts());

    final var rateLimiter = assertInstanceOf(LinearRateLimiter.class, config.getRateLimiter());
    assertEquals(
        BaseClassWithGradualRetryAndRateLimited.RATE_LIMITED_MAX_RECONCILIATIONS,
        rateLimiter.getLimitForPeriod());
    assertEquals(
        Duration.ofSeconds(BaseClassWithGradualRetryAndRateLimited.RATE_LIMITED_WITHIN_SECONDS),
        rateLimiter.getRefreshPeriod());
  }

  @Test
  void checkingRetryingGraduallyWorks() {
    var config = configFor(new CheckRetryingGraduallyConfiguration());
    final var retry = config.getRetry();
    final var genericRetry = assertInstanceOf(GenericRetry.class, retry);
    assertEquals(
        CheckRetryingGraduallyConfiguration.INITIAL_INTERVAL, genericRetry.getInitialInterval());
    assertEquals(CheckRetryingGraduallyConfiguration.MAX_ATTEMPTS, genericRetry.getMaxAttempts());
    assertEquals(
        CheckRetryingGraduallyConfiguration.INTERVAL_MULTIPLIER,
        genericRetry.getIntervalMultiplier());
    assertEquals(CheckRetryingGraduallyConfiguration.MAX_INTERVAL, genericRetry.getMaxInterval());
  }

  @Test
  void controllerConfigurationOnSuperClassShouldWork() {
    var config = configFor(new ControllerConfigurationOnSuperClass());
    assertNotNull(config.getName());
  }

  @Test
  void configuringFromCustomAnnotationsShouldWork() {
    var config = configFor(new CustomAnnotationReconciler());
    assertEquals(CustomAnnotatedDep.PROVIDED_VALUE, getValue(config, 0));
    assertEquals(CustomConfigConverter.CONVERTER_PROVIDED_DEFAULT, getValue(config, 1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void excludedResourceClassesShouldNotUseSSAByDefault() {
    final var config = configFor(new SelectorReconciler());

    // ReadOnlyDependent targets ConfigMap which is configured to not use SSA by default
    final var kubernetesDependentResourceConfig =
        extractDependentKubernetesResourceConfig(config, 1);
    assertNotNull(kubernetesDependentResourceConfig);
    assertFalse(
        configurationService.shouldUseSSA(
            ReadOnlyDependent.class, ConfigMap.class, kubernetesDependentResourceConfig));
  }

  @Test
  @SuppressWarnings("unchecked")
  void excludedResourceClassesShouldUseSSAIfAnnotatedToDoSo() {
    final var config = configFor(new SelectorReconciler());

    // WithAnnotation dependent also targets ConfigMap but overrides the default with the annotation
    final var kubernetesDependentResourceConfig =
        extractDependentKubernetesResourceConfig(config, 0);
    assertNotNull(kubernetesDependentResourceConfig);
    assertTrue(kubernetesDependentResourceConfig.useSSA());
    assertTrue(
        configurationService.shouldUseSSA(
            SelectorReconciler.WithAnnotation.class,
            ConfigMap.class,
            kubernetesDependentResourceConfig));
  }

  @Test
  @SuppressWarnings("unchecked")
  void dependentsShouldUseSSAByDefaultIfNotExcluded() {
    final var config = configFor(new DefaultSSAForDependentsReconciler());

    var kubernetesDependentResourceConfig = extractDependentKubernetesResourceConfig(config, 0);
    assertNotNull(kubernetesDependentResourceConfig);
    assertTrue(
        configurationService.shouldUseSSA(
            DefaultSSAForDependentsReconciler.DefaultDependent.class,
            ConfigMapReader.class,
            kubernetesDependentResourceConfig));

    kubernetesDependentResourceConfig = extractDependentKubernetesResourceConfig(config, 1);
    assertNotNull(kubernetesDependentResourceConfig);
    assertFalse(kubernetesDependentResourceConfig.useSSA());
    assertFalse(
        configurationService.shouldUseSSA(
            DefaultSSAForDependentsReconciler.NonSSADependent.class,
            Service.class,
            kubernetesDependentResourceConfig));
  }

  @Test
  void shouldUseSSAShouldAlsoWorkWithManualConfiguration() {
    var reconciler = new DependentSSAReconciler(true);
    assertEquals(
        reconciler.isUseSSA(),
        configurationService.shouldUseSSA(reconciler.getSsaConfigMapDependent()));

    reconciler = new DependentSSAReconciler(false);
    assertEquals(
        reconciler.isUseSSA(),
        configurationService.shouldUseSSA(reconciler.getSsaConfigMapDependent()));
  }

  @SuppressWarnings("unchecked")
  private static int getValue(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    final var spec =
        configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().get(index);
    return ((CustomConfig) configuration.getConfigurationFor(spec)).value();
  }

  @ControllerConfiguration(
      maxReconciliationInterval =
          @MaxReconciliationInterval(interval = 50, timeUnit = TimeUnit.SECONDS))
  private static class MaxIntervalReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = @Dependent(type = ReadOnlyDependent.class))
  @ControllerConfiguration(informer = @Informer(namespaces = OneDepReconciler.CONFIGURED_NS))
  private static class OneDepReconciler implements Reconciler<ConfigMapReader> {

    private static final String CONFIGURED_NS = "foo";

    @Override
    public UpdateControl<ConfigMapReader> reconcile(
        ConfigMapReader resource, Context<ConfigMapReader> context) {
      return null;
    }
  }

  @Workflow(dependents = @Dependent(type = ReadOnlyDependent.class, name = NamedDepReconciler.NAME))
  @ControllerConfiguration
  private static class NamedDepReconciler implements Reconciler<ConfigMapReader> {

    private static final String NAME = "foo";

    @Override
    public UpdateControl<ConfigMapReader> reconcile(
        ConfigMapReader resource, Context<ConfigMapReader> context) {
      return null;
    }
  }

  @Workflow(
      dependents = {
        @Dependent(type = ReadOnlyDependent.class),
        @Dependent(type = ReadOnlyDependent.class)
      })
  @ControllerConfiguration
  private static class DuplicatedDepReconciler implements Reconciler<ConfigMapReader> {

    @Override
    public UpdateControl<ConfigMapReader> reconcile(
        ConfigMapReader resource, Context<ConfigMapReader> context) {
      return null;
    }
  }

  @Workflow(
      dependents = {
        @Dependent(type = ReadOnlyDependent.class, name = NamedDuplicatedDepReconciler.NAME),
        @Dependent(type = ReadOnlyDependent.class)
      })
  @ControllerConfiguration
  private static class NamedDuplicatedDepReconciler implements Reconciler<ConfigMapReader> {

    private static final String NAME = "duplicated";

    @Override
    public UpdateControl<ConfigMapReader> reconcile(
        ConfigMapReader resource, Context<ConfigMapReader> context) {
      return null;
    }
  }

  @ControllerConfiguration
  private static class NoDepReconciler implements Reconciler<ConfigMapReader> {

    @Override
    public UpdateControl<ConfigMapReader> reconcile(
        ConfigMapReader resource, Context<ConfigMapReader> context) {
      return null;
    }
  }

  @Workflow(
      dependents = {
        @Dependent(type = SelectorReconciler.WithAnnotation.class),
        @Dependent(type = ReadOnlyDependent.class)
      })
  @ControllerConfiguration
  public static class SelectorReconciler implements Reconciler<ConfigMapReader> {

    @Override
    public UpdateControl<ConfigMapReader> reconcile(
        ConfigMapReader resource, Context<ConfigMapReader> context) {
      return null;
    }

    @KubernetesDependent(useSSA = BooleanWithUndefined.TRUE)
    public static class WithAnnotation
        extends CRUDKubernetesDependentResource<ConfigMap, ConfigMapReader> {}
  }

  public static class MissingAnnotationReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(
      dependents = {
        @Dependent(type = DefaultSSAForDependentsReconciler.DefaultDependent.class),
        @Dependent(type = DefaultSSAForDependentsReconciler.NonSSADependent.class)
      })
  private static class DefaultSSAForDependentsReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }

    private static class DefaultDependent
        extends KubernetesDependentResource<ConfigMapReader, ConfigMap> {}

    @KubernetesDependent(useSSA = BooleanWithUndefined.FALSE)
    private static class NonSSADependent extends KubernetesDependentResource<Service, ConfigMap> {}
  }

  public static class TestRetry implements Retry, AnnotationConfigurable<TestRetryConfiguration> {

    private int value;

    public TestRetry() {}

    @Override
    public RetryExecution initExecution() {
      return null;
    }

    public int getValue() {
      return value;
    }

    @Override
    public void initFrom(TestRetryConfiguration configuration) {
      value = configuration.value();
    }
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  private @interface TestRetryConfiguration {

    int value() default 42;
  }

  @TestRetryConfiguration(12)
  @RateLimited(maxReconciliations = 7, within = 3)
  @ControllerConfiguration(retry = TestRetry.class)
  private static class ConfigurableRateLimitAndRetryReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }
  }

  @GradualRetry(
      maxAttempts = CheckRetryingGraduallyConfiguration.MAX_ATTEMPTS,
      initialInterval = CheckRetryingGraduallyConfiguration.INITIAL_INTERVAL,
      intervalMultiplier = CheckRetryingGraduallyConfiguration.INTERVAL_MULTIPLIER,
      maxInterval = CheckRetryingGraduallyConfiguration.MAX_INTERVAL)
  @ControllerConfiguration
  private static class CheckRetryingGraduallyConfiguration implements Reconciler<ConfigMap> {

    public static final int MAX_ATTEMPTS = 7;
    public static final int INITIAL_INTERVAL = 1000;
    public static final int INTERVAL_MULTIPLIER = 2;
    public static final int MAX_INTERVAL = 60000;

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }
  }

  @ControllerConfiguration
  private static class GradualRetryAndRateLimitedOnSuperClass
      extends BaseClassWithGradualRetryAndRateLimited implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @RateLimited(
      maxReconciliations = BaseClassWithGradualRetryAndRateLimited.RATE_LIMITED_MAX_RECONCILIATIONS,
      within = BaseClassWithGradualRetryAndRateLimited.RATE_LIMITED_WITHIN_SECONDS)
  @GradualRetry(maxAttempts = BaseClassWithGradualRetryAndRateLimited.RETRY_MAX_ATTEMPTS)
  private static class BaseClassWithGradualRetryAndRateLimited {

    public static final int RATE_LIMITED_MAX_RECONCILIATIONS = 7;
    public static final int RATE_LIMITED_WITHIN_SECONDS = 3;
    public static final int RETRY_MAX_ATTEMPTS = 3;
  }

  private static class ControllerConfigurationOnSuperClass extends BaseClass {}

  @ControllerConfiguration
  private static class BaseClass implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(
      dependents = {
        @Dependent(type = CustomAnnotatedDep.class),
        @Dependent(type = ChildCustomAnnotatedDep.class)
      })
  @ControllerConfiguration()
  private static class CustomAnnotationReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @CustomAnnotation(value = CustomAnnotatedDep.PROVIDED_VALUE)
  @Configured(
      by = CustomAnnotation.class,
      with = CustomConfig.class,
      converter = CustomConfigConverter.class)
  private static class CustomAnnotatedDep
      implements DependentResource<ConfigMap, ConfigMap>,
          ConfiguredDependentResource<CustomConfig> {

    public static final int PROVIDED_VALUE = 42;
    private CustomConfig config;

    @Override
    public ReconcileResult<ConfigMap> reconcile(ConfigMap primary, Context<ConfigMap> context) {
      return null;
    }

    @Override
    public Class<ConfigMap> resourceType() {
      return ConfigMap.class;
    }

    @Override
    public void configureWith(CustomConfig config) {
      this.config = config;
    }

    @Override
    public Optional<CustomConfig> configuration() {
      return Optional.ofNullable(config);
    }
  }

  private static class ChildCustomAnnotatedDep extends CustomAnnotatedDep {}

  @Retention(RetentionPolicy.RUNTIME)
  private @interface CustomAnnotation {

    int value();
  }

  private record CustomConfig(int value) {}

  private static class CustomConfigConverter
      implements ConfigurationConverter<CustomAnnotation, CustomConfig> {

    static final int CONVERTER_PROVIDED_DEFAULT = 7;

    @Override
    public CustomConfig configFrom(
        CustomAnnotation configAnnotation,
        DependentResourceSpec<?, ?, CustomConfig> spec,
        io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> parentConfiguration) {
      if (configAnnotation == null) {
        return new CustomConfig(CONVERTER_PROVIDED_DEFAULT);
      } else {
        return new CustomConfig(configAnnotation.value());
      }
    }
  }
}
