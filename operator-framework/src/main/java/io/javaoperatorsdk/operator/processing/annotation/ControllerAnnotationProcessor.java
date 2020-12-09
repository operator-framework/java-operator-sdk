package io.javaoperatorsdk.operator.processing.annotation;

import static io.javaoperatorsdk.operator.ControllerUtils.CONTROLLERS_RESOURCE_PATH;
import static io.javaoperatorsdk.operator.ControllerUtils.DONEABLES_RESOURCE_PATH;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("io.javaoperatorsdk.operator.api.Controller")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ControllerAnnotationProcessor extends AbstractProcessor {

  private AccumulativeMappingWriter controllersResourceWriter;
  private AccumulativeMappingWriter doneablesResourceWriter;
  private Set<String> generatedDoneableClassFiles = new HashSet<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    controllersResourceWriter =
        new AccumulativeMappingWriter(CONTROLLERS_RESOURCE_PATH, processingEnv)
            .loadExistingMappings();
    doneablesResourceWriter =
        new AccumulativeMappingWriter(DONEABLES_RESOURCE_PATH, processingEnv)
            .loadExistingMappings();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        annotatedElements.stream()
            .filter(element -> element.getKind().equals(ElementKind.CLASS))
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

  private TypeMirror findResourceType(TypeElement controllerClassSymbol) throws Exception {
    try {
      final var chain = findChain((DeclaredType) controllerClassSymbol.asType());
      final var customResourceClass = getCustomResourceClass(chain);
      return customResourceClass;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private List<TypeMirror> collectAllInterfaces(TypeElement element) {
    try {
      List<TypeMirror> interfaces = new ArrayList<>(element.getInterfaces());
      interfaces.add(element.getSuperclass());
      TypeElement superclass = ((TypeElement) ((DeclaredType) element.getSuperclass()).asElement());
      while (superclass.getSuperclass().getKind() != TypeKind.NONE) {
        interfaces.addAll(superclass.getInterfaces());
        superclass = ((TypeElement) ((DeclaredType) superclass.getSuperclass()).asElement());
        interfaces.add(element.getSuperclass());
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

  private List<DeclaredType> findChain(DeclaredType declaredType) {
    final var resourceControllerType =
        processingEnv
            .getTypeUtils()
            .getDeclaredType(
                processingEnv
                    .getElementUtils()
                    .getTypeElement(ResourceController.class.getCanonicalName()),
                processingEnv.getTypeUtils().getWildcardType(null, null));
    final var result = new ArrayList<DeclaredType>();
    result.add(declaredType);
    var superElement = ((TypeElement) ((DeclaredType) declaredType).asElement());
    var superclass = (DeclaredType) superElement.getSuperclass();
    boolean interfaceFound = false;
    final var matchingInterfaces =
        superElement.getInterfaces().stream()
            .filter(
                intface ->
                    processingEnv.getTypeUtils().isAssignable(intface, resourceControllerType))
            .map(i -> (DeclaredType) i)
            .collect(Collectors.toList());
    if (!matchingInterfaces.isEmpty()) {
      result.addAll(matchingInterfaces);
      interfaceFound = true;
    }

    while (superclass.getKind() != TypeKind.NONE) {
      if (interfaceFound) {
        final var lastFoundInterface = result.get(result.size() - 1);
        final var marchingInterfaces =
            ((TypeElement) lastFoundInterface.asElement())
                .getInterfaces().stream()
                    .filter(
                        intface ->
                            processingEnv
                                .getTypeUtils()
                                .isAssignable(intface, resourceControllerType))
                    .map(i -> (DeclaredType) i)
                    .collect(Collectors.toList());

        if (marchingInterfaces.size() > 0) {
          result.addAll(marchingInterfaces);
          continue;
        } else {
          break;
        }
      }

      if (processingEnv.getTypeUtils().isAssignable(superclass, resourceControllerType)) {
        result.add(superclass);
      }

      superElement = (TypeElement) superclass.asElement();
      final var matchedInterfaces =
          superElement.getInterfaces().stream()
              .filter(
                  intface ->
                      processingEnv.getTypeUtils().isAssignable(intface, resourceControllerType))
              .map(i -> (DeclaredType) i)
              .collect(Collectors.toList());
      if (matchedInterfaces.size() > 0) {
        result.addAll(matchedInterfaces);
        interfaceFound = true;
        continue;
      }

      if (superElement.getSuperclass().getKind() == TypeKind.NONE) {
        break;
      }
      superclass = (DeclaredType) superElement.getSuperclass();
    }

    return result;
  }

  private TypeMirror getCustomResourceClass(List<DeclaredType> chain) {
    var lastIndex = chain.size() - 1;
    String typeName;
    final List<? extends TypeMirror> typeArguments = (chain.get(lastIndex)).getTypeArguments();
    if (typeArguments.get(0).getKind() == TYPEVAR) {
      typeName = ((TypeVariable) typeArguments.get(0)).asElement().getSimpleName().toString();
    } else if (typeArguments.get(0).getKind() == DECLARED) {
      return typeArguments.get(0);
    } else {
      typeName = "";
    }

    while (lastIndex > 0) {
      lastIndex -= 1;
      final List<? extends TypeMirror> tArguments = (chain.get(lastIndex)).getTypeArguments();
      final List<? extends TypeParameterElement> typeParameters =
          ((TypeElement) ((chain.get(lastIndex)).asElement())).getTypeParameters();
      final String tName = typeName;
      final var typeIndex =
          IntStream.range(0, typeParameters.size())
              .filter(i -> typeParameters.get(i).getSimpleName().toString().equals(tName))
              .findFirst()
              .getAsInt();

      final TypeMirror matchedType = tArguments.get(typeIndex);
      if (matchedType.getKind() == TYPEVAR) {
        typeName = ((TypeVariable) matchedType).asElement().getSimpleName().toString();
      } else if (matchedType.getKind() == DECLARED) {
        return matchedType;
      } else {
        typeName = "";
      }
    }
    return null;
  }
}
