package io.javaoperatorsdk.quarkus.extension.deployment;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.quarkus.extension.ConfigurationServiceRecorder;
import io.javaoperatorsdk.quarkus.extension.ExternalConfiguration;
import io.javaoperatorsdk.quarkus.extension.ExternalControllerConfiguration;
import io.javaoperatorsdk.quarkus.extension.OperatorProducer;
import io.javaoperatorsdk.quarkus.extension.QuarkusConfigurationService;
import io.javaoperatorsdk.quarkus.extension.QuarkusControllerConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Singleton;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

class QuarkusExtensionProcessor {

  private static final Logger log = Logger.getLogger(QuarkusExtensionProcessor.class.getName());

  private static final String FEATURE = "operator-sdk";
  private static final DotName RESOURCE_CONTROLLER =
      DotName.createSimple(ResourceController.class.getName());
  private static final DotName CONTROLLER = DotName.createSimple(Controller.class.getName());
  private static final DotName APPLICATION_SCOPED =
      DotName.createSimple(ApplicationScoped.class.getName());
  private static final Supplier<String> EXCEPTION_SUPPLIER =
      () -> {
        throw new IllegalArgumentException();
      };

  private ExternalConfiguration externalConfiguration;

  @BuildStep
  void indexSDKDependencies(
      BuildProducer<IndexDependencyBuildItem> indexDependency,
      BuildProducer<FeatureBuildItem> features) {
    features.produce(new FeatureBuildItem(FEATURE));
    indexDependency.produce(
        new IndexDependencyBuildItem("io.javaoperatorsdk", "operator-framework-core"));
  }

  @BuildStep
  @Record(ExecutionTime.STATIC_INIT)
  void createConfigurationServiceAndOperator(
      CombinedIndexBuildItem combinedIndexBuildItem,
      BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
      BuildProducer<AdditionalBeanBuildItem> additionalBeans,
      BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
      ConfigurationServiceRecorder recorder) {
    final var index = combinedIndexBuildItem.getIndex();
    final var resourceControllers = index.getAllKnownImplementors(RESOURCE_CONTROLLER);

    final List<ControllerConfiguration> controllerConfigs =
        resourceControllers.stream()
            .map(ci -> createControllerConfiguration(ci, additionalBeans, reflectionClasses))
            .collect(Collectors.toList());

    final var supplier = recorder.configurationServiceSupplier(controllerConfigs);
    syntheticBeanBuildItemBuildProducer.produce(
        SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
            .scope(Singleton.class)
            .addType(ConfigurationService.class)
            .setRuntimeInit()
            .supplier(supplier)
            .done());

    additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorProducer.class));
  }

  private ControllerConfiguration createControllerConfiguration(
      ClassInfo info,
      BuildProducer<AdditionalBeanBuildItem> additionalBeans,
      BuildProducer<ReflectiveClassBuildItem> reflectionClasses) {
    // first retrieve the custom resource class
    final var rcInterface =
        info.interfaceTypes().stream()
            .filter(t -> t.name().equals(RESOURCE_CONTROLLER))
            .findFirst()
            .map(Type::asParameterizedType)
            // shouldn't happen since we're only dealing with ResourceController implementors
            // already
            .orElseThrow();
    final var crType = rcInterface.arguments().get(0).name().toString();

    // create ResourceController bean
    final var resourceControllerClassName = info.name().toString();
    additionalBeans.produce(
        AdditionalBeanBuildItem.builder()
            .addBeanClass(resourceControllerClassName)
            .setUnremovable()
            .setDefaultScope(APPLICATION_SCOPED)
            .build());

    // load CR class
    final Class<CustomResource> crClass = (Class<CustomResource>) loadClass(crType);

    // Instantiate CR to check that it's properly annotated
    final CustomResource cr;
    try {
      cr = crClass.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException(e.getCause());
    }

    // retrieve CRD name from CR type
    final var crdName = CustomResource.getCRDName(crClass);

    // register CR class for introspection
    reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, crClass));

    // retrieve the Controller annotation if it exists
    final var controllerAnnotation = info.classAnnotation(CONTROLLER);

    // retrieve the controller's name
    final var defaultControllerName =
        ControllerUtils.getDefaultResourceControllerName(resourceControllerClassName);
    final var name =
        annotationValueOrDefault(
            controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);

    // check if we have externalized configuration to provide values
    final var extContConfig = externalConfiguration.controllers.get(name);

    final var extractor = new ValueExtractor(controllerAnnotation, extContConfig);

    // create the configuration
    final var configuration =
        new QuarkusControllerConfiguration(
            resourceControllerClassName,
            name,
            crdName,
            extractor.extract(
                c -> c.finalizer,
                "finalizerName",
                AnnotationValue::asString,
                () -> ControllerUtils.getDefaultFinalizerName(crdName)),
            extractor.extract(
                c -> c.generationAware,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> true),
            QuarkusControllerConfiguration.asSet(
                extractor.extract(
                    c -> c.namespaces.map(l -> l.toArray(new String[0])),
                    "namespaces",
                    AnnotationValue::asStringArray,
                    () -> new String[] {})),
            crType,
            retryConfiguration(extContConfig));

    log.infov(
        "Processed ''{0}'' controller named ''{1}'' for ''{2}'' CR (version ''{3}'')",
        info.name().toString(), name, cr.getCRDName(), cr.getApiVersion());

    return configuration;
  }

  private RetryConfiguration retryConfiguration(ExternalControllerConfiguration extConfig) {
    return extConfig == null ? null : new DelegatingRetryConfiguration(extConfig.retry).resolve();
  }

  private static class ValueExtractor {

    private final AnnotationInstance controllerAnnotation;
    private final ExternalControllerConfiguration extContConfig;

    ValueExtractor(
        AnnotationInstance controllerAnnotation, ExternalControllerConfiguration extContConfig) {
      this.controllerAnnotation = controllerAnnotation;
      this.extContConfig = extContConfig;
    }

    <T> T extract(
        Function<ExternalControllerConfiguration, Optional<T>> extractor,
        String annotationField,
        Function<AnnotationValue, T> converter,
        Supplier<T> defaultValue) {
      // first check if we have an external configuration
      if (extContConfig != null) {
        // extract value from config if present
        return extractor
            .apply(extContConfig)
            // or get from the annotation or default
            .orElse(annotationValueOrDefault(annotationField, converter, defaultValue));
      } else {
        // get from annotation or default
        return annotationValueOrDefault(annotationField, converter, defaultValue);
      }
    }

    private <T> T annotationValueOrDefault(
        String name, Function<AnnotationValue, T> converter, Supplier<T> defaultValue) {
      return QuarkusExtensionProcessor.annotationValueOrDefault(
          controllerAnnotation, name, converter, defaultValue);
    }
  }

  private static <T> T annotationValueOrDefault(
      AnnotationInstance annotation,
      String name,
      Function<AnnotationValue, T> converter,
      Supplier<T> defaultValue) {
    return annotation != null
        ?
        // get converted annotation value of get default
        Optional.ofNullable(annotation.value(name)).map(converter).orElseGet(defaultValue)
        :
        // get default
        defaultValue.get();
  }

  private Class<?> loadClass(String className) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Couldn't find class " + className);
    }
  }
}
