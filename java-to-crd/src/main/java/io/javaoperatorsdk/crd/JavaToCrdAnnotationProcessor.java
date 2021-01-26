package io.javaoperatorsdk.crd;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.auto.service.AutoService;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionBuilder;
import io.fabric8.kubernetes.client.utils.Pluralize;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("io.javaoperatorsdk.crd.CRD")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class JavaToCrdAnnotationProcessor extends AbstractProcessor {

  private static final YAMLMapper mapper = new YAMLMapper();
  private static ProcessingEnvironment env;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    env = processingEnv;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        annotatedElements.stream().map(e -> (TypeElement) e).forEach(this::generateCRD);
      }
    } finally {
      if (roundEnv.processingOver()) {}
    }
    return true;
  }

  private void generateCRD(TypeElement customResource) {
    try {
      final var group = customResource.getAnnotation(Group.class).value();
      final var version = customResource.getAnnotation(Version.class).value();
      final var crdName = crdName(customResource, group);
      final var crd =
          new CustomResourceDefinitionBuilder()
              .withNewMetadata()
              .withName(crdName)
              .endMetadata()
              .withNewSpec()
              .withNewNames()
              .withKind(kind(customResource))
              .withPlural(plural(customResource))
              .withSingular(singular(customResource))
              .withShortNames(shortNames(customResource))
              .endNames()
              .withGroup(group)
              .withVersions(new CustomResourceDefinitionVersionBuilder().withName(version).build())
              .endSpec()
              .build();
      final var crdFile =
          env.getFiler()
              .createResource(
                  StandardLocation.CLASS_OUTPUT, "", "javaoperatorsdk/" + crdName + ".yml");
      final var writer = crdFile.openWriter();
      writer.write(mapper.writeValueAsString(crd));
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String crdName(TypeElement customResource, String group) {
    return plural(customResource) + "." + group;
  }

  private String[] shortNames(TypeElement customResource) {
    return Optional.ofNullable(customResource.getAnnotation(CRD.class))
        .map(CRD::shortNames)
        .orElse(new String[] {});
  }

  private String singular(TypeElement customResource) {
    return Optional.ofNullable(customResource.getAnnotation(Singular.class))
        .map(Singular::value)
        .orElse(kind(customResource).toLowerCase(Locale.ROOT));
  }

  private String plural(TypeElement customResource) {
    return Optional.ofNullable(customResource.getAnnotation(Plural.class))
        .map(Plural::value)
        .map(s -> s.toLowerCase(Locale.ROOT))
        .orElse(Pluralize.toPlural(singular(customResource)));
  }

  private String kind(TypeElement customResource) {
    return Optional.ofNullable(customResource.getAnnotation(Kind.class))
        .map(Kind::value)
        .orElse(customResource.getSimpleName().toString());
  }
}
