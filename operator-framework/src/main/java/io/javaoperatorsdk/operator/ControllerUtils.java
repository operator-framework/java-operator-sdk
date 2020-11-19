package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class ControllerUtils {

    private final static double JAVA_VERSION = Double.parseDouble(System.getProperty("java.specification.version"));
    private static final String FINALIZER_NAME_SUFFIX = "/finalizer";

    // this is just to support testing, this way we don't try to create class multiple times in memory with same name.
    // note that other solution is to add a random string to doneable class name
    private static Map<Class<? extends CustomResource>, Class<? extends CustomResourceDoneable<? extends CustomResource>>>
            doneableClassCache = new HashMap<>();

    static String getFinalizer(ResourceController controller) {
        final String annotationFinalizerName = getAnnotation(controller).finalizerName();
        if (!Controller.NULL.equals(annotationFinalizerName)) {
            return annotationFinalizerName;
        }
        final String crdName = getAnnotation(controller).crdName() + FINALIZER_NAME_SUFFIX;
        return crdName;
    }

    static boolean getGenerationEventProcessing(ResourceController controller) {
        return getAnnotation(controller).generationAwareEventProcessing();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController<R> controller) {
        final Class<R> type = Arrays.stream(controller.getClass().getGenericInterfaces()).filter(i -> i instanceof ParameterizedType).map(i -> (ParameterizedTypeImpl) i).findFirst().map(i -> (Class<R>) i.getActualTypeArguments()[0]).get();
        return type;
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }


    public static <T extends CustomResource> Class<? extends CustomResourceDoneable<T>>
    getCustomResourceDoneableClass(ResourceController<T> controller) {
        try {
            final Class<T> customResourceClass = getCustomResourceClass(controller);
            return (Class<? extends CustomResourceDoneable<T>>) Class.forName(customResourceClass.getCanonicalName() + "Doneable");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    public static boolean hasGivenFinalizer(CustomResource resource, String finalizer) {
        return resource.getMetadata().getFinalizers() != null && resource.getMetadata().getFinalizers().contains(finalizer);
    }
}
