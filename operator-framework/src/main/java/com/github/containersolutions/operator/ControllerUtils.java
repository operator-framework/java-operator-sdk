package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import javassist.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


class ControllerUtils {

    // this is just to support testing, this way we don't try to create class multiple times in memory with same name.
    // note that other solution is to add a random string to doneable class name
    private static Map<Class<? extends CustomResource>, Class<? extends CustomResourceDoneable<? extends CustomResource>>>
            doneableClassCache = new ConcurrentHashMap<>();

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).finalizerName();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    public static <T extends CustomResource> Class<? extends CustomResourceDoneable<T>>
    createDoneableClassForCustomResource(Class<T> customResourceClass) {
        try {
            if (doneableClassCache.containsKey(customResourceClass)) {
                return (Class<? extends CustomResourceDoneable<T>>) doneableClassCache.get(customResourceClass);
            }
            ClassPool pool = ClassPool.getDefault();
            pool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
            CtClass customResourceDoneableClass = pool.get(CustomResourceDoneable.class.getName());

            CtClass result = pool.makeClass(customResourceClass.getName() + "Doneable", customResourceDoneableClass);

            CtClass customResourceCtClass = pool.get(customResourceClass.getName());
            CtClass functionCtClass = pool.get(Function.class.getName());

            CtConstructor ctConstructor = new CtConstructor(new CtClass[]{customResourceCtClass, functionCtClass}, result);
            result.addConstructor(ctConstructor);
            ctConstructor.setBody("super($1, $2);");

            Class<? extends CustomResourceDoneable<T>> resClass = (Class<? extends CustomResourceDoneable<T>>) result.toClass();
            doneableClassCache.put(customResourceClass, resClass);
            return resClass;
        } catch (CannotCompileException | NotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
