package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.Serializable;

import static java.lang.String.format;

@Controller(crdName = "test.crd")
public class ControllerImplemented2Interfaces implements Serializable, ResourceController<ControllerImplemented2Interfaces.MyCustomResource> {

    public static class MyCustomResource extends CustomResource {
    }

    @Override
    public UpdateControl<MyCustomResource> createOrUpdateResource(MyCustomResource customResource, Context<MyCustomResource> context) {
        return UpdateControl.updateCustomResource(null);
    }

    @Override
    public boolean deleteResource(MyCustomResource customResource, Context<MyCustomResource> context) {
        return false;
    }
}
