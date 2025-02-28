package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.api.builder.Builder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class ReconcilerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
  protected static final String MISSING_GROUP_SUFFIX = ".javaoperatorsdk.io";
  private static final String GET_SPEC = "getSpec";
  private static final String SET_SPEC = "setSpec";
  private static final Pattern API_URI_PATTERN =
      Pattern.compile(".*http(s?)://[^/]*/api(s?)/(\\S*).*"); // NOSONAR: input is controlled

  // prevent instantiation of util class
  private ReconcilerUtils() {}

  public static boolean isFinalizerValid(String finalizer) {
    return HasMetadata.validateFinalizer(finalizer);
  }

  public static String getResourceTypeNameWithVersion(Class<? extends HasMetadata> resourceClass) {
    final var version = HasMetadata.getVersion(resourceClass);
    return getResourceTypeName(resourceClass) + "/" + version;
  }

  public static String getResourceTypeName(Class<? extends HasMetadata> resourceClass) {
    return HasMetadata.getFullResourceName(resourceClass);
  }

  public static String getDefaultFinalizerName(Class<? extends HasMetadata> resourceClass) {
    return getDefaultFinalizerName(getResourceTypeName(resourceClass));
  }

  public static String getDefaultFinalizerName(String resourceName) {
    // resource names for historic resources such as Pods are missing periods and therefore do not
    // constitute valid domain names as mandated by Kubernetes so generate one that does
    if (resourceName.indexOf('.') < 0) {
      resourceName = resourceName + MISSING_GROUP_SUFFIX;
    }
    return resourceName + FINALIZER_NAME_SUFFIX;
  }

  public static String getNameFor(Class<? extends Reconciler> reconcilerClass) {
    // if the reconciler annotation has a name attribute, use it
    final var annotation = reconcilerClass.getAnnotation(ControllerConfiguration.class);
    if (annotation != null) {
      final var name = annotation.name();
      if (!Constants.NO_VALUE_SET.equals(name)) {
        return name;
      }
    }
    // otherwise, use the lower-cased full class name
    return getDefaultNameFor(reconcilerClass);
  }

  public static void checkIfCanAddOwnerReference(HasMetadata owner, HasMetadata resource) {
    if (owner instanceof GenericKubernetesResource
        || resource instanceof GenericKubernetesResource) {
      return;
    }
    if (owner instanceof Namespaced) {
      if (!(resource instanceof Namespaced)) {
        throw new OperatorException(
            "Cannot add owner reference from a cluster scoped to a namespace scoped resource."
                + resourcesIdentifierDescription(owner, resource));
      } else if (!Objects.equals(
          owner.getMetadata().getNamespace(), resource.getMetadata().getNamespace())) {
        throw new OperatorException(
            "Cannot add owner reference between two resource in different namespaces."
                + resourcesIdentifierDescription(owner, resource));
      }
    }
  }

  private static String resourcesIdentifierDescription(HasMetadata owner, HasMetadata resource) {
    return " Owner name: "
        + owner.getMetadata().getName()
        + " Kind: "
        + owner.getKind()
        + ", Resource name: "
        + resource.getMetadata().getName()
        + " Kind: "
        + resource.getKind();
  }

  public static String getNameFor(Reconciler reconciler) {
    return getNameFor(reconciler.getClass());
  }

  public static String getDefaultNameFor(Reconciler reconciler) {
    return getDefaultNameFor(reconciler.getClass());
  }

  public static String getDefaultNameFor(Class<? extends Reconciler> reconcilerClass) {
    return getDefaultReconcilerName(reconcilerClass.getSimpleName());
  }

  public static String getDefaultReconcilerName(String reconcilerClassName) {
    // if the name is fully qualified, extract the simple class name
    final var lastDot = reconcilerClassName.lastIndexOf('.');
    if (lastDot > 0) {
      reconcilerClassName = reconcilerClassName.substring(lastDot + 1);
    }
    return reconcilerClassName.toLowerCase(Locale.ROOT);
  }

  public static boolean specsEqual(HasMetadata r1, HasMetadata r2) {
    return getSpec(r1).equals(getSpec(r2));
  }

  // will be replaced with: https://github.com/fabric8io/kubernetes-client/issues/3816
  public static Object getSpec(HasMetadata resource) {
    // optimize CustomResource case
    if (resource instanceof CustomResource cr) {
      return cr.getSpec();
    }

    try {
      Method getSpecMethod = resource.getClass().getMethod(GET_SPEC);
      return getSpecMethod.invoke(resource);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw noSpecException(resource, e);
    }
  }

  @SuppressWarnings("unchecked")
  public static Object setSpec(HasMetadata resource, Object spec) {
    // optimize CustomResource case
    if (resource instanceof CustomResource cr) {
      cr.setSpec(spec);
      return null;
    }

    try {
      Class<? extends HasMetadata> resourceClass = resource.getClass();

      // if given spec is null, find the method just using its name
      Method setSpecMethod;
      if (spec != null) {
        setSpecMethod = resourceClass.getMethod(SET_SPEC, spec.getClass());
      } else {
        setSpecMethod =
            Arrays.stream(resourceClass.getMethods())
                .filter(method -> SET_SPEC.equals(method.getName()))
                .findFirst()
                .orElseThrow(() -> noSpecException(resource, null));
      }

      return setSpecMethod.invoke(resource, spec);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw noSpecException(resource, e);
    }
  }

  private static IllegalStateException noSpecException(
      HasMetadata resource, ReflectiveOperationException e) {
    return new IllegalStateException(
        "No spec found on resource " + resource.getClass().getName(), e);
  }

  public static <T> T loadYaml(Class<T> clazz, Class loader, String yaml) {
    try (InputStream is = loader.getResourceAsStream(yaml)) {
      if (Builder.class.isAssignableFrom(clazz)) {
        return BuilderUtils.newBuilder(
            clazz, Serialization.unmarshal(is, BuilderUtils.builderTargetType(clazz)));
      }
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }

  public static void handleKubernetesClientException(Exception e, String resourceTypeName) {
    if (e instanceof MissingCRDException) {
      throw ((MissingCRDException) e);
    }

    if (e instanceof KubernetesClientException ke) {
      // only throw MissingCRDException if the 404 error occurs on the target CRD
      if (404 == ke.getCode()
          && (resourceTypeName.equals(ke.getFullResourceName())
              || matchesResourceType(resourceTypeName, ke))) {
        throw new MissingCRDException(resourceTypeName, ke.getVersion(), e.getMessage(), e);
      }
    }
  }

  private static boolean matchesResourceType(
      String resourceTypeName, KubernetesClientException exception) {
    final var fullResourceName = exception.getFullResourceName();
    if (fullResourceName != null) {
      return resourceTypeName.equals(fullResourceName);
    } else {
      // extract matching information from URI in the message if available
      final var message = exception.getMessage();
      final var regex = API_URI_PATTERN.matcher(message);
      if (regex.matches()) {
        var group = regex.group(3);
        if (group.endsWith(".")) {
          group = group.substring(0, group.length() - 1);
        }
        final var segments =
            Arrays.stream(group.split("/")).filter(Predicate.not(String::isEmpty)).toList();
        if (segments.size() != 3) {
          return false;
        }
        final var targetResourceName = segments.get(2) + "." + segments.get(0);
        return resourceTypeName.equals(targetResourceName);
      }
    }
    return false;
  }
}
