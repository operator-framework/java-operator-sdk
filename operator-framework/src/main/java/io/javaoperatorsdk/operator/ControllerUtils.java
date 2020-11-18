package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ControllerUtils {

    private final static double JAVA_VERSION = Double.parseDouble(System.getProperty("java.specification.version"));
    private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
    
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
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }
    
    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    public static boolean hasGivenFinalizer(CustomResource resource, String finalizer) {
        return resource.getMetadata().getFinalizers() != null && resource.getMetadata().getFinalizers().contains(finalizer);
    }
}
