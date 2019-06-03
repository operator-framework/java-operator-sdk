package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;

class ControllerUtils {

    public static final String GROUP_API_DELIMITER = "/";

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).defaultFinalizer();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getApiVersion(ResourceController controller) {
        return getGroup(controller) + GROUP_API_DELIMITER + getAnnotation(controller).version();
    }


    static String getVersion(ResourceController controller) {
        return getAnnotation(controller).version();
    }

    static String getKind(ResourceController controller) {
        return getAnnotation(controller).kind();
    }


    static <R extends CustomResource> Class<? extends CustomResourceList<R>> getCustomResourceListClass(ResourceController controller) {
        return (Class<? extends CustomResourceList<R>>) getAnnotation(controller).customResourceListClass();
    }

    static <R extends CustomResource> Class<? extends CustomResourceDoneable<R>> getCustomResourceDonebaleClass(ResourceController controller) {
        return (Class<? extends CustomResourceDoneable<R>>) getAnnotation(controller).customResourceDonebaleClass();
    }


    private static String getGroup(ResourceController controller) {
        return getAnnotation(controller).group();
    }
    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

}
