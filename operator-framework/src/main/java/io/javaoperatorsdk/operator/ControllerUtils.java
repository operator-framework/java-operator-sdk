package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import org.apache.commons.lang3.ClassUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class ControllerUtils {

    private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
    private static Map<Class<? extends ResourceController>, Class<? extends CustomResource>> controllerToCustomResourceMappings = new HashMap();

    static {
        try {
            final Enumeration<URL> customResourcesMetadaList = ControllerUtils.class.getClassLoader().getResources("javaoperatorsdk/controllers");
            for (Iterator<URL> it = customResourcesMetadaList.asIterator(); it.hasNext(); ) {
                URL url = it.next();
                final List<String> classNamePairs = Files.lines(Path.of(url.getPath()))
                        .collect(Collectors.toList());

                classNamePairs.forEach(clazzPair -> {
                    try {

                        final String[] classNames = clazzPair.split(",");
                        if (classNames.length != 2) {
                            throw new IllegalStateException(String.format("%s is not custom-resource metadata defined in %s", url.toString()));
                        }

                        controllerToCustomResourceMappings.put((Class<? extends ResourceController>) ClassUtils.getClass(classNames[0]), (Class<? extends CustomResource>) ClassUtils.getClass(classNames[1]));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //TODO: DEBUG log
    }

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
        final Class<? extends CustomResource> customResourceClass = controllerToCustomResourceMappings
                .get(controller.getClass());
        if (customResourceClass == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "No custom resource has been found for controller %s",
                            controller.getClass().getCanonicalName()
                    )
            );
        }
        return (Class<R>) customResourceClass;
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
