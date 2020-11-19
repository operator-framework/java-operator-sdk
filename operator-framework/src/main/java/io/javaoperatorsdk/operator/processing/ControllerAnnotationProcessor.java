package io.javaoperatorsdk.operator.processing;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.code.Symbol;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
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
                    .forEach(controllerClassSymbol -> {
                        generateDoneableClass(controllerClassSymbol);
                    });
        }
        return false;
    }

    private void generateDoneableClass(Symbol.ClassSymbol controllerClassSymbol) {
        JavaFileObject builderFile = null;
        try {
            // TODO: the resourceType retrieval logic is currently very fragile, done for testing purposes and need to be improved to cover all possible conditions
            final TypeMirror resourceType = ((DeclaredType) controllerClassSymbol.getInterfaces().head).getTypeArguments().get(0);
            Symbol.ClassSymbol customerResourceSymbol = (Symbol.ClassSymbol) processingEnv.getElementUtils().getTypeElement(resourceType.toString());
            builderFile = processingEnv.getFiler()
                    .createSourceFile(customerResourceSymbol.className() + "Doneable");
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                out.println("package " + customerResourceSymbol.packge().fullname + ";");
                out.println("import io.quarkus.runtime.annotations.RegisterForReflection;");
                out.println("import io.fabric8.kubernetes.api.builder.Function;");
                out.println("import io.fabric8.kubernetes.client.CustomResourceDoneable;");
                out.println();
                out.println("@RegisterForReflection");
                out.println("public class " + customerResourceSymbol.name + "Doneable " + " extends CustomResourceDoneable<" + customerResourceSymbol.name + "> {");
                out.println("public " + customerResourceSymbol.name + "Doneable(" + customerResourceSymbol.name + " resource, Function function){ super(resource,function);}");
                out.println("}");
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
