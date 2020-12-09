package io.javaoperatorsdk.quarkus.extension.deployment;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ControllerUtils;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.quarkus.extension.ConfigurationServiceRecorder;
import io.javaoperatorsdk.quarkus.extension.QuarkusConfigurationService;
import io.javaoperatorsdk.quarkus.extension.QuarkusControllerConfiguration;
import io.javaoperatorsdk.quarkus.extension.QuarkusOperator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.kubernetes.client.spi.KubernetesClientBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

class QuarkusExtensionProcessor {
    
    private static final String FEATURE = "operator-sdk";
    private static final DotName RESOURCE_CONTROLLER = DotName.createSimple(ResourceController.class.getName());
    private static final DotName CONTROLLER = DotName.createSimple(Controller.class.getName());
    private static final Supplier<String> EXCEPTION_SUPPLIER = () -> {
        throw new IllegalArgumentException();
    };
    
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
    
    
    @BuildStep
    List<ControllerConfigurationBuildItem> createControllerBeans(CombinedIndexBuildItem combinedIndexBuildItem,
                                                                 BuildProducer<GeneratedClassBuildItem> generatedClass,
                                                                 BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        final var index = combinedIndexBuildItem.getIndex();
        final var resourceControllers = index.getAllKnownImplementors(RESOURCE_CONTROLLER);
        
        final var classOutput = new GeneratedClassGizmoAdaptor(generatedClass, true);
        return resourceControllers.stream()
            .map(ci -> createControllerConfiguration(ci, classOutput, additionalBeans))
            .collect(Collectors.toList());
    }
    
    
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Produce(ConfigurationServiceDoneBuildItem.class)
    void createConfigurationService(BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
                                    List<ControllerConfigurationBuildItem> configurations,
                                    KubernetesClientBuildItem clientBuildItem,
                                    ConfigurationServiceRecorder recorder) {
        final List<ControllerConfiguration> controllerConfigs = configurations.stream()
            .map(ControllerConfigurationBuildItem::getConfiguration)
            .collect(Collectors.toList());
        final var supplier = recorder.configurationServiceSupplier(controllerConfigs, clientBuildItem.getClient());
        syntheticBeanBuildItemBuildProducer.produce(SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
            .scope(Singleton.class)
            .setRuntimeInit()
            .supplier(supplier)
            .done());
    }
    
    @BuildStep
    @Consume(ConfigurationServiceDoneBuildItem.class)
    void createOperator(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(QuarkusOperator.class));
    }
    
    private ControllerConfigurationBuildItem createControllerConfiguration(ClassInfo info, ClassOutput classOutput, BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        // first retrieve the custom resource class
        final var rcInterface = info.interfaceTypes().stream()
            .filter(t -> t.name().equals(RESOURCE_CONTROLLER))
            .findFirst()
            .map(Type::asParameterizedType)
            .orElseThrow(); // shouldn't happen since we're only dealing with ResourceController implementors already
        final var crType = rcInterface.arguments().get(0).name().toString();
        
        // create ResourceController bean
        final var resourceControllerClassName = info.name().toString();
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(resourceControllerClassName));
        
        // generate associated Doneable class
        final var doneableClassName = crType + "Doneable";
        final var crDoneableClassName = CustomResourceDoneable.class.getName();
        try (ClassCreator cc = ClassCreator.builder()
            .classOutput(classOutput)
            .className(doneableClassName)
            .superClass(crDoneableClassName)
            .build()) {
            
            MethodCreator ctor = cc.getMethodCreator("<init>", void.class.getName(), crType);
            ctor.setModifiers(Modifier.PUBLIC);
            final var functionName = Function.class.getName();
            ctor.invokeSpecialMethod(
                MethodDescriptor.ofConstructor(crDoneableClassName, crType, functionName),
                ctor.getThis(),
                ctor.getMethodParam(0),
                ctor.invokeStaticMethod(MethodDescriptor.ofMethod(functionName, "identity", functionName)));
        }
        
        // generate configuration
        final var controllerAnnotation = info.classAnnotation(CONTROLLER);
        final var crdName = valueOrDefault(controllerAnnotation, "crdName", AnnotationValue::asString, EXCEPTION_SUPPLIER);
        final var configuration = new QuarkusControllerConfiguration(
            valueOrDefault(controllerAnnotation, "name", AnnotationValue::asString, () -> ControllerUtils.getDefaultResourceControllerName(info.simpleName())),
            crdName,
            valueOrDefault(controllerAnnotation, "finalizerName", AnnotationValue::asString, () -> ControllerUtils.getDefaultFinalizerName(crdName)),
            valueOrDefault(controllerAnnotation, "generationAwareEventProcessing", AnnotationValue::asBoolean, () -> true),
            valueOrDefault(controllerAnnotation, "isClusterScoped", AnnotationValue::asBoolean, () -> false),
            valueOrDefault(controllerAnnotation, "namespaces", AnnotationValue::asStringArray, () -> new String[]{}),
            crType,
            doneableClassName,
            null // todo: fix-me
        );
        
        return new ControllerConfigurationBuildItem(configuration);
    }
    
    
    private <T> T valueOrDefault(AnnotationInstance annotation, String name, Function<AnnotationValue, T> converter, Supplier<T> defaultValue) {
        return Optional.ofNullable(annotation.value(name)).map(converter).orElseGet(defaultValue);
    }
}
                                                     