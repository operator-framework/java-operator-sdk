package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import javassist.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class ControllerUtils {

    private final static Logger log = LoggerFactory.getLogger(Operator.class);
    private static ClassPool pool;
    private static Class generatedCustomResourceDoneable;

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).finalizerName();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }

    public static Class<? extends CustomResourceList> getCustomResourceListClass() {
        return CustomResourceList.class;
    }

    public static <R extends CustomResource> Class<? extends CustomResourceDoneable<R>> getCustomResourceDoneableClass(ResourceController<R> controller) {
        return createCustomResourceDoneableClass(controller);
    }

    private static <R extends CustomResource> Class<? extends CustomResourceDoneable<R>> createCustomResourceDoneableClass(ResourceController<R> controller) {
        pool = ClassPool.getDefault();
        pool.appendClassPath(new ClassClassPath(ControllerUtils.class));

        String controllerName = StringUtils.substringAfterLast(controller.getClass().toString(), ".");
        Class<R> customResourceClass = (Class<R>) getAnnotation(controller).customResourceClass();

        String className = getPackageName(customResourceClass.getName(), controllerName + "CustomResourceDoneable");
        if (isClassInPool(className)) {
            return generatedCustomResourceDoneable;
        }
        String superClassName = "io.fabric8.kubernetes.client.CustomResourceDoneable";
        CtClass customDoneable = getOrCreateClass(className, superClassName);

        try {
            CtClass customResource = pool.get(customResourceClass.getName());
            CtClass function = pool.get("io.fabric8.kubernetes.api.builder.Function");
            CtClass[] argTypes = {customResource, function};
            CtConstructor ctConstructor = CtNewConstructor.make(argTypes, null, "super($1, $2);", customDoneable);
            customDoneable.addConstructor(ctConstructor);

        } catch (CannotCompileException | NotFoundException e) {
            log.error("Error compiling constructor for CustomResourceDoneable class: {}", e);
        }

        Class<? extends CustomResourceDoneable<R>> doneableClass = getClassFromCtClass(customDoneable);
        generatedCustomResourceDoneable = doneableClass;
        return doneableClass;
    }

    private static boolean isClassInPool(String className) {
        try {
            pool.get(className);
            return true;
        } catch (NotFoundException e) {
            log.debug("Class {} not in pool", className);
            return false;
        }
    }

    private static CtClass getOrCreateClass(String className, String superClassName){
        CtClass customClass;
        try {
            customClass = pool.get(className);
            customClass.defrost();
        } catch (NotFoundException ce) {
            log.info("Class not found, creating new: {}", className);
            CtClass superClass = null;
            try {
                superClass = pool.get(superClassName);
            } catch (NotFoundException sce) {
                log.error("Error getting superClass: {}", sce);
            }
            customClass = pool.makeClass(className, superClass);
        }
        return customClass;
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    private static String getPackageName(String customResourceName, String newClassName) {
        CtClass customResource = null;
        try {
            customResource = pool.get(customResourceName);
        } catch (NotFoundException e) {
            log.error("Error getting class: {}", e);
        }
        String packageName = customResource != null ? customResource.getPackageName() : "";
        return packageName + "." + newClassName;
    }

    private static Class getClassFromCtClass(CtClass customCtClass) {
        Class customClass = null;
        try {
            customClass = customCtClass.toClass();
        } catch (CannotCompileException e) {
            log.error("Error transforming CtClass to Class: {}", e);
        }
        return customClass;
    }
}