package io.javaoperatorsdk.crd;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.auto.service.AutoService;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersionFluentImpl;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.utils.Pluralize;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;
import io.sundr.codegen.CodegenContext;
import io.sundr.codegen.functions.ElementTo;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("io.javaoperatorsdk.crd.CRD")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class JavaToCrdAnnotationProcessor extends AbstractProcessor {

  private static final YAMLMapper mapper = new YAMLMapper();
  private static final Map<String, CustomResourceDefinition> crds = new HashMap<>();
  public static final String CUSTOM_RESOURCE_NAME = CustomResource.class.getCanonicalName();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    CodegenContext.create(processingEnv.getElementUtils(), processingEnv.getTypeUtils());

    try {
      for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
        crds.putAll(
            annotatedElements.stream()
                .map(e -> (TypeElement) e)
                .map(this::crInfo)
                .filter(Objects::nonNull)
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

  private CRInfo crInfo(TypeElement e) {
    final var superclass = (DeclaredType) e.getSuperclass();
    final var crClassName = e.getQualifiedName();
    if (superclass.asElement().toString().equals(CUSTOM_RESOURCE_NAME)) {
      final List<? extends TypeMirror> typeArguments = superclass.getTypeArguments();
      if (typeArguments.size() != 2) {
        System.out.println("Ignoring " + crClassName + " because it isn't parameterized");
        return null;
      }
      var spec = ((TypeElement) ((DeclaredType) typeArguments.get(0)).asElement());
      var status = ((TypeElement) ((DeclaredType) typeArguments.get(1)).asElement());
      System.out.println(
          "Generating CRD for "
              + crClassName
              + " (spec: "
              + spec.getQualifiedName()
              + " / status: "
              + status.getQualifiedName()
              + ")");
      return new CRInfo(e, spec, status);
    } else {
      System.out.println(
          "Ignoring " + crClassName + " because it doesn't extend " + CUSTOM_RESOURCE_NAME);
      return null;
    }
  }

  private void writeCRD(String name, CustomResourceDefinition crd) {
    try {
      final var crdFile =
          processingEnv
              .getFiler()
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

  private void enrich(CustomResourceDefinitionBuilder builder, CRInfo info) {
    final var customResource = info.customResource;
    final var group = customResource.getAnnotation(Group.class).value();
    final var crdName = crdName(customResource, group);
    final var version = customResource.getAnnotation(Version.class).value();
    if (!builder.hasSpec()) {
      builder
          .withNewMetadata()
          .withName(crdName)
          .endMetadata()
          .withNewSpec()
          .withScope(scope(customResource))
          .withNewNames()
          .withKind(kind(customResource))
          .withPlural(plural(customResource))
          .withSingular(singular(customResource))
          .withShortNames(shortNames(customResource))
          .endNames()
          .withGroup(group)
          .endSpec();
    }

    // first check that we only have one version with storage set to true
    final var spec = builder.editOrNewSpec();
    final var storage = storage(customResource);
    if (storage) {
      final var existing =
          spec.buildMatchingVersion(CustomResourceDefinitionVersionFluentImpl::hasStorage);
      if (existing != null) {
        throw new IllegalArgumentException(
            "Only one version can be stored but both "
                + version
                + " and "
                + existing.getName()
                + " are currently setting storage to true for " + crdName);
      }
    }

    // validation schema
    final var typeDef = ElementTo.TYPEDEF.apply(info.spec);
    final var schema = JsonSchema.from(typeDef);
    if (preserveUnknownFields(customResource)) {
      schema.setXKubernetesPreserveUnknownFields(true);
    }
    final var crdVersion =
        new CustomResourceDefinitionVersionBuilder()
            .withName(version)
            .withNewSchema()
            .withOpenAPIV3Schema(schema)
            .endSchema()
            .withServed(served(customResource))
            .withStorage(storage)
            .build();

    // add new version to the CRD
    spec.addToVersions(crdVersion).endSpec();
  }

  private boolean preserveUnknownFields(TypeElement customResource) {
    return customResource.getAnnotation(CRD.class).preserveUnknownFields();
  }

  private boolean storage(TypeElement customResource) {
    return customResource.getAnnotation(CRD.class).storage();
  }

  private boolean served(TypeElement customResource) {
    return customResource.getAnnotation(CRD.class).served();
  }

  private String key(CRInfo info) {
    final var customResource = info.customResource;
    return crdName(customResource, customResource.getAnnotation(Group.class).value());
  }

  private String scope(TypeElement customResource) {
    return customResource.getAnnotation(CRD.class).scope().name();
  }

  private String crdName(TypeElement customResource, String group) {
    return plural(customResource) + "." + group;
  }

  private String[] shortNames(TypeElement customResource) {
    return customResource.getAnnotation(CRD.class).shortNames();
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

  private static class CRInfo {

    private final TypeElement customResource;
    private final TypeElement spec;
    private final TypeElement status;

    public CRInfo(TypeElement customResource, TypeElement spec, TypeElement status) {
      this.customResource = customResource;
      this.spec = spec;
      this.status = status;
    }
  }
}
