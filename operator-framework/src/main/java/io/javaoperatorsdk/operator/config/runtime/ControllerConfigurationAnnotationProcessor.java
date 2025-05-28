package io.javaoperatorsdk.operator.config.runtime;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import com.squareup.javapoet.TypeName;

import static io.javaoperatorsdk.operator.config.runtime.RuntimeControllerMetadata.RECONCILERS_RESOURCE_PATH;

@SupportedAnnotationTypes("io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration")
public class ControllerConfigurationAnnotationProcessor extends AbstractProcessor {

  private static final Logger log =
      LoggerFactory.getLogger(ControllerConfigurationAnnotationProcessor.class);

  private AccumulativeMappingWriter controllersResourceWriter;
  private TypeParameterResolver typeParameterResolver;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    controllersResourceWriter =
        new AccumulativeMappingWriter(RECONCILERS_RESOURCE_PATH, processingEnv)
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
            .forEach(this::recordCRType);
      }
    } finally {
      if (roundEnv.processingOver()) {
        controllersResourceWriter.flush();
      }
    }
    return true;
  }

  private TypeParameterResolver initializeResolver(ProcessingEnvironment processingEnv) {
    final DeclaredType resourceControllerType =
        processingEnv
            .getTypeUtils()
            .getDeclaredType(
                processingEnv.getElementUtils().getTypeElement(Reconciler.class.getCanonicalName()),
                processingEnv.getTypeUtils().getWildcardType(null, null));
    return new TypeParameterResolver(resourceControllerType, 0);
  }

  private void recordCRType(TypeElement controllerClassSymbol) {
    try {
      final TypeMirror resourceType = findResourceType(controllerClassSymbol);
      if (resourceType == null) {
        controllersResourceWriter.add(
            controllerClassSymbol.getQualifiedName().toString(),
            CustomResource.class.getCanonicalName());
        System.out.println(
            "No defined resource type for '"
                + controllerClassSymbol.getQualifiedName()
                + "': ignoring!");
        return;
      }
      final TypeName customResourceType = TypeName.get(resourceType);
      controllersResourceWriter.add(
          controllerClassSymbol.getQualifiedName().toString(), customResourceType.toString());

    } catch (Exception ioException) {
      log.error("Error", ioException);
    }
  }

  private TypeMirror findResourceType(TypeElement controllerClassSymbol) {
    try {

      return typeParameterResolver.resolve(
          processingEnv.getTypeUtils(), (DeclaredType) controllerClassSymbol.asType());
    } catch (Exception e) {
      log.error("Error", e);
      return null;
    }
  }
}
