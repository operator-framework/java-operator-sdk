package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;


public class ControllerUtils {

    private static final String FINALIZER_NAME_SUFFIX = "/finalizer";

    static String getFinalizer(ResourceController controller) {
        final String annotationFinalizerName = getAnnotation(controller).finalizerName();
        if (!Controller.NULL.equals(annotationFinalizerName)) {
            return annotationFinalizerName;
        }
        return getAnnotation(controller).crdName() + FINALIZER_NAME_SUFFIX;
    }

    static boolean getGenerationEventProcessing(ResourceController<?> controller) {
        return getAnnotation(controller).generationAwareEventProcessing();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController<R> controller) {
        return Arrays
                .stream(controller.getClass().getGenericInterfaces())
                .filter(i -> i instanceof ParameterizedType)
                .map(i -> (ParameterizedType) i)
                .findFirst()
                .map(i -> (Class<R>) i.getActualTypeArguments()[0])
                .get();
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

    private static Controller getAnnotation(ResourceController<?> controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    public static boolean hasGivenFinalizer(CustomResource resource, String finalizer) {
        return resource.getMetadata().getFinalizers() != null && resource.getMetadata().getFinalizers().contains(finalizer);
    }
}
