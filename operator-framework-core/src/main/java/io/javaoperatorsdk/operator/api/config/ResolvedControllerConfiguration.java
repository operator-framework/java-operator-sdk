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

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

@SuppressWarnings("rawtypes")
public class ResolvedControllerConfiguration<P extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> {

  private final InformerConfiguration<P> informerConfig;
  private final String name;
  private final boolean generationAware;
  private final String associatedReconcilerClassName;
  private final Retry retry;
  private final RateLimiter rateLimiter;
  private final Duration maxReconciliationInterval;
  private final String finalizer;
  private final Map<DependentResourceSpec, Object> configurations;
  private final ConfigurationService configurationService;
  private final String fieldManager;
  private final boolean triggerReconcilerOnAllEvents;
  private WorkflowSpec workflowSpec;

  public ResolvedControllerConfiguration(ControllerConfiguration<P> other) {
    this(
        other.getName(),
        other.isGenerationAware(),
        other.getAssociatedReconcilerClassName(),
        other.getRetry(),
        other.getRateLimiter(),
        other.maxReconciliationInterval().orElse(null),
        other.getFinalizerName(),
        Collections.emptyMap(),
        other.fieldManager(),
        other.getConfigurationService(),
        other.getInformerConfig(),
        other.triggerReconcilerOnAllEvents(),
        other.getWorkflowSpec().orElse(null));
  }

  public ResolvedControllerConfiguration(
      String name,
      boolean generationAware,
      String associatedReconcilerClassName,
      Retry retry,
      RateLimiter rateLimiter,
      Duration maxReconciliationInterval,
      String finalizer,
      Map<DependentResourceSpec, Object> configurations,
      String fieldManager,
      ConfigurationService configurationService,
      InformerConfiguration<P> informerConfig,
      boolean triggerReconcilerOnAllEvents,
      WorkflowSpec workflowSpec) {
    this(
        name,
        generationAware,
        associatedReconcilerClassName,
        retry,
        rateLimiter,
        maxReconciliationInterval,
        finalizer,
        configurations,
        fieldManager,
        configurationService,
        informerConfig,
        triggerReconcilerOnAllEvents);
    setWorkflowSpec(workflowSpec);
  }

  protected ResolvedControllerConfiguration(
      String name,
      boolean generationAware,
      String associatedReconcilerClassName,
      Retry retry,
      RateLimiter rateLimiter,
      Duration maxReconciliationInterval,
      String finalizer,
      Map<DependentResourceSpec, Object> configurations,
      String fieldManager,
      ConfigurationService configurationService,
      InformerConfiguration<P> informerConfig,
      boolean triggerReconcilerOnAllEvents) {
    this.informerConfig = informerConfig;
    this.configurationService = configurationService;
    this.name = ControllerConfiguration.ensureValidName(name, associatedReconcilerClassName);
    this.generationAware = generationAware;
    this.associatedReconcilerClassName = associatedReconcilerClassName;
    this.retry = ensureRetry(retry);
    this.rateLimiter = ensureRateLimiter(rateLimiter);
    this.maxReconciliationInterval = maxReconciliationInterval;
    this.configurations = configurations != null ? configurations : Collections.emptyMap();
    this.finalizer =
        ControllerConfiguration.ensureValidFinalizerName(finalizer, getResourceTypeName());
    this.fieldManager = fieldManager;
    this.triggerReconcilerOnAllEvents = triggerReconcilerOnAllEvents;
  }

  protected ResolvedControllerConfiguration(
      Class<P> resourceClass,
      String name,
      Class<? extends Reconciler> reconcilerClas,
      ConfigurationService configurationService) {
    this(
        name,
        false,
        getAssociatedReconcilerClassName(reconcilerClas),
        null,
        null,
        null,
        null,
        null,
        null,
        configurationService,
        InformerConfiguration.builder(resourceClass).buildForController(),
        false);
  }

  @Override
  public InformerConfiguration<P> getInformerConfig() {
    return informerConfig;
  }

  public static Duration getMaxReconciliationInterval(long interval, TimeUnit timeUnit) {
    return interval > 0 ? Duration.of(interval, timeUnit.toChronoUnit()) : null;
  }

  public static String getAssociatedReconcilerClassName(
      Class<? extends Reconciler> reconcilerClass) {
    return reconcilerClass.getCanonicalName();
  }

  protected Retry ensureRetry(Retry given) {
    return given == null ? ControllerConfiguration.super.getRetry() : given;
  }

  protected RateLimiter ensureRateLimiter(RateLimiter given) {
    return given == null ? ControllerConfiguration.super.getRateLimiter() : given;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getFinalizerName() {
    return finalizer;
  }

  @Override
  public boolean isGenerationAware() {
    return generationAware;
  }

  @Override
  public String getAssociatedReconcilerClassName() {
    return associatedReconcilerClassName;
  }

  @Override
  public Retry getRetry() {
    return retry;
  }

  @Override
  public RateLimiter getRateLimiter() {
    return rateLimiter;
  }

  @Override
  public Optional<WorkflowSpec> getWorkflowSpec() {
    return Optional.ofNullable(workflowSpec);
  }

  public void setWorkflowSpec(WorkflowSpec workflowSpec) {
    this.workflowSpec = workflowSpec;
  }

  @Override
  public Optional<Duration> maxReconciliationInterval() {
    return Optional.ofNullable(maxReconciliationInterval);
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C> C getConfigurationFor(DependentResourceSpec<?, P, C> spec) {
    // first check if there's an overridden configuration at the controller level
    var config = configurations.get(spec);
    if (config == null) {
      // if not, check the spec for configuration
      config = spec.getConfiguration().orElse(null);
    }
    return (C) config;
  }

  @Override
  public String fieldManager() {
    return fieldManager;
  }

  @Override
  public boolean triggerReconcilerOnAllEvents() {
    return triggerReconcilerOnAllEvents;
  }
}
