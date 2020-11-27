package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.*;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.Serializable;

import static java.lang.String.format;

@Controller(crdName = "test.crd")
public class ControllerImplementedIntermediateAbstractClass extends AbstractController implements Serializable {

    public UpdateControl<AbstractController.MyCustomResource> createOrUpdateResource(AbstractController.MyCustomResource customResource, Context<AbstractController.MyCustomResource> context) {
        return UpdateControl.updateCustomResource(null);
    }

    public DeleteControl deleteResource(AbstractController.MyCustomResource customResource, Context<AbstractController.MyCustomResource> context) {
        return DeleteControl.DEFAULT_DELETE;
    }
}
