package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class ReconcilerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
  protected static final String MISSING_GROUP_SUFFIX = ".javaoperatorsdk.io";

  // prevent instantiation of util class
  private ReconcilerUtils() {}

  public static boolean isFinalizerValid(String finalizer) {
    return HasMetadata.validateFinalizer(finalizer);
  }

  public static String getResourceTypeNameWithVersion(Class<? extends HasMetadata> resourceClass) {
    final var version = HasMetadata.getVersion(resourceClass);
    return getResourceTypeName(resourceClass) + "/" + version;
  }

  public static String getResourceTypeName(
      Class<? extends HasMetadata> resourceClass) {
    final var group = HasMetadata.getGroup(resourceClass);
    final var plural = HasMetadata.getPlural(resourceClass);
    return (group == null || group.isEmpty()) ? plural : plural + "." + group;
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
    try {
      Method getSpecMethod = resource.getClass().getMethod("getSpec");
      return getSpecMethod.invoke(resource);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("No spec found on resource", e);
    }
  }

  public static Object setSpec(HasMetadata resource, Object spec) {
    try {
      Method setSpecMethod = resource.getClass().getMethod("setSpec", spec.getClass());
      return setSpecMethod.invoke(resource, spec);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("No spec found on resource", e);
    }
  }

  public static <T> T loadYaml(Class<T> clazz, Class loader, String yaml) {
    try (InputStream is = loader.getResourceAsStream(yaml)) {
      return Serialization.unmarshal(is, clazz);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
    }
  }

}
