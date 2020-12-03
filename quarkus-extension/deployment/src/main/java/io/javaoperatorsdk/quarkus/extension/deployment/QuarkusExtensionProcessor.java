package io.javaoperatorsdk.quarkus.extension.deployment;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.config.ControllerConfiguration;
import io.javaoperatorsdk.quarkus.extension.QuarkusConfigurationService;
import io.javaoperatorsdk.quarkus.extension.QuarkusControllerConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

class QuarkusExtensionProcessor {
    
    private static final String FEATURE = "operator-sdk";
    private static final DotName RESOURCE_CONTROLLER = DotName.createSimple("io.javaoperatorsdk.operator.api.ResourceController");
    private static final DotName CONTROLLER = DotName.createSimple("io.javaoperatorsdk.operator.api.Controller");
    private static final Supplier<String> EXCEPTION_SUPPLIER = () -> {
        throw new IllegalArgumentException();
    };
    
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
    
    
    @BuildStep
    void createOperator(CombinedIndexBuildItem combinedIndexBuildItem,
                        BuildProducer<AdditionalBeanBuildItem> additionalBeanBuildItemBuildItem) {
        final var index = combinedIndexBuildItem.getIndex();
        final var resourceControllers = index.getAllKnownImplementors(RESOURCE_CONTROLLER);
        final var controllerConfigs = resourceControllers.stream()
            .map(this::createControllerConfiguration)
            .collect(Collectors.toList());
        final var configService = AdditionalBeanBuildItem.unremovableOf(QuarkusConfigurationService.class);
        additionalBeanBuildItemBuildItem.produce(configService);
    }
    
    private ControllerConfiguration createControllerConfiguration(ClassInfo info) {
        // first retrieve the custom resource class
        final var rcInterface = info.interfaceTypes().stream()
            .filter(t -> t.name().equals(RESOURCE_CONTROLLER))
            .findFirst()
            .map(Type::asParameterizedType)
            .orElseThrow(); // shouldn't happen since we're only dealing with ResourceController implementors already
        final var crType = rcInterface.arguments().get(0).name().toString();
        final Class<?> crClass;
        try {
            crClass = Class.forName(crType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + crType);
        }
        
        
        // generate associated Doneable class
        /*final var doneableClassName = crType + "Doneable";
        try (ClassCreator cc = ClassCreator.builder()
//            .classOutput(classOutput)
            .className(doneableClassName)
            .superClass(CustomResourceDoneable.class)
            .build()) {
            
            MethodCreator ctor = cc.getMethodCreator("<init>", void.class, crClass);
            ctor.setModifiers(Modifier.PUBLIC);
            ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(CustomResourceDoneable.class, crClass, Function.class), ctor.getThis(), ctor.getMethodParam(0), ctor.invokeStaticMethod(MethodDescriptor.ofMethod(Function.class, "identity", Function.class)));
        }*/
        
        // get Controller annotation
        final var controllerAnnotation = info.classAnnotation(CONTROLLER);
        
        
        final var crdName = valueOrDefault(controllerAnnotation, "crdName", AnnotationValue::asString, EXCEPTION_SUPPLIER);
        return new QuarkusControllerConfiguration(
            valueOrDefault(controllerAnnotation, "name", AnnotationValue::asString, () -> ControllerUtils.getDefaultResourceControllerName(info.simpleName())),
            crdName,
            valueOrDefault(controllerAnnotation, "finalizerName", AnnotationValue::asString, () -> ControllerUtils.getDefaultFinalizerName(crdName)),
            valueOrDefault(controllerAnnotation, "generationAwareEventProcessing", AnnotationValue::asBoolean, () -> true),
            valueOrDefault(controllerAnnotation, "isClusterScoped", AnnotationValue::asBoolean, () -> false),
            valueOrDefault(controllerAnnotation, "namespaces", AnnotationValue::asStringArray, () -> new String[]{}),
            crClass,
            null,
            null
        );
    }
    
    private <T> T valueOrDefault(AnnotationInstance annotation, String name, Function<AnnotationValue, T> converter, Supplier<T> defaultValue) {
        return Optional.ofNullable(annotation.value(name)).map(converter).orElseGet(defaultValue);
    }
}
                                                     