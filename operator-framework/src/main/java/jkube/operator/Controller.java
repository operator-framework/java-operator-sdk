package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Controller {

    String DEFAULT_FINALIZER = "default";

    String apiVersion() default "v1";

    String crdVersion() default "v1";

    Class<? extends CustomResource> customResourceClass();

    Class<? extends CustomResourceList> customResourceListClass();

    Class<? extends CustomResourceDoneable> customResourceDoneableClass();

    String defaultFinalizer() default DEFAULT_FINALIZER;
}
