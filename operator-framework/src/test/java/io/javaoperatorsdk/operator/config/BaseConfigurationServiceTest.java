package io.javaoperatorsdk.operator.config;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.dependent.ConfigurationConverter;
import io.javaoperatorsdk.operator.api.config.dependent.Configured;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimited;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.javaoperatorsdk.operator.processing.retry.RetryExecution;
import io.javaoperatorsdk.operator.sample.readonly.ReadOnlyDependent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BaseConfigurationServiceTest {

  // subclass to expose configFor method to this test class
  private final static class TestConfigurationService extends BaseConfigurationService {

    @Override
    protected <P extends HasMetadata> io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
        Reconciler<P> reconciler) {
      return super.configFor(reconciler);
    }
  }

  private final TestConfigurationService configurationService = new TestConfigurationService();

  private <P extends HasMetadata> io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> configFor(
      Reconciler<P> reconciler) {
    // ensure that a new configuration is created each time
    return configurationService.configFor(reconciler);
  }

  @Test
  void defaultValuesShouldBeConsistent() {
    final var configuration = configFor(new SelectorReconciler());
    final var annotated = extractDependentKubernetesResourceConfig(configuration, 1);
    final var unannotated = extractDependentKubernetesResourceConfig(configuration, 0);

    assertNull(annotated.labelSelector());
    assertNull(unannotated.labelSelector());
  }

  @SuppressWarnings("rawtypes")
  private KubernetesDependentResourceConfig extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    final var spec =
        configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().get(index);
    return (KubernetesDependentResourceConfig) DependentResourceConfigurationResolver
        .configurationFor(spec, configuration);
  }

  @Test
  @SuppressWarnings("rawtypes")
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
    var maybeConfig =
        DependentResourceConfigurationResolver.configurationFor(dependentSpec, configuration);
    assertNotNull(maybeConfig);
    assertTrue(maybeConfig instanceof KubernetesDependentResourceConfig);
    final var config = (KubernetesDependentResourceConfig) maybeConfig;
    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OneDepReconciler.CONFIGURED_NS), config.namespaces());

    configuration = configFor(new NamedDepReconciler());
    dependents = configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    dependentSpec = findByName(dependents, NamedDepReconciler.NAME);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    maybeConfig = DependentResourceConfigurationResolver.configurationFor(dependentSpec,
        configuration);
    assertNotNull(maybeConfig);
    assertTrue(maybeConfig instanceof KubernetesDependentResourceConfig);
  }

  @Test
  void missingAnnotationThrowsException() {
    final var reconciler = new MissingAnnotationReconciler();
    Assertions.assertThrows(OperatorException.class, () -> configFor(reconciler));
  }

  @SuppressWarnings("rawtypes")
  private DependentResourceSpec findByName(
      List<DependentResourceSpec> dependentResourceSpecList, String name) {
    return dependentResourceSpecList.stream().filter(d -> d.getName().equals(name)).findFirst()
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
    assertTrue(findByNameOptional(dependents, NamedDuplicatedDepReconciler.NAME).isPresent()
        && findByNameOptional(dependents, DependentResource.defaultNameFor(ReadOnlyDependent.class))
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
        BaseClassWithGradualRetryAndRateLimited.RETRY_MAX_ATTEMPTS,
        testRetry.getMaxAttempts());

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
    assertEquals(CheckRetryingGraduallyConfiguration.INITIAL_INTERVAL,
        genericRetry.getInitialInterval());
    assertEquals(CheckRetryingGraduallyConfiguration.MAX_ATTEMPTS, genericRetry.getMaxAttempts());
    assertEquals(CheckRetryingGraduallyConfiguration.INTERVAL_MULTIPLIER,
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

  private static int getValue(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    return ((CustomConfig) DependentResourceConfigurationResolver
        .configurationFor(
            configuration.getWorkflowSpec().orElseThrow().getDependentResourceSpecs().get(index),
            configuration))
        .getValue();
  }

  @ControllerConfiguration(
      maxReconciliationInterval = @MaxReconciliationInterval(interval = 50,
          timeUnit = TimeUnit.SECONDS))
  private static class MaxIntervalReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }
  }

  @Workflow(dependents = @Dependent(type = ReadOnlyDependent.class))
  @ControllerConfiguration(namespaces = OneDepReconciler.CONFIGURED_NS)
  private static class OneDepReconciler implements Reconciler<ConfigMap> {

    private static final String CONFIGURED_NS = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = @Dependent(type = ReadOnlyDependent.class, name = NamedDepReconciler.NAME))
  @ControllerConfiguration
  private static class NamedDepReconciler implements Reconciler<ConfigMap> {

    private static final String NAME = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = {
      @Dependent(type = ReadOnlyDependent.class),
      @Dependent(type = ReadOnlyDependent.class)
  })
  @ControllerConfiguration
  private static class DuplicatedDepReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = {
      @Dependent(type = ReadOnlyDependent.class, name = NamedDuplicatedDepReconciler.NAME),
      @Dependent(type = ReadOnlyDependent.class)
  })
  @ControllerConfiguration
  private static class NamedDuplicatedDepReconciler implements Reconciler<ConfigMap> {

    private static final String NAME = "duplicated";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration
  private static class NoDepReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = {
      @Dependent(type = SelectorReconciler.WithAnnotation.class),
      @Dependent(type = ReadOnlyDependent.class)
  })
  @ControllerConfiguration
  private static class SelectorReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }

    @KubernetesDependent
    private static class WithAnnotation extends KubernetesDependentResource<ConfigMap, ConfigMap> {

      public WithAnnotation() {
        super(ConfigMap.class);
      }
    }
  }

  private static class MissingAnnotationReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
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
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
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
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return UpdateControl.noUpdate();
    }
  }

  @ControllerConfiguration
  private static class GradualRetryAndRateLimitedOnSuperClass
      extends BaseClassWithGradualRetryAndRateLimited
      implements Reconciler<ConfigMap> {

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

  private static class ControllerConfigurationOnSuperClass extends BaseClass {

  }

  @ControllerConfiguration
  private static class BaseClass implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @Workflow(dependents = {
      @Dependent(type = CustomAnnotatedDep.class),
      @Dependent(type = ChildCustomAnnotatedDep.class)
  })
  @ControllerConfiguration()
  private static class CustomAnnotationReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }
  }

  @CustomAnnotation(value = CustomAnnotatedDep.PROVIDED_VALUE)
  @Configured(by = CustomAnnotation.class, with = CustomConfig.class,
      converter = CustomConfigConverter.class)
  private static class CustomAnnotatedDep implements DependentResource<ConfigMap, ConfigMap>,
      DependentResourceConfigurator<CustomConfig> {

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

  private static class ChildCustomAnnotatedDep extends CustomAnnotatedDep {

  }

  @Retention(RetentionPolicy.RUNTIME)
  private @interface CustomAnnotation {

    int value();
  }

  private static class CustomConfig {

    private final int value;

    private CustomConfig(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  private static class CustomConfigConverter
      implements ConfigurationConverter<CustomAnnotation, CustomConfig, CustomAnnotatedDep> {

    static final int CONVERTER_PROVIDED_DEFAULT = 7;

    @Override
    public CustomConfig configFrom(CustomAnnotation configAnnotation,
        io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> parentConfiguration,
        Class<CustomAnnotatedDep> originatingClass) {
      if (configAnnotation == null) {
        return new CustomConfig(CONVERTER_PROVIDED_DEFAULT);
      } else {
        return new CustomConfig(configAnnotation.value());
      }
    }
  }
}
