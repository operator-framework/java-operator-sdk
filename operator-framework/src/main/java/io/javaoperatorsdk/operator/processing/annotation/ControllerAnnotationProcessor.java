package io.javaoperatorsdk.operator.processing.annotation;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.ResourceController;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.javaoperatorsdk.operator.ControllerUtils.CONTROLLERS_RESOURCE_PATH;
import static io.javaoperatorsdk.operator.ControllerUtils.DONEABLES_RESOURCE_PATH;

@SupportedAnnotationTypes(
        "io.javaoperatorsdk.operator.api.Controller")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ControllerAnnotationProcessor extends AbstractProcessor {
    private AccumulativeMappingWriter controllersResourceWriter;
    private AccumulativeMappingWriter doneablesResourceWriter;
    private Set<String> generatedDoneableClassFiles = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        controllersResourceWriter = new AccumulativeMappingWriter(CONTROLLERS_RESOURCE_PATH, processingEnv)
                .loadExistingMappings();
        doneablesResourceWriter = new AccumulativeMappingWriter(DONEABLES_RESOURCE_PATH, processingEnv)
                .loadExistingMappings();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            for (TypeElement annotation : annotations) {
                Set<? extends Element> annotatedElements
                        = roundEnv.getElementsAnnotatedWith(annotation);
                annotatedElements.stream().filter(element -> element.getKind().equals(ElementKind.CLASS))
                        .map(e -> (TypeElement) e)
                        .forEach(e -> this.generateDoneableClass(e));
            }
        } finally {
            if (roundEnv.processingOver()) {
                controllersResourceWriter.flush();
                doneablesResourceWriter.flush();
            }
        }
        return true;
    }

    private void generateDoneableClass(TypeElement controllerClassSymbol) {
        try {
            final TypeMirror resourceType = findResourceType(controllerClassSymbol);

            TypeElement customerResourceTypeElement = processingEnv
                    .getElementUtils()
                    .getTypeElement(resourceType.toString());

            final String doneableClassName = customerResourceTypeElement.getSimpleName() + "Doneable";
            final String destinationClassFileName = customerResourceTypeElement.getQualifiedName() + "Doneable";
            final TypeName customResourceType = TypeName.get(resourceType);

            if (!generatedDoneableClassFiles.add(destinationClassFileName)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        String.format(
                                "%s already exists! adding the mapping to the %s",
                                destinationClassFileName,
                                CONTROLLERS_RESOURCE_PATH)
                );
                controllersResourceWriter.add(controllerClassSymbol.getQualifiedName().toString(), customResourceType.toString());
                return;
            }
            JavaFileObject builderFile = processingEnv.getFiler()
                    .createSourceFile(destinationClassFileName);

            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                controllersResourceWriter.add(controllerClassSymbol.getQualifiedName().toString(), customResourceType.toString());
                final MethodSpec constructor = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(customResourceType, "resource")
                        .addParameter(Function.class, "function")
                        .addStatement("super(resource,function)")
                        .build();


                final TypeSpec typeSpec = TypeSpec.classBuilder(doneableClassName)
                        .superclass(ParameterizedTypeName.get(ClassName.get(CustomResourceDoneable.class), customResourceType))
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(constructor)
                        .build();

                final PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(customerResourceTypeElement);
                final var packageQualifiedName = packageElement.getQualifiedName().toString();
                JavaFile file = JavaFile.builder(packageQualifiedName, typeSpec)
                        .build();
                file.writeTo(out);
                doneablesResourceWriter.add(customResourceType.toString(), makeQualifiedClassName(packageQualifiedName, doneableClassName));
            }
        } catch (Exception ioException) {
            ioException.printStackTrace();
        }
    }

    private TypeMirror findResourceType(TypeElement controllerClassSymbol) throws Exception {
        try {
            final DeclaredType controllerType = collectAllInterfaces(controllerClassSymbol)
                    .stream()
                    .filter(i -> i.toString()
                            .startsWith(ResourceController.class.getCanonicalName())
                    )
                    .findFirst()
                    .orElseThrow(() -> new Exception("ResourceController is not implemented by " + controllerClassSymbol.toString()));
            return controllerType.getTypeArguments().get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<DeclaredType> collectAllInterfaces(TypeElement element) {
        try {
            List<DeclaredType> interfaces = new ArrayList<>(element.getInterfaces()).stream().map(t -> (DeclaredType) t).collect(Collectors.toList());
            TypeElement superclass = ((TypeElement) ((DeclaredType) element.getSuperclass()).asElement());
            while (superclass.getSuperclass().getKind() != TypeKind.NONE) {
                interfaces.addAll(superclass.getInterfaces().stream().map(t -> (DeclaredType) t).collect(Collectors.toList()));
                superclass = ((TypeElement) ((DeclaredType) superclass.getSuperclass()).asElement());
            }
            return interfaces;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String makeQualifiedClassName(String packageName, String className) {
        if (packageName.equals("")) {
            return className;
        }
        return packageName + "." + className;
    }
}
