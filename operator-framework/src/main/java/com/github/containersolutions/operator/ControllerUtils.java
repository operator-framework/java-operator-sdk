package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import javassist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class ControllerUtils {

    private final static double JAVA_VERSION = Double.parseDouble(System.getProperty("java.specification.version"));

    private final static Logger log = LoggerFactory.getLogger(Operator.class);

    // this is just to support testing, this way we don't try to create class multiple times in memory with same name.
    // note that other solution is to add a random string to doneable class name
    private static Map<Class<? extends CustomResource>, Class<? extends CustomResourceDoneable<? extends CustomResource>>>
            doneableClassCache = new HashMap<>();

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).finalizerName();
    }

    static boolean getGenerationEventProcessing(ResourceController controller) {
        return getAnnotation(controller).generationAwareEventProcessing();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }


    public static <T extends CustomResource> Class<? extends CustomResourceDoneable<T>>
    getCustomResourceDoneableClass(ResourceController<T> controller) {
        try {
            Class<? extends CustomResource> customResourceClass = getAnnotation(controller).customResourceClass();
            String className = customResourceClass.getPackage().getName() + "." + customResourceClass.getSimpleName() + "CustomResourceDoneable";

            if (doneableClassCache.containsKey(customResourceClass)) {
                return (Class<? extends CustomResourceDoneable<T>>) doneableClassCache.get(customResourceClass);
            }

            ClassPool pool = ClassPool.getDefault();
            pool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));

            CtClass superClass = pool.get(CustomResourceDoneable.class.getName());
            CtClass function = pool.get(Function.class.getName());
            CtClass customResource = pool.get(customResourceClass.getName());
            CtClass[] argTypes = {customResource, function};
            CtClass customDoneable = pool.makeClass(className, superClass);
            CtConstructor ctConstructor = CtNewConstructor.make(argTypes, null, "super($1, $2);", customDoneable);
            customDoneable.addConstructor(ctConstructor);

            Class<? extends CustomResourceDoneable<T>> doneableClass;
            if (JAVA_VERSION >= 9) {
                doneableClass = (Class<? extends CustomResourceDoneable<T>>) customDoneable.toClass(customResourceClass);
            } else {
                doneableClass = (Class<? extends CustomResourceDoneable<T>>) customDoneable.toClass();
            }
            doneableClassCache.put(customResourceClass, doneableClass);
            return doneableClass;
        } catch (CannotCompileException | NotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    public static boolean hasDefaultFinalizer(CustomResource resource, String finalizer) {
        if (resource.getMetadata().getFinalizers() != null) {
            return resource.getMetadata().getFinalizers().contains(finalizer);
        }
        return false;
    }
}
