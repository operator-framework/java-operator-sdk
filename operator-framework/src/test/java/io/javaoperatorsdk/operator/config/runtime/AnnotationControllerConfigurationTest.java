package io.javaoperatorsdk.operator.config.runtime;

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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationControllerConfigurationTest {

  @Test
  void defaultValuesShouldBeConsistent() {
    final var configuration = new AnnotationControllerConfiguration<>(new SelectorReconciler());
    final var annotated = extractDependentKubernetesResourceConfig(configuration, 1);
    final var unannotated = extractDependentKubernetesResourceConfig(configuration, 0);

    assertNull(annotated.labelSelector());
    assertNull(unannotated.labelSelector());
  }

  @SuppressWarnings("rawtypes")
  private KubernetesDependentResourceConfig extractDependentKubernetesResourceConfig(
      io.javaoperatorsdk.operator.api.config.ControllerConfiguration<?> configuration, int index) {
    return (KubernetesDependentResourceConfig) configuration.getDependentResources().get(index)
        .getDependentResourceConfiguration()
        .orElseThrow();
  }

  @Test
  @SuppressWarnings("rawtypes")
  void getDependentResources() {
    var configuration = new AnnotationControllerConfiguration<>(new NoDepReconciler());
    var dependents = configuration.getDependentResources();
    assertTrue(dependents.isEmpty());

    configuration = new AnnotationControllerConfiguration<>(new OneDepReconciler());
    dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    final var dependentResourceName = DependentResource.defaultNameFor(ReadOnlyDependent.class);
    assertTrue(dependents.stream().anyMatch(d -> d.getName().equals(dependentResourceName)));
    var dependentSpec = findByName(dependents, dependentResourceName);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    var maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
    final var config = (KubernetesDependentResourceConfig) maybeConfig.orElseThrow();
    // check that the DependentResource inherits the controller's configuration if applicable
    assertEquals(1, config.namespaces().size());
    assertEquals(Set.of(OneDepReconciler.CONFIGURED_NS), config.namespaces());

    configuration = new AnnotationControllerConfiguration<>(new NamedDepReconciler());
    dependents = configuration.getDependentResources();
    assertFalse(dependents.isEmpty());
    assertEquals(1, dependents.size());
    dependentSpec = findByName(dependents, NamedDepReconciler.NAME);
    assertEquals(ReadOnlyDependent.class, dependentSpec.getDependentResourceClass());
    maybeConfig = dependentSpec.getDependentResourceConfiguration();
    assertTrue(maybeConfig.isPresent());
    assertTrue(maybeConfig.get() instanceof KubernetesDependentResourceConfig);
  }

  @Test
  void missingAnnotationThrowsException() {
    Assertions.assertThrows(OperatorException.class,
        () -> new AnnotationControllerConfiguration<>(new MissingAnnotationReconciler()));
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
    var configuration = new AnnotationControllerConfiguration<>(new DuplicatedDepReconciler());
    assertThrows(IllegalArgumentException.class, configuration::getDependentResources);
  }

  @Test
  void addingDuplicatedDependentsWithNameShouldWork() {
    var config = new AnnotationControllerConfiguration<>(new NamedDuplicatedDepReconciler());
    var dependents = config.getDependentResources();
    assertEquals(2, dependents.size());
    assertTrue(findByNameOptional(dependents, NamedDuplicatedDepReconciler.NAME).isPresent()
        && findByNameOptional(dependents, DependentResource.defaultNameFor(ReadOnlyDependent.class))
            .isPresent());
  }

  @Test
  void maxIntervalCanBeConfigured() {
    var config = new AnnotationControllerConfiguration<>(new MaxIntervalReconciler());
    assertEquals(50, config.maxReconciliationInterval().map(Duration::getSeconds).orElseThrow());
  }

  @Test
  void checkDefaultRateAndRetryConfigurations() {
    var config = new AnnotationControllerConfiguration<>(new NoDepReconciler());
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
    var config =
        new AnnotationControllerConfiguration<>(new ConfigurableRateLimitAndRetryReconciler());
    final var retry = config.getRetry();
    final var testRetry = assertInstanceOf(TestRetry.class, retry);
    assertEquals(12, testRetry.getValue());

    final var rateLimiter = assertInstanceOf(LinearRateLimiter.class, config.getRateLimiter());
    assertEquals(7, rateLimiter.getLimitForPeriod());
    assertEquals(Duration.ofSeconds(3), rateLimiter.getRefreshPeriod());
  }

  @Test
  void checkingRetryingGraduallyWorks() {
    var config = new AnnotationControllerConfiguration<>(new CheckRetryingGraduallyConfiguration());
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
    var config = new AnnotationControllerConfiguration<>(new ControllerConfigurationOnSuperClass());
    assertNotNull(config.getName());
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

  @ControllerConfiguration(namespaces = OneDepReconciler.CONFIGURED_NS,
      dependents = @Dependent(type = ReadOnlyDependent.class))
  private static class OneDepReconciler implements Reconciler<ConfigMap> {

    private static final String CONFIGURED_NS = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(
      dependents = @Dependent(type = ReadOnlyDependent.class, name = NamedDepReconciler.NAME))
  private static class NamedDepReconciler implements Reconciler<ConfigMap> {
    private static final String NAME = "foo";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(
      dependents = {
          @Dependent(type = ReadOnlyDependent.class),
          @Dependent(type = ReadOnlyDependent.class)
      })
  private static class DuplicatedDepReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }

  @ControllerConfiguration(
      dependents = {
          @Dependent(type = ReadOnlyDependent.class, name = NamedDuplicatedDepReconciler.NAME),
          @Dependent(type = ReadOnlyDependent.class)
      })
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

  @ControllerConfiguration(dependents = {
      @Dependent(type = SelectorReconciler.WithAnnotation.class),
      @Dependent(type = ReadOnlyDependent.class)
  })
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

  private static class ControllerConfigurationOnSuperClass extends BaseClass {
  }

  @ControllerConfiguration
  private static class BaseClass implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return null;
    }
  }
}
