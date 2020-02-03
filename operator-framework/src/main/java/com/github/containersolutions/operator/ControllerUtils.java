package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;

class ControllerUtils {

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).defaultFinalizer();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }

    static <R extends CustomResource> Class<? extends CustomResourceList<R>> getCustomResourceListClass(ResourceController controller) {
        return (Class<? extends CustomResourceList<R>>) getAnnotation(controller).customResourceListClass();
    }

    static <R extends CustomResource> Class<? extends CustomResourceDoneable<R>> getCustomResourceDonebaleClass(ResourceController controller) {
        return (Class<? extends CustomResourceDoneable<R>>) getAnnotation(controller).customResourceDoneableClass();
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

}
