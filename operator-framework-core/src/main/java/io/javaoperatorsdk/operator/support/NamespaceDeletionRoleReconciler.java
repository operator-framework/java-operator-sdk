package io.javaoperatorsdk.operator.support;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.fabric8.kubernetes.api.model.HasMetadata.getPlural;

@ControllerConfiguration
public class NamespaceDeletionRoleReconciler
    implements Reconciler<Role>, Cleaner<Role>, EventSourceInitializer<Role> {

  final List<Class<? extends HasMetadata>> resourceClasses;
  final Map<Class<? extends HasMetadata>, String> plurals;

  public NamespaceDeletionRoleReconciler(List<Class<? extends HasMetadata>> resourceClasses) {
    this.resourceClasses = resourceClasses;
    this.plurals =
        resourceClasses.stream().collect(Collectors.toMap(c -> c, HasMetadata::getPlural));
  }

  @Override
  public UpdateControl<Role> reconcile(Role resource, Context<Role> context) throws Exception {
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(Role resource, Context<Role> context) {
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Role> context) {
    // var allPlurals = plurals.values();
    // context.getPrimaryCache().addIndexer("namespace-target-resource-index", r-> {
    // r.getRules().stream().map() // todo
    //
    // });

    return resourceClasses.stream()
        .map(c -> new InformerEventSource(InformerConfiguration.from(c, context)
            .withSecondaryToPrimaryMapper((SecondaryToPrimaryMapper) resource -> {
              return null;
            })
            .build(), context))
        .collect(Collectors.toMap(i -> plurals.get(i.resourceType()), i -> i));

  }
}
