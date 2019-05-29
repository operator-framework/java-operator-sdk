package jkube.operator.sample;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;

class GetResourceFromAnnotationSimpleTest {

    // todo could be generalized?
    @Test
    public void genericsAccess() {
        Class<BitbucketController> bitbucketControllerClass = BitbucketController.class;
        System.out.println(((ParameterizedType) bitbucketControllerClass.getAnnotatedInterfaces()[0].getType()).getActualTypeArguments()[0]);
    }

}