package io.javaoperatorsdk.operator.processing.annotation;

import static io.javaoperatorsdk.operator.ControllerUtils.CONTROLLERS_RESOURCE_PATH;
import static io.javaoperatorsdk.operator.ControllerUtils.DONEABLES_RESOURCE_PATH;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("io.javaoperatorsdk.operator.api.Controller")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ControllerAnnotationProcessor extends AbstractProcessor {

  private AccumulativeMappingWriter controllersResourceWriter;
  private AccumulativeMappingWriter doneablesResourceWriter;
  private TypeParameterResolver typeParameterResolver;
  private final Set<String> generatedDoneableClassFiles = new HashSet<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    controllersResourceWriter =
        new AccumulativeMappingWriter(CONTROLLERS_RESOURCE_PATH, processingEnv)
            .loadExistingMappings();
    doneablesResourceWriter =
        new AccumulativeMappingWriter(DONEABLES_RESOURCE_PATH, processingEnv)
            .loadExistingMappings();

    typeParameterResolver = initializeResolver(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        annotatedElements.stream()
            .filter(element -> element.getKind().equals(ElementKind.CLASS))
            .map(e -> (TypeElement) e)
            .forEach(this::generateDoneableClass);
      }
    } finally {
      if (roundEnv.processingOver()) {
        controllersResourceWriter.flush();
        doneablesResourceWriter.flush();
      }
    }
    return true;
  }

  private TypeParameterResolver initializeResolver(ProcessingEnvironment processingEnv) {
    final DeclaredType resourceControllerType =
        processingEnv
            .getTypeUtils()
            .getDeclaredType(
                processingEnv
                    .getElementUtils()
                    .getTypeElement(ResourceController.class.getCanonicalName()),
                processingEnv.getTypeUtils().getWildcardType(null, null));
    return new TypeParameterResolver(resourceControllerType, 0);
  }

  private void generateDoneableClass(TypeElement controllerClassSymbol) {
    try {
      System.out.println(controllerClassSymbol.toString());
      final TypeMirror resourceType = findResourceType(controllerClassSymbol);
      System.out.println("the resource type is " + resourceType);

      TypeElement customerResourceTypeElement =
          processingEnv.getElementUtils().getTypeElement(resourceType.toString());
      System.out.println("the customerResourceTypeElement  is " + customerResourceTypeElement);

      final String doneableClassName = customerResourceTypeElement.getSimpleName() + "Doneable";
      final String destinationClassFileName =
          customerResourceTypeElement.getQualifiedName() + "Doneable";
      final TypeName customResourceType = TypeName.get(resourceType);

      if (!generatedDoneableClassFiles.add(destinationClassFileName)) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.NOTE,
                String.format(
                    "%s already exists! adding the mapping to the %s",
                    destinationClassFileName, CONTROLLERS_RESOURCE_PATH));
        controllersResourceWriter.add(
            controllerClassSymbol.getQualifiedName().toString(), customResourceType.toString());
        return;
      }
      JavaFileObject builderFile =
          processingEnv.getFiler().createSourceFile(destinationClassFileName);

      try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
        controllersResourceWriter.add(
            controllerClassSymbol.getQualifiedName().toString(), customResourceType.toString());
        final MethodSpec constructor =
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(customResourceType, "resource")
                .addParameter(Function.class, "function")
                .addStatement("super(resource,function)")
                .build();

        final TypeSpec typeSpec =
            TypeSpec.classBuilder(doneableClassName)
                .superclass(
                    ParameterizedTypeName.get(
                        ClassName.get(CustomResourceDoneable.class), customResourceType))
                .addModifiers(Modifier.PUBLIC)
                .addMethod(constructor)
                .build();

        final PackageElement packageElement =
            processingEnv.getElementUtils().getPackageOf(customerResourceTypeElement);
        final var packageQualifiedName = packageElement.getQualifiedName().toString();
        JavaFile file = JavaFile.builder(packageQualifiedName, typeSpec).build();
        file.writeTo(out);
        doneablesResourceWriter.add(
            customResourceType.toString(),
            makeQualifiedClassName(packageQualifiedName, doneableClassName));
      }
    } catch (Exception ioException) {
      ioException.printStackTrace();
    }
  }

  private TypeMirror findResourceType(TypeElement controllerClassSymbol) {
    try {
      return typeParameterResolver.resolve(
          processingEnv.getTypeUtils(), (DeclaredType) controllerClassSymbol.asType());
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
