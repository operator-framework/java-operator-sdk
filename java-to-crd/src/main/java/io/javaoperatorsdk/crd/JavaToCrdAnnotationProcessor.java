package io.javaoperatorsdk.crd;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.auto.service.AutoService;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionBuilder;
import io.fabric8.kubernetes.client.utils.Pluralize;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
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
  private static final Map<String, CustomResourceDefinition> crds = new HashMap<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    env = processingEnv;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        crds.putAll(
            annotatedElements.stream()
                .map(e -> (TypeElement) e)
                .map(
                    e -> {
                      System.out.println("Generating CRD for " + e.getQualifiedName());
                      return e;
                    })
                .collect(
                    Collectors.groupingBy(
                        this::key,
                        Collector.of(
                            CustomResourceDefinitionBuilder::new,
                            this::enrich,
                            this::combine,
                            CustomResourceDefinitionBuilder::build))));
      }
    } finally {
      if (roundEnv.processingOver()) {
        crds.forEach(this::writeCRD);
      }
    }
    return true;
  }

  private void writeCRD(String name, CustomResourceDefinition crd) {
    try {
      final var crdFile =
          env.getFiler()
              .createResource(
                  StandardLocation.CLASS_OUTPUT, "", "javaoperatorsdk/" + name + ".yml");
      final var writer = crdFile.openWriter();
      writer.write(mapper.writeValueAsString(crd));
      writer.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CustomResourceDefinitionBuilder combine(
      CustomResourceDefinitionBuilder initial, CustomResourceDefinitionBuilder toMerge) {
    return initial
        .withNewSpecLike(initial.getSpec())
        .addAllToVersions(toMerge.getSpec().getVersions())
        .endSpec();
  }

  private void enrich(CustomResourceDefinitionBuilder builder, TypeElement customResource) {
    final var group = customResource.getAnnotation(Group.class).value();
    final var crdName = crdName(customResource, group);
    final var version = customResource.getAnnotation(Version.class).value();
    if (!builder.hasSpec()) {
      builder
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
          .endSpec();
    }
    builder
        .editOrNewSpec()
        .addToVersions(new CustomResourceDefinitionVersionBuilder().withName(version).build())
        .endSpec();
  }

  private String key(TypeElement customResource) {
    return crdName(customResource, customResource.getAnnotation(Group.class).value());
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
