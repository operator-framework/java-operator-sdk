package io.javaoperatorsdk.operator.processing;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            annotatedElements.stream().filter(element -> element instanceof Symbol.ClassSymbol)
                    .map(e -> (Symbol.ClassSymbol) e)
                    .forEach(this::generateDoneableClass);
        }
        return false;
    }

    private void generateDoneableClass(Symbol.ClassSymbol controllerClassSymbol) {
        try {
            final TypeMirror resourceType = findResourceType(controllerClassSymbol);
            Symbol.ClassSymbol customerResourceSymbol = (Symbol.ClassSymbol) processingEnv
                    .getElementUtils()
                    .getTypeElement(resourceType.toString());

            JavaFileObject builderFile = processingEnv.getFiler()
                    .createSourceFile(customerResourceSymbol.className() + "Doneable");

            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                final MethodSpec constructor = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.get(resourceType), "resource")
                        .addParameter(Function.class, "function")
                        .addStatement("super(resource,function)")
                        .build();

                final TypeSpec typeSpec = TypeSpec.classBuilder(customerResourceSymbol.name + "Doneable")
                        .addAnnotation(RegisterForReflection.class)
                        .superclass(ParameterizedTypeName.get(ClassName.get(CustomResourceDoneable.class), TypeName.get(resourceType)))
                        .addModifiers(Modifier.PUBLIC)
                        .addMethod(constructor)
                        .build();

                JavaFile file = JavaFile.builder(customerResourceSymbol.packge().fullname.toString(), typeSpec)
                        .build();
                file.writeTo(out);
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private TypeMirror findResourceType(Symbol.ClassSymbol controllerClassSymbol) throws Exception {
        final Type controllerType = collectAllInterfaces(controllerClassSymbol)
                .stream()
                .filter(i -> i.toString()
                        .startsWith(ResourceController.class.getCanonicalName())
                )
                .findFirst()
                .orElseThrow(() -> new Exception("ResourceController is not implemented by " + controllerClassSymbol.toString()));

        final TypeMirror resourceType = controllerType.getTypeArguments().get(0);
        return resourceType;
    }

    private List<Type> collectAllInterfaces(Symbol.ClassSymbol classSymbol) {
        List<Type> interfaces = new ArrayList<>(classSymbol.getInterfaces());
        Symbol.ClassSymbol superclass = (Symbol.ClassSymbol) processingEnv.getTypeUtils().asElement(classSymbol.getSuperclass());

        while (superclass != null) {
            interfaces.addAll(superclass.getInterfaces());
            superclass = (Symbol.ClassSymbol) processingEnv.getTypeUtils().asElement(superclass.getSuperclass());
        }

        return interfaces;
    }
}
