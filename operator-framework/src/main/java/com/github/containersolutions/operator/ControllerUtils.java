package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import javassist.*;


class ControllerUtils {

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).finalizerName();
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

    public static <T extends CustomResource> Class
    createDoneableClassForCustomResource(Class<T> customResourceClass) {
        try {
            ClassPool pool = ClassPool.getDefault();
            pool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
            CtClass customResourceDoneableClass = pool.get(CustomResourceDoneable.class.getName());

            CtClass result = pool.makeClass(customResourceClass.getName() + "Doneable2", customResourceDoneableClass);

            CtClass customResourceCtClass = pool.get(customResourceClass.getName());
            CtClass functionCtClass = pool.get(Function.class.getName());

            CtConstructor ctConstructor = new CtConstructor(new CtClass[]{customResourceCtClass, functionCtClass}, result);
            result.addConstructor(ctConstructor);
            ctConstructor.setBody("super($1, $2);");

            return result.toClass();
        } catch (CannotCompileException | NotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
