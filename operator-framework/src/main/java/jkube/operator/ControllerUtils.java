package jkube.operator;

public class ControllerUtils {

    public static String getDefaultFinalizer(Class<? extends CustomResourceController> controllerClass) {
        return "todo";
    }
}
