package io.javaoperatorsdk.quarkus.extension.deployment;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.quarkus.extension.ConfigurationServiceRecorder;
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

class QuarkusExtensionProcessor {

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

    // generate configuration
    final var controllerAnnotation = info.classAnnotation(CONTROLLER);
    if (controllerAnnotation == null) {
      throw new IllegalArgumentException(
          resourceControllerClassName
              + " is missing the "
              + Controller.class.getCanonicalName()
              + " annotation");
    }

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

    // create the configuration
    final var configuration =
        new QuarkusControllerConfiguration(
            resourceControllerClassName,
            valueOrDefault(
                controllerAnnotation,
                "name",
                AnnotationValue::asString,
                () ->
                    ControllerUtils.getDefaultResourceControllerName(resourceControllerClassName)),
            crdName,
            valueOrDefault(
                controllerAnnotation,
                "finalizerName",
                AnnotationValue::asString,
                () -> ControllerUtils.getDefaultFinalizerName(crdName)),
            valueOrDefault(
                controllerAnnotation,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> true),
            valueOrDefault(
                controllerAnnotation, "isClusterScoped", AnnotationValue::asBoolean, () -> false),
            QuarkusControllerConfiguration.asSet(
                valueOrDefault(
                    controllerAnnotation,
                    "namespaces",
                    AnnotationValue::asStringArray,
                    () -> new String[] {})),
            crType,
            null // todo: fix-me
            );

    return configuration;
  }

  private <T> T valueOrDefault(
      AnnotationInstance annotation,
      String name,
      Function<AnnotationValue, T> converter,
      Supplier<T> defaultValue) {
    return Optional.ofNullable(annotation.value(name)).map(converter).orElseGet(defaultValue);
  }

  private Class<?> loadClass(String className) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Couldn't find class " + className);
    }
  }
}
