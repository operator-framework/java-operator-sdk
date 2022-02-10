package io.javaoperatorsdk.operator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import com.fasterxml.jackson.core.JsonProcessingException;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.OBJECT_MAPPER;

@SuppressWarnings("rawtypes")
public class ReconcilerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
  protected static final String MISSING_GROUP_SUFFIX = ".javaoperatorsdk.io";

  // prevent instantiation of util class
  private ReconcilerUtils() {}

  public static boolean isFinalizerValid(String finalizer) {
    // todo: use fabric8 method when 5.12 is released
    // return HasMetadata.validateFinalizer(finalizer);
    final var validator = new HasMetadata() {

      @Override
      public ObjectMeta getMetadata() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void setMetadata(ObjectMeta objectMeta) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void setApiVersion(String s) {
        throw new UnsupportedOperationException();
      }
    };
    return Constants.NO_FINALIZER.equals(finalizer) || validator.isFinalizerValid(finalizer);
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
      if (!Constants.EMPTY_STRING.equals(name)) {
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

  public static boolean specsSame(HasMetadata r1, HasMetadata r2) {
    try {
      var c1json = OBJECT_MAPPER.writeValueAsString(getSpec(r1));
      var c2json = OBJECT_MAPPER.writeValueAsString(getSpec(r2));
      return OBJECT_MAPPER.readTree(c1json).equals(OBJECT_MAPPER.readTree(c2json));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  // will be replaced with: https://github.com/fabric8io/kubernetes-client/issues/3816
  public static Object getSpec(HasMetadata resource) {
    try {
      Method getSpecMethod = resource.getClass().getMethod("getSpec");
      return getSpecMethod.invoke(resource);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

}
