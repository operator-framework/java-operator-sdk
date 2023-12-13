package io.javaoperatorsdk.operator.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

// todo label selector needs to added explicitly
@ControllerConfiguration(onAddFilter = NonMarkedForDeletionAddFilter.class,
    onUpdateFilter = NonMarkedForDeletionUpdateFilter.class)
public class NamespaceDeletionRoleReconciler
    implements Reconciler<Role>, Cleaner<Role>, EventSourceInitializer<Role> {

  public static final String TARGET_RESOURCES_IN_NAMESPACE_TO_ROLE_INDEX =
      "target-resources-in-namespace";
  public static final String RESOURCE_NAMESPACE_INDEX = "resource-namespace-index";

  final List<Class<? extends HasMetadata>> resourceClasses;
  final Set<String> resourcePlurals;
  final Map<Class<? extends HasMetadata>, String> classToPlural;
  final Map<String, Class<? extends HasMetadata>> pluralToClass;

  public NamespaceDeletionRoleReconciler(List<Class<? extends HasMetadata>> resourceClasses) {
    this.resourceClasses = resourceClasses;
    this.classToPlural =
        resourceClasses.stream().collect(Collectors.toMap(c -> c, HasMetadata::getPlural));
    this.pluralToClass =
        resourceClasses.stream().collect(Collectors.toMap(HasMetadata::getPlural, c -> c));
    this.resourcePlurals =
        resourceClasses.stream().map(HasMetadata::getPlural).collect(Collectors.toSet());
  }

  @Override
  public UpdateControl<Role> reconcile(Role resource, Context<Role> context) throws Exception {
    return UpdateControl.noUpdate();
  }

  @SuppressWarnings("unchecked")
  @Override
  public DeleteControl cleanup(Role resource, Context<Role> context) {
    AtomicBoolean watchedResourceExistsInNamespace = new AtomicBoolean(false);
    resource.getRules().forEach(rule -> {
      rule.getResources().forEach(r -> {
        if (resourcePlurals.contains(r)) {
          InformerEventSource<HasMetadata, Role> es =
              (InformerEventSource<HasMetadata, Role>) context.eventSourceRetriever()
                  .getResourceEventSourceFor(pluralToClass.get(r));
          var resources =
              es.byIndex(RESOURCE_NAMESPACE_INDEX, resource.getMetadata().getNamespace());
          if (!resources.isEmpty()) {
            watchedResourceExistsInNamespace.set(true);
          }
        }
      });
    });
    if (watchedResourceExistsInNamespace.get()) {
      return DeleteControl.noFinalizerRemoval();
    } else {
      return DeleteControl.defaultDelete();
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Role> context) {
    var allPlurals = classToPlural.values();
    context.getPrimaryCache().addIndexer(TARGET_RESOURCES_IN_NAMESPACE_TO_ROLE_INDEX, r -> {
      // resource-plural+namespace -> role (in that namespace)
      List<String> result = new ArrayList<>();
      r.getRules().forEach(rule -> rule.getResources().forEach(resource -> {
        if (allPlurals.contains(resource)) {
          result.add(keyFor(r.getMetadata().getNamespace(), resource));
        }
      }));
      return result;
    });

    return resourceClasses.stream()
        .map(c -> {
          var ies =
              new InformerEventSource<HasMetadata, Role>(InformerConfiguration.from(c, context)
                  .withSecondaryToPrimaryMapper((SecondaryToPrimaryMapper) resource -> {
                    HasMetadata rm = (HasMetadata) resource;
                    var roles = context.getPrimaryCache().byIndex(
                        TARGET_RESOURCES_IN_NAMESPACE_TO_ROLE_INDEX,
                        keyFor(rm.getMetadata().getNamespace(), rm.getPlural()));
                    return roles.stream().map(r -> new ResourceID(r.getMetadata().getName(),
                        r.getMetadata().getNamespace())).collect(Collectors.toSet());
                  })
                  .build(), context);
          ies.addIndexer(RESOURCE_NAMESPACE_INDEX, r -> List.of(r.getMetadata().getNamespace()));
          return ies;
        })
        .collect(Collectors.toMap(i -> classToPlural.get(i.resourceType()), i -> i));
  }

  public static String keyFor(String namespace, String resourcePlural) {
    return resourcePlural + "-" + namespace;
  }

}
