package io.javaoperatorsdk.quarkus.extension.deployment;

import java.lang.reflect.Modifier;
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
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
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
    @Record(ExecutionTime.RUNTIME_INIT)
    void createDoneableClasses(CombinedIndexBuildItem combinedIndexBuildItem,
                               BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
                               BuildProducer<GeneratedClassBuildItem> generatedClass,
                               ConfigurationServiceRecorder recorder) {
        final var index = combinedIndexBuildItem.getIndex();
        final var resourceControllers = index.getAllKnownImplementors(RESOURCE_CONTROLLER);
        final var controllerConfigs = resourceControllers.stream()
            .map(ci -> createControllerConfiguration(ci, new GeneratedClassGizmoAdaptor(generatedClass, true)))
            .collect(Collectors.toList());
        syntheticBeanBuildItemBuildProducer.produce(SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
            .scope(Singleton.class)
            .setRuntimeInit()
            .supplier(recorder.configurationServiceSupplier(controllerConfigs))
            .done());
    }
    
    private ControllerConfiguration createControllerConfiguration(ClassInfo info, ClassOutput classOutput) {
        // first retrieve the custom resource class
        final var rcInterface = info.interfaceTypes().stream()
            .filter(t -> t.name().equals(RESOURCE_CONTROLLER))
            .findFirst()
            .map(Type::asParameterizedType)
            .orElseThrow(); // shouldn't happen since we're only dealing with ResourceController implementors already
        final var crType = rcInterface.arguments().get(0).name().toString();
        final Class<?> crClass;
        try {
            crClass = Thread.currentThread().getContextClassLoader().loadClass(crType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + crType);
        }
        
        // generate associated Doneable class
        final var doneableClassName = crType + "Doneable";
        try (ClassCreator cc = ClassCreator.builder()
            .classOutput(classOutput)
            .className(doneableClassName)
            .superClass(CustomResourceDoneable.class)
            .build()) {
            
            MethodCreator ctor = cc.getMethodCreator("<init>", void.class, crClass);
            ctor.setModifiers(Modifier.PUBLIC);
            ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(CustomResourceDoneable.class, crClass, Function.class), ctor.getThis(), ctor.getMethodParam(0), ctor.invokeStaticMethod(MethodDescriptor.ofMethod(Function.class, "identity", Function.class)));
        }
        
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
            null,  // todo: fix-me
            null // todo: fix-me
        );
    }
    
    private <T> T valueOrDefault(AnnotationInstance annotation, String name, Function<AnnotationValue, T> converter, Supplier<T> defaultValue) {
        return Optional.ofNullable(annotation.value(name)).map(converter).orElseGet(defaultValue);
    }
}
                                                     