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
package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.Utils.Configurator;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfiguration.CONTROLLER_NAME_AS_FIELD_MANAGER;

/**
 * A default {@link ConfigurationService} implementation, resolving {@link Reconciler}s
 * configuration when it has already been resolved before. If this behavior is not adequate, please
 * use {@link AbstractConfigurationService} instead as a base for your {@code ConfigurationService}
 * implementation.
 */
public class BaseConfigurationService extends AbstractConfigurationService {

  private static final String LOGGER_NAME = "Default ConfigurationService implementation";
  private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);
  private static final ResourceClassResolver DEFAULT_RESOLVER = new DefaultResourceClassResolver();

  public BaseConfigurationService(Version version) {
    this(version, null);
  }

  public BaseConfigurationService(Version version, Cloner cloner) {
    this(version, cloner, null);
  }

  public BaseConfigurationService(Version version, Cloner cloner, KubernetesClient client) {
    super(version, cloner, null, client);
  }

  public BaseConfigurationService() {
    this(Utils.VERSION);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<DependentResourceSpec> dependentResources(
      Workflow annotation, ControllerConfiguration<?> controllerConfiguration) {
    final var dependents = annotation.dependents();

    if (dependents == null || dependents.length == 0) {
      return Collections.emptyList();
    }

    final var specsMap = new LinkedHashMap<String, DependentResourceSpec>(dependents.length);
    for (Dependent dependent : dependents) {
      final Class<? extends DependentResource> dependentType = dependent.type();

      final var dependentName = getName(dependent.name(), dependentType);
      var spec = specsMap.get(dependentName);
      if (spec != null) {
        throw new IllegalArgumentException(
            "A DependentResource named '" + dependentName + "' already exists: " + spec);
      }

      final var name = controllerConfiguration.getName();

      var eventSourceName = dependent.useEventSourceWithName();
      eventSourceName = Constants.NO_VALUE_SET.equals(eventSourceName) ? null : eventSourceName;
      final var context = Utils.contextFor(name, dependentType, null);
      spec =
          new DependentResourceSpec(
              dependentType,
              dependentName,
              Set.of(dependent.dependsOn()),
              Utils.instantiate(dependent.readyPostcondition(), Condition.class, context),
              Utils.instantiate(dependent.reconcilePrecondition(), Condition.class, context),
              Utils.instantiate(dependent.deletePostcondition(), Condition.class, context),
              Utils.instantiate(dependent.activationCondition(), Condition.class, context),
              eventSourceName);
      specsMap.put(dependentName, spec);

      // extract potential configuration
      DependentResourceConfigurationResolver.configureSpecFromConfigured(
          spec, controllerConfiguration, dependentType);

      specsMap.put(dependentName, spec);
    }

    return specsMap.values().stream().toList();
  }

  @SuppressWarnings("unchecked")
  private static <T> T valueOrDefaultFromAnnotation(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration controllerConfiguration,
      Function<io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration, T> mapper,
      String defaultMethodName) {
    try {
      if (controllerConfiguration == null) {
        return (T)
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class
                .getDeclaredMethod(defaultMethodName)
                .getDefaultValue();
      } else {
        return mapper.apply(controllerConfiguration);
      }
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("rawtypes")
  private static String getName(String name, Class<? extends DependentResource> dependentType) {
    if (name.isBlank()) {
      name = DependentResource.defaultNameFor(dependentType);
    }
    return name;
  }

  @SuppressWarnings("unused")
  private static <T> Configurator<T> configuratorFor(
      Class<T> instanceType, Class<? extends Reconciler<?>> reconcilerClass) {
    return instance -> configureFromAnnotatedReconciler(instance, reconcilerClass);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void configureFromAnnotatedReconciler(
      Object instance, Class<? extends Reconciler<?>> reconcilerClass) {
    if (instance instanceof AnnotationConfigurable configurable) {
      final Class<? extends Annotation> configurationClass =
          (Class<? extends Annotation>)
              Utils.getFirstTypeArgumentFromSuperClassOrInterface(
                  instance.getClass(), AnnotationConfigurable.class);
      final var configAnnotation = reconcilerClass.getAnnotation(configurationClass);
      if (configAnnotation != null) {
        configurable.initFrom(configAnnotation);
      }
    }
  }

  @Override
  protected void logMissingReconcilerWarning(String reconcilerKey, String reconcilersNameMessage) {
    if (!createIfNeeded()) {
      logger.warn(
          "Configuration for reconciler '{}' was not found. {}",
          reconcilerKey,
          reconcilersNameMessage);
    }
  }

  @SuppressWarnings("unused")
  public String getLoggerName() {
    return LOGGER_NAME;
  }

  protected Logger getLogger() {
    return logger;
  }

  @Override
  public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> reconciler) {
    var config = super.getConfigurationFor(reconciler);
    if (config == null) {
      if (createIfNeeded()) {
        // create the configuration on demand and register it
        config = configFor(reconciler);
        register(config);
        getLogger()
            .info(
                "Created configuration for reconciler {} with name {}",
                reconciler.getClass().getName(),
                config.getName());
      }
    } else {
      // check that we don't have a reconciler name collision
      final var newControllerClassName = reconciler.getClass().getCanonicalName();
      if (!config.getAssociatedReconcilerClassName().equals(newControllerClassName)) {
        throwExceptionOnNameCollision(newControllerClassName, config);
      }
    }
    return config;
  }

  /**
   * Override if a different class resolution is needed
   *
   * @return the custom {@link ResourceClassResolver} implementation to use
   */
  protected ResourceClassResolver getResourceClassResolver() {
    return DEFAULT_RESOLVER;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected <P extends HasMetadata> ControllerConfiguration<P> configFor(Reconciler<P> reconciler) {
    final Class<? extends Reconciler<P>> reconcilerClass =
        (Class<? extends Reconciler<P>>) reconciler.getClass();
    final var controllerAnnotation =
        reconcilerClass.getAnnotation(
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class);

    ResolvedControllerConfiguration<P> config =
        controllerConfiguration(reconcilerClass, controllerAnnotation);

    final var workflowAnnotation =
        reconcilerClass.getAnnotation(io.javaoperatorsdk.operator.api.reconciler.Workflow.class);
    if (workflowAnnotation != null) {
      final var specs = dependentResources(workflowAnnotation, config);
      WorkflowSpec workflowSpec =
          new WorkflowSpec() {
            @Override
            public List<DependentResourceSpec> getDependentResourceSpecs() {
              return specs;
            }

            @Override
            public boolean isExplicitInvocation() {
              return workflowAnnotation.explicitInvocation();
            }

            @Override
            public boolean handleExceptionsInReconciler() {
              return workflowAnnotation.handleExceptionsInReconciler();
            }
          };
      config.setWorkflowSpec(workflowSpec);
    }

    return config;
  }

  @SuppressWarnings({"unchecked"})
  private <P extends HasMetadata> ResolvedControllerConfiguration<P> controllerConfiguration(
      Class<? extends Reconciler<P>> reconcilerClass,
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration annotation) {
    final var resourceClass = getResourceClassResolver().getPrimaryResourceClass(reconcilerClass);

    final var name = ReconcilerUtilsInternal.getNameFor(reconcilerClass);
    final var generationAware =
        valueOrDefaultFromAnnotation(
            annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
                ::generationAwareEventProcessing,
            "generationAwareEventProcessing");
    final var associatedReconcilerClass =
        ResolvedControllerConfiguration.getAssociatedReconcilerClassName(reconcilerClass);

    final var context = Utils.contextFor(name);
    final Class<? extends Retry> retryClass =
        valueOrDefaultFromAnnotation(
            annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::retry,
            "retry");
    final var retry =
        Utils.instantiateAndConfigureIfNeeded(
            retryClass, Retry.class, context, configuratorFor(Retry.class, reconcilerClass));

    @SuppressWarnings("rawtypes")
    final Class<? extends RateLimiter> rateLimiterClass =
        valueOrDefaultFromAnnotation(
            annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::rateLimiter,
            "rateLimiter");
    final var rateLimiter =
        Utils.instantiateAndConfigureIfNeeded(
            rateLimiterClass,
            RateLimiter.class,
            context,
            configuratorFor(RateLimiter.class, reconcilerClass));

    final var reconciliationInterval =
        valueOrDefaultFromAnnotation(
            annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
                ::maxReconciliationInterval,
            "maxReconciliationInterval");
    long interval = -1;
    TimeUnit timeUnit = null;
    if (reconciliationInterval != null && reconciliationInterval.interval() > 0) {
      interval = reconciliationInterval.interval();
      timeUnit = reconciliationInterval.timeUnit();
    }

    var fieldManager =
        valueOrDefaultFromAnnotation(
            annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::fieldManager,
            "fieldManager");
    final var dependentFieldManager =
        fieldManager.equals(CONTROLLER_NAME_AS_FIELD_MANAGER) ? name : fieldManager;

    var triggerReconcilerOnAllEvent =
        annotation != null && annotation.triggerReconcilerOnAllEvent();

    InformerConfiguration<P> informerConfig =
        InformerConfiguration.builder(resourceClass)
            .initFromAnnotation(annotation != null ? annotation.informer() : null, context)
            .buildForController();

    return new ResolvedControllerConfiguration<>(
        name,
        generationAware,
        associatedReconcilerClass,
        retry,
        rateLimiter,
        ResolvedControllerConfiguration.getMaxReconciliationInterval(interval, timeUnit),
        valueOrDefaultFromAnnotation(
            annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::finalizerName,
            "finalizerName"),
        null,
        dependentFieldManager,
        this,
        informerConfig,
        triggerReconcilerOnAllEvent);
  }

  /**
   * @deprecated This method was meant to allow subclasses to prevent automatic creation of the
   *     configuration when not found. This functionality is now removed, if you want to be able to
   *     prevent automated, on-demand creation of a reconciler's configuration, please use the
   *     {@link AbstractConfigurationService} implementation instead as base for your extension.
   */
  @Deprecated(forRemoval = true)
  protected boolean createIfNeeded() {
    return true;
  }

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return Utils.shouldCheckCRDAndValidateLocalModel();
  }
}
