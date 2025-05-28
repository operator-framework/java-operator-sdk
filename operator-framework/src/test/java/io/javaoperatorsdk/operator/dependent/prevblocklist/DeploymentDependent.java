package io.javaoperatorsdk.operator.dependent.prevblocklist;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.SSABasedGenericKubernetesResourceMatcher;

@KubernetesDependent
public class DeploymentDependent
    extends CRUDKubernetesDependentResource<Deployment, PrevAnnotationBlockCustomResource> {

  public static final String RESOURCE_NAME = "test1";

  public DeploymentDependent() {
    super(Deployment.class);
  }

  @Override
  protected Deployment desired(
      PrevAnnotationBlockCustomResource primary,
      Context<PrevAnnotationBlockCustomResource> context) {

    return new DeploymentBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(primary.getMetadata().getName())
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .withSpec(
            new DeploymentSpecBuilder()
                .withReplicas(1)
                .withSelector(
                    new LabelSelectorBuilder().withMatchLabels(Map.of("app", "nginx")).build())
                .withTemplate(
                    new PodTemplateSpecBuilder()
                        .withMetadata(
                            new ObjectMetaBuilder().withLabels(Map.of("app", "nginx")).build())
                        .withSpec(
                            new PodSpecBuilder()
                                .withContainers(
                                    new ContainerBuilder()
                                        .withName("nginx")
                                        .withImage("nginx:1.14.2")
                                        .withResources(
                                            new ResourceRequirementsBuilder()
                                                .withLimits(Map.of("cpu", new Quantity("2000m")))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build())
        .build();
  }

  // for testing purposes replicating the matching logic but with the special matcher
  @Override
  public Result<Deployment> match(
      Deployment actualResource,
      Deployment desired,
      PrevAnnotationBlockCustomResource primary,
      Context<PrevAnnotationBlockCustomResource> context) {
    final boolean matches;
    addMetadata(true, actualResource, desired, primary, context);
    if (useSSA(context)) {
      matches = new SSAMatcherWithoutSanitization().matches(actualResource, desired, context);
    } else {
      matches =
          GenericKubernetesResourceMatcher.match(desired, actualResource, false, false, context)
              .matched();
    }
    return Result.computed(matches, desired);
  }

  // using this matcher, so we are able to reproduce issue with resource limits
  static class SSAMatcherWithoutSanitization<R extends HasMetadata>
      extends SSABasedGenericKubernetesResourceMatcher<R> {

    @Override
    protected void sanitizeState(R actual, R desired, Map<String, Object> actualMap) {}
  }
}
