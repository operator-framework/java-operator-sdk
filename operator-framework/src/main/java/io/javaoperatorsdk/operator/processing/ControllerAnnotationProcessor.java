package io.javaoperatorsdk.operator.processing;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes(
        "io.javaoperatorsdk.operator.api.Controller")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ControllerAnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements
                    = roundEnv.getElementsAnnotatedWith(annotation);
            annotatedElements.stream().filter(element -> element.getKind().equals(ElementKind.CLASS))
                    .map(e -> (TypeElement) e)
                    .forEach(this::generateDoneableClass);
        }
        return true;
    }

    private void generateDoneableClass(TypeElement controllerClassSymbol) {
        try {
            final TypeMirror resourceType = findResourceType(controllerClassSymbol);
            TypeElement customerResourceTypeElement = processingEnv
                    .getElementUtils()
                    .getTypeElement(resourceType.toString());

            final String destinationClassFileName = customerResourceTypeElement.getQualifiedName() + "Doneable";
            JavaFileObject builderFile = processingEnv.getFiler()
                    .createSourceFile(destinationClassFileName);

            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                final MethodSpec constructor = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.get(resourceType), "resource")
                        .addParameter(Function.class, "function")
                        .addStatement("super(resource,function)")
                        .build();

                final TypeSpec typeSpec = TypeSpec.classBuilder(customerResourceTypeElement.getSimpleName() + "Doneable")
                        .addAnnotation(RegisterForReflection.class)
                        .superclass(ParameterizedTypeName.get(ClassName.get(CustomResourceDoneable.class), TypeName.get(resourceType)))
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(constructor)
                        .build();

                final PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(customerResourceTypeElement);
                JavaFile file = JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec)
                        .build();
                file.writeTo(out);
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
}
